package com.satcop.smartvisitor.kiosk.data.geo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Capture stamps for face/incident (AC-FL-GPS / AC-CAP).
 * Never invents coordinates — gpsMissing=true when unavailable.
 */
data class CaptureStamp(
    val capturedAt: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val accuracyM: Float? = null,
    val gpsMissing: Boolean = true,
)

object CaptureGeo {
    @Volatile private var app: Context? = null

    fun install(context: Context) {
        app = context.applicationContext
    }

    suspend fun read(context: Context? = null): CaptureStamp {
        val ctx = context?.applicationContext ?: app
            ?: return CaptureStamp(
                capturedAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
                gpsMissing = true,
            )
        return readWith(ctx)
    }

    private suspend fun readWith(context: Context): CaptureStamp = withContext(Dispatchers.IO) {
        val at = OffsetDateTime.now(ZoneOffset.UTC).toString()
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            return@withContext CaptureStamp(capturedAt = at, gpsMissing = true)
        }
        val loc = lastKnown(context)
        if (loc == null) {
            CaptureStamp(capturedAt = at, gpsMissing = true)
        } else {
            CaptureStamp(
                capturedAt = at,
                lat = loc.latitude,
                lng = loc.longitude,
                accuracyM = if (loc.hasAccuracy()) loc.accuracy else null,
                gpsMissing = false,
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        var best: Location? = null
        for (p in providers) {
            if (!lm.isProviderEnabled(p) && p != LocationManager.PASSIVE_PROVIDER) continue
            val loc = runCatching { lm.getLastKnownLocation(p) }.getOrNull() ?: continue
            if (best == null || loc.accuracy < best.accuracy) best = loc
        }
        return best
    }
}
