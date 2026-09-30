package com.satcop.smartvisitor.kiosk.data.geo

/** A location reading with its own age clock (elapsed-realtime ms), never invented. */
data class GpsFix(
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    /** SystemClock.elapsedRealtime() at which the fix was produced. */
    val elapsedMs: Long,
) {
    fun ageMs(nowElapsedMs: Long): Long = (nowElapsedMs - elapsedMs).coerceAtLeast(0)
}

sealed interface GpsState {
    /** No reading yet. */
    data object Searching : GpsState
    /** Reading too old (attendance needs a FRESH reading every attempt). */
    data class Stale(val fix: GpsFix) : GpsState
    /** Accuracy worse than the limit. */
    data class Weak(val fix: GpsFix) : GpsState
    data class Ready(val fix: GpsFix) : GpsState
}

object GpsPolicy {
    /** Backend blocks above 100 m (GET /schools/me/settings attendanceAccuracyLimitM). Client pre-check only. */
    const val ACCURACY_LIMIT_M = 100.0
    /** A reading older than this is never used for an attempt (server stale window is 120 s). */
    const val MAX_FIX_AGE_MS = 20_000L

    fun evaluate(fix: GpsFix?, nowElapsedMs: Long): GpsState = when {
        fix == null -> GpsState.Searching
        fix.ageMs(nowElapsedMs) > MAX_FIX_AGE_MS -> GpsState.Stale(fix)
        fix.accuracyM > ACCURACY_LIMIT_M || fix.accuracyM < 0 -> GpsState.Weak(fix)
        else -> GpsState.Ready(fix)
    }

    /** Should [candidate] replace [current]? Newer+better wins; an old fix is always replaced. */
    fun prefer(current: GpsFix?, candidate: GpsFix): GpsFix {
        if (current == null) return candidate
        if (candidate.elapsedMs < current.elapsedMs) return current
        val currentAge = candidate.elapsedMs - current.elapsedMs
        return if (candidate.accuracyM <= current.accuracyM || currentAge > 10_000L) candidate else current
    }

    /** Status line shown under the camera. Plain words only. */
    fun statusLine(state: GpsState, nowElapsedMs: Long): String = when (state) {
        GpsState.Searching -> "Looking for your location…"
        is GpsState.Stale -> "Location is out of date. Waiting for a fresh reading…"
        is GpsState.Weak -> "Weak GPS signal (about ${state.fix.accuracyM.toInt()} m). Move to an open area."
        is GpsState.Ready -> "Location ready (accurate to about ${state.fix.accuracyM.toInt()} m)"
    }
}
