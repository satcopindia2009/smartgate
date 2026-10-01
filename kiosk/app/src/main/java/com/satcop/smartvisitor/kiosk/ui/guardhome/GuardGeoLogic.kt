package com.satcop.smartvisitor.kiosk.ui.guardhome

import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.GeoFenceDef
import com.satcop.smartvisitor.kiosk.data.model.GeofenceInfo
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.asin
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** School geofence mode (GET /guards/me/geofence `mode`). UNKNOWN = the app could not read it: the server decides. */
enum class GeoMode {
    UNKNOWN, OFF, SOFT, RESTRICT;

    companion object {
        fun parse(raw: String?): GeoMode = when (raw?.trim()?.lowercase()) {
            "off" -> OFF
            "soft" -> SOFT
            "restrict", "restricted" -> RESTRICT
            else -> UNKNOWN
        }
    }
}

/** Where the "Open ..." button on a location problem panel should go. */
enum class LocationAction { APP_SETTINGS, LOCATION_SETTINGS }

/** A location problem that stops a CHECK-IN / CLOCK-OUT before any request is made (no record is created). */
data class LocationGate(val message: String, val action: LocationAction)

/**
 * 1072 guard flow rules that are free of Android types: geofence copy, location gating, mock location,
 * shift-end reminder. The server stays the authority; every server field used here is optional.
 */
object GuardGeoLogic {
    // --- Product rulings 2026-10-01 (EN). Hindi lines below are NEW and need a Product/Compliance look. ---
    const val LOCATION_OFF_RESTRICT = "Location is off. Turn it on to check in."
    const val OPEN_LOCATION_SETTINGS = "Open location settings"
    const val SOFT_OUTSIDE_NOTE = "You checked in from outside the campus. This will be flagged for review."
    const val SHIFT_END_PROMPT = "Your shift time is over. Clock out now?"
    const val SHIFT_END_CLOCK_OUT = "Clock out"
    const val SHIFT_END_LATER = "Later"
    /** Soft/unknown mode: allowed, flagged MOCK_LOCATION (Product 1 Oct). */
    const val MOCK_LOCATION_NOTE = "Mock location detected. Your check-in will be flagged for review."
    /** Restrict mode, and every clock-out: blocked. */
    const val MOCK_BLOCKED = "Fake location detected. Turn off mock-location apps and try again."
    const val CLOCK_OUT_OUTSIDE_NOTE = "You clocked out from outside the campus. This will be flagged for review."
    /** Face login card: neutral wording (demo accepts a face without enrolment). */
    const val FACE_CARD_NEUTRAL = "Verify your face to clock in"

    /** Mock-location rule: restrict mode and any clock-out are blocked; soft/off/unknown mode goes out flagged. */
    fun mockBlock(mode: AttendanceMode, geo: GeoMode, isMock: Boolean): String? =
        if (isMock && (mode == AttendanceMode.CLOCK_OUT || geo == GeoMode.RESTRICT)) MOCK_BLOCKED else null

    /** Business date: the IST clock minus the school cutoff ("HH:mm", default 00:00). */
    fun businessDate(now: Instant, cutoff: String?): LocalDate {
        val c = parseShiftEnd(cutoff) ?: LocalTime.MIDNIGHT
        return now.atZone(GuardHomeLogic.IST).minusHours(c.hour.toLong()).minusMinutes(c.minute.toLong()).toLocalDate()
    }

    /** Hindi equivalents from the Product ruling. NOT displayed yet: the app has no language switch outside Login. */
    object Hi {
        const val OUTSIDE_WITH_DISTANCE = "आप परिसर से लगभग %1\$s मीटर दूर हैं; सीमा %2\$s मीटर है। परिसर के अंदर आकर दोबारा कोशिश करें।"
        const val LOCATION_OFF_RESTRICT = "लोकेशन बंद है। चेक इन के लिए इसे चालू करें।"
        const val OPEN_LOCATION_SETTINGS = "लोकेशन सेटिंग खोलें"
        const val MOCK_BLOCKED = "नकली लोकेशन मिली है। मॉक-लोकेशन ऐप बंद करें और दोबारा कोशिश करें।"
        const val MOCK_LOCATION_NOTE = "मॉक लोकेशन मिली है। आपका चेक इन समीक्षा के लिए चिह्नित किया जाएगा।"
        const val SOFT_OUTSIDE_NOTE = "आपने परिसर के बाहर से चेक इन किया है। इसे समीक्षा के लिए चिह्नित किया जाएगा।"
    }

