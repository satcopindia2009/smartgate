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

    private val _serviceOn = MutableStateFlow(isLocationServiceOn())
    /** 1072: false when the phone's Location switch (or every usable provider) is off, even if the permission is granted. */
    val serviceOn: StateFlow<Boolean> = _serviceOn.asStateFlow()

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            publish(location)
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) { restart() }
        override fun onProviderDisabled(provider: String) { restart() }
    }

    /** True when location is switched on for the device and at least the GPS or network provider is enabled. */
    fun isLocationServiceOn(): Boolean {
        val lm = app.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        val master = if (android.os.Build.VERSION.SDK_INT >= 28) runCatching { lm.isLocationEnabled }.getOrDefault(true) else true
        if (!master) return false
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .any { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    }

    /** Re-read the service state and (re)register the listeners. Call on provider change and on screen resume. */
    fun restart() {
        stop()
        start()
        _serviceOn.value = isLocationServiceOn()
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        _serviceOn.value = isLocationServiceOn()
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
        val mock = if (android.os.Build.VERSION.SDK_INT >= 31) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
        val fix = GpsFix(
            lat = location.latitude,
            lng = location.longitude,
            accuracyM = if (location.hasAccuracy()) location.accuracy.toDouble() else Double.MAX_VALUE,
            elapsedMs = SystemClock.elapsedRealtime() - ageMs.coerceAtLeast(0),
            isMock = mock,
        )
        if (fix.ageMs(SystemClock.elapsedRealtime()) > GpsPolicy.MAX_FIX_AGE_MS) return
        _fix.value = GpsPolicy.prefer(_fix.value, fix)
    }
}
