package com.satcop.smartvisitor.kiosk.data.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Live location feed for the attendance panel. It only ever publishes what the OS reports
 * (GPS/network providers); it never fabricates coordinates and never reuses an old reading:
 * a seeded last-known fix older than [GpsPolicy.MAX_FIX_AGE_MS] is ignored.
 */
class LiveGpsTracker(context: Context) {
    private val app = context.applicationContext
    private val _fix = MutableStateFlow<GpsFix?>(null)
    val fix: StateFlow<GpsFix?> = _fix.asStateFlow()

    private var started = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            publish(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPermission()) return
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        var any = false
        for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (!runCatching { lm.isProviderEnabled(p) }.getOrDefault(false)) continue
            runCatching {
                lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())
                any = true
                lm.getLastKnownLocation(p)?.let { publish(it) }
            }
        }
        started = any
    }

    fun stop() {
        if (!started) return
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        runCatching { lm?.removeUpdates(listener) }
        started = false
    }

    private fun publish(location: Location) {
        val ageMs = (SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000L
        val fix = GpsFix(
            lat = location.latitude,
            lng = location.longitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.MAX_VALUE,
            elapsedMs = SystemClock.elapsedRealtime() - ageMs.coerceAtLeast(0),
        )
        if (fix.ageMs(SystemClock.elapsedRealtime()) > GpsPolicy.MAX_FIX_AGE_MS) return
        _fix.value = GpsPolicy.prefer(_fix.value, fix)
    }
}