    /** Soft mode: how long to wait for a first GPS reading before a check-in may go out flagged (gpsMissing). */
    const val GPS_WAIT_TIMEOUT_MS = 20_000L
    const val DEFAULT_ACCURACY_LIMIT_M = 100.0
    /** Default shift end (school time) when the guard has no shift end from the server. Product: 18:00. */
    val DEFAULT_SHIFT_END: LocalTime = LocalTime.of(18, 0)

    /** Round UP to the next 10 m (Product: "Round X up to the next 10 m"). 341 -> 350, 340 -> 340. */
    fun roundUpTo10(distanceM: Double): Int = (ceil(distanceM / 10.0) * 10).toInt()

    /** "You are about {X} m from the campus; the limit is {Y} m. Move inside and try again." */
    fun outsideMessage(distanceM: Double, radiusM: Double): String =
        "You are about ${roundUpTo10(distanceM)} m from the campus; the limit is ${radiusM.roundToInt()} m. Move inside and try again."

    /** Soft-outside detail line for the result screen (only when the server sent a distance and a radius). */
    fun softOutsideDetail(distanceM: Double?, radiusM: Double?): String? =
        if (distanceM != null && radiusM != null && distanceM >= 0 && radiusM > 0) {
            "You were about ${roundUpTo10(distanceM)} m from the campus; the limit is ${radiusM.roundToInt()} m."
        } else null

    private fun num(s: String?): Double? = s?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    /**
     * 403 GEO_FENCE_RESTRICTED with distance: product copy. [fallbackRadiusM] is the fence radius read earlier from
     * /guards/me/geofence, used only when the error carries a distance but no radius. Null = no distance (use the generic line).
     */
    fun outsideMessageFrom(e: ApiException, fallbackRadiusM: Double? = null): String? {
        if (canonicalCode(e) != "GEO_FENCE_RESTRICTED") return null
        val d = num(e.details["distanceM"]) ?: return null
        val r = num(e.details["radiusM"]) ?: num(e.details["limitM"]) ?: fallbackRadiusM ?: return null
        if (d < 0 || r <= 0) return null
        return outsideMessage(d, r)
    }

    /** reasonCode (v2 names) -> the legacy code the app already handles. */
    private val reasonToLegacy = mapOf(
        "GEOFENCE_OUTSIDE" to "GEO_FENCE_RESTRICTED",
        "SELFIE_REQUIRED" to "PHOTO_REQUIRED",
        "GPS_STALE" to "STALE_CAPTURE",
        "GPS_ACCURACY_TOO_LOW" to "GPS_ACCURACY_LOW",
        "ALREADY_CLOCKED_IN" to "ALREADY_CHECKED_IN",
    )
    private val legacyCodes = reasonToLegacy.values.toSet()

    /** The code the app reasons about: the legacy `code` when known, else the mapped `details.reasonCode`, else the raw code. */
    fun canonicalCode(e: ApiException): String {
        val code = e.code.uppercase()
        if (code in legacyCodes) return code
        val mapped = reasonToLegacy[e.details["reasonCode"]?.trim()?.uppercase()]
        if (mapped != null && (code.isBlank() || code == "FORBIDDEN" || code == "VALIDATION" || code == "UNKNOWN" || code == "CONFLICT" || code == "BAD_REQUEST")) return mapped
        return code
    }

    /** The same error with the canonical (legacy) code, so every existing handler sees one vocabulary. */
    fun normalize(e: ApiException): ApiException {
        val c = canonicalCode(e)
        return if (c == e.code.uppercase()) e else ApiException(c, e.message, e.httpStatus, e.details)
    }

    // --- location gating (A, B, C) ---

    /**
     * What must stop the attempt BEFORE the camera/request. Restrict mode: no permission or GPS service off -> blocked, no record.
     * Clock-out always needs location. Soft / off / unknown mode: never blocked here for check-in (flagged instead).
     */
    fun locationGate(mode: AttendanceMode, geo: GeoMode, permission: Boolean, serviceOn: Boolean): LocationGate? = when {
        !permission && (mode == AttendanceMode.CLOCK_OUT || geo == GeoMode.RESTRICT) ->
            LocationGate(if (mode == AttendanceMode.CLOCK_OUT) ClockInLogic.CHECKOUT_NEEDS_GPS else ClockInLogic.LOCATION_NEEDED, LocationAction.APP_SETTINGS)
        permission && !serviceOn && mode == AttendanceMode.CLOCK_OUT ->
            LocationGate(ClockInLogic.CHECKOUT_NEEDS_GPS, LocationAction.LOCATION_SETTINGS)
        permission && !serviceOn && geo == GeoMode.RESTRICT ->
            LocationGate(LOCATION_OFF_RESTRICT, LocationAction.LOCATION_SETTINGS)
        else -> null
    }

    /** True when a CHECK-IN may go out WITHOUT a location (soft/off/unknown mode only; the server still decides). */
    fun checkInWithoutLocation(
        mode: AttendanceMode, geo: GeoMode, permission: Boolean, serviceOn: Boolean, hasFix: Boolean, waitedMs: Long,
    ): Boolean {
        if (mode != AttendanceMode.CHECK_IN || geo == GeoMode.RESTRICT || hasFix) return false
        if (!permission || !serviceOn) return true
        // Permission + service on, no reading yet: soft/off mode lets it go out after a timeout. Unknown mode keeps waiting.
        return (geo == GeoMode.SOFT || geo == GeoMode.OFF) && waitedMs >= GPS_WAIT_TIMEOUT_MS
    }

    // --- client-side inside/outside hint (B). The server stays authoritative. ---

    fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1); val p2 = Math.toRadians(lat2)
        val dp = p2 - p1; val dl = Math.toRadians(lng2 - lng1)
        val a = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
        return 2 * r * asin(sqrt(a))
    }

    data class FenceHint(val distanceM: Double, val radiusM: Double, val outside: Boolean)

    /** Nearest fence from the server's list (or its single centre+radius). Null when the server gave no fence or there is no fix. */
    fun hint(fix: GpsFix?, info: GeofenceInfo?): FenceHint? {
        if (fix == null || info == null) return null
        val fences: List<GeoFenceDef> = info.fences?.filter { it.lat != null && it.lng != null && (it.radiusM ?: 0.0) > 0 }.orEmpty()
            .ifEmpty {
                val c = info.center
                val r = info.radiusM
                if (c?.lat != null && c.lng != null && r != null && r > 0) listOf(GeoFenceDef(lat = c.lat, lng = c.lng, radiusM = r)) else emptyList()
            }
        if (fences.isEmpty()) return null
        val best = fences.map { f ->
            val d = distanceM(fix.lat, fix.lng, f.lat!!, f.lng!!)
            Triple(f, d, d - f.radiusM!!)
        }.minByOrNull { it.third } ?: return null
        // Outside only when clearly outside, allowing for the reading's own accuracy (never a false alarm).
        val acc = fix.accuracyM.takeIf { it.isFinite() && it >= 0 && it < 1e6 } ?: 0.0
        return FenceHint(best.second, best.first.radiusM!!, outside = best.third > acc)
    }

    /** Restrict-mode pre-warning shown on the selfie screen when the reading is clearly outside. Null otherwise. */
    fun restrictHint(geo: GeoMode, fix: GpsFix?, info: GeofenceInfo?): String? {
        if (geo != GeoMode.RESTRICT) return null
        val h = hint(fix, info)?.takeIf { it.outside } ?: return null
        return outsideMessage(h.distanceM, h.radiusM)
    }

    fun accuracyLimitM(info: GeofenceInfo?): Double =
        info?.rules?.accuracyLimitM?.takeIf { it.isFinite() && it > 0 } ?: DEFAULT_ACCURACY_LIMIT_M

    /** Mode from the geofence block, else the /schools/me value. */
    fun modeOf(info: GeofenceInfo?, schoolMode: String? = null): GeoMode {
        val m = GeoMode.parse(info?.mode)
        return if (m != GeoMode.UNKNOWN) m else GeoMode.parse(schoolMode)
    }

    // --- shift-end reminder (G) ---

    /** "HH:mm" (also "H:mm", "HH:mm:ss"). Null when unreadable. */
    fun parseShiftEnd(raw: String?): LocalTime? {
        val t = raw?.trim().orEmpty()
        if (t.isEmpty()) return null
        return runCatching { LocalTime.parse(if (Regex("^\\d:").containsMatchIn(t)) "0$t" else t) }.getOrNull()
    }

    /**
     * Prompt once per duty day, only while on duty, today's row, after the shift end (server `shiftEndTime`, else 18:00).
     * Never auto clock-out.
     */
    fun shiftEndDue(
        state: com.satcop.smartvisitor.kiosk.data.model.AttendanceState,
        dutyDate: String?,
        shiftEndTime: String?,
        now: Instant,
        alreadyPromptedFor: LocalDate?,
    ): Boolean {
        if (state != com.satcop.smartvisitor.kiosk.data.model.AttendanceState.PRESENT) return false
        val today = GuardHomeLogic.todayIst(now)
        if (alreadyPromptedFor == today) return false
        val duty = dutyDate?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (duty != null && duty != today) return false
        val end = parseShiftEnd(shiftEndTime) ?: DEFAULT_SHIFT_END
        return !now.atZone(GuardHomeLogic.IST).toLocalTime().isBefore(end)
    }
}
