package com.satcop.smartvisitor.kiosk.ui.guardhome

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.AttendanceState
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the guard app shows first. Server state only (GET /attendance/me/today). */
enum class LockState {
    /** Attendance not read yet: stay locked, show a loading state. */
    UNKNOWN,
    /** Not clocked in today: lock screen -> Self Check In. */
    LOCKED,
    /** On duty: the app is open. */
    UNLOCKED,
    /** Clocked out today: "Shift complete. See you tomorrow." with only Sign Out. */
    SHIFT_COMPLETE,
}

enum class ClockStep { INFO, SELFIE }

/** Status chip on the Self Check In/Out info screen (Product ruling R1). */
data class StatusCard(val chip: String, val title: String, val detail: String, val canProceed: Boolean, val note: String? = null)

/** Data behind the "Checked In!" / "Checked Out!" screen. */
data class ClockResult(
    val mode: AttendanceMode,
    val action: String,
    val time: String,
    val gate: String,
    val selfieUploaded: Boolean,
    val recordId: String,
    val flaggedNote: String?,
    /** 1072 F: second line under the soft-outside note ("You were about 340 m from the campus; the limit is 192 m."), only with server numbers. */
    val flaggedDetail: String? = null,
    /** 1072 D: the OS flagged the reading as a mock location (the check-in went through and is flagged on the server). */
    val mockNote: String? = null,
    /** Signed URL exactly as the server returned it (?t= intact); loaded via MediaUrl, never built from a key. */
    val guardPhotoUrl: String? = null,
    /** 1076: a role whose clock-in the server refused: face-verify only. The card says Face verified. */
    val verifyOnly: Boolean = false,
)

/** Pure rules of the Guard clock-in / clock-out flow (Product final rulings 2026-09-30). Compose-free. */
object ClockInLogic {
    const val LOCK_CHIP = "App locked until you clock in"
    const val LOCK_TEXT = "You must clock in before accessing the visitor management app."
    const val LOCK_BUTTON = "CLOCK IN TO CONTINUE"
    const val SIGN_OUT = "Sign Out"
    const val SHIFT_COMPLETE = "Shift complete. See you tomorrow."
    const val SIGN_OUT_ON_DUTY = "You are still on duty. Sign out without clocking out?"
    const val CAMERA_OFF = "Camera permission is off. Please allow camera access in Settings to check in."
    const val LOCATION_NEEDED = "Location access is needed to check in."
    const val LOCATION_OFF_SOFT = "Location is off. Your check-in will be flagged for review."
    const val CHECKOUT_NEEDS_GPS = "Your location is required to clock out. Please turn on GPS and try again."
    const val SELFIE_HINT = "Position your face inside the oval"

    private val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    private val time12 = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)

    /** Phone clock, display only, never sent: Morning 05:00-11:59, Afternoon 12:00-16:59, Evening 17:00-04:59. */
    fun greetingWord(now: LocalTime): String = com.satcop.smartvisitor.kiosk.ui.Greeting.word(now)

    fun firstName(displayName: String): String =
        displayName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { "Guard" }

    fun greeting(displayName: String, now: LocalTime): String = com.satcop.smartvisitor.kiosk.ui.Greeting.line(firstName(displayName), now)

    /**
     * 1072 E: [todayIst] (the IST date now) makes a row from a previous day never unlock the app: if the server's `dutyDate`
     * is not today, the guard must clock in again. A missing dutyDate or a null [todayIst] keeps the old behaviour.
     */
    fun lockState(today: TodayAttendance?, loaded: Boolean, todayIst: java.time.LocalDate? = null): LockState {
        if (today == null) return if (loaded) LockState.LOCKED else LockState.UNKNOWN
        if (todayIst != null && isStaleDay(today, todayIst)) return LockState.LOCKED
        return when (today.state) {
            AttendanceState.PRESENT -> LockState.UNLOCKED
            AttendanceState.CLOCKED_OUT -> LockState.SHIFT_COMPLETE
            else -> LockState.LOCKED
        }
    }

    /** True when the server row is for an earlier IST day than [todayIst]. */
    fun isStaleDay(today: TodayAttendance, todayIst: java.time.LocalDate): Boolean {
        val d = (today.dutyDate ?: today.attendance?.dutyDate)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() } ?: return false
        return d.isBefore(todayIst)
    }

    /** Header text for the flow screens. */
    fun headerTitle(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Self Check In" else "Self Check Out"
    fun cameraLabel(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Front Camera · Check In" else "Front Camera · Check Out"
    fun proceedLabel(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Proceed to Check In" else "Proceed to Check Out"
    fun resultTitle(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Checked In!" else "Checked Out!"
    fun resultBody(mode: AttendanceMode) =
        if (mode == AttendanceMode.CHECK_IN) "You are now on duty. Have a safe shift!" else "Your shift is complete. Thank you!"
    fun actionLabel(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Check In" else "Check Out"

    /** 12-hour school time, e.g. "06:32 pm". Server time only (never the phone clock). */
    fun time12h(iso: String?): String {
        val odt = runCatching { OffsetDateTime.parse(iso?.trim().orEmpty()) }.getOrNull() ?: return "—"
        return odt.atZoneSameInstant(IST).format(time12).lowercase(Locale.ENGLISH)
    }

    /** Result "Gate": the assignment name, else the geofence name, else "—". */
    fun gateLabel(row: AttendanceRow?, fallbackGateName: String? = null): String =
        listOf(row?.dutyGateName, row?.gateName, fallbackGateName, row?.geofenceName)
            .firstOrNull { !it.isNullOrBlank() && it.trim() != "—" }?.trim() ?: "—"

    /** 1079: selfie shown as uploaded when the row says so or carries any selfie key/URL (photoKey, photoUrl, selfieUrl, selfiePhotoUrl, checkInSelfieUploaded). */
    fun selfieUploadedOf(row: AttendanceRow): Boolean =
        row.selfieUploaded ?: row.checkInSelfieUploaded
            ?: listOf(row.photoKey, row.photoUrl, row.selfieUrl, row.selfiePhotoUrl).any { !it.isNullOrBlank() }

    /** 1073: the result header always matches the action: a check-in says "Self Check In", a clock-out "Self Check Out". */
    fun resultHeader(result: ClockResult) = if (result.verifyOnly) "Face verified" else headerTitle(result.mode)
    fun resultTitleOf(result: ClockResult) = if (result.verifyOnly) "Face verified" else resultTitle(result.mode)

    /** Server geofence status of this action: nested block first, then the flat field. Lower-cased; null when absent. */
    fun geofenceStatusOf(row: AttendanceRow): String? =
        (row.geofence?.status ?: row.geofenceStatus)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    fun resultFrom(
        mode: AttendanceMode, row: AttendanceRow, fallbackGateName: String? = null,
        sentWithoutLocation: Boolean = false, sentMock: Boolean = false,
    ): ClockResult {
        val geoStatus = if (mode == AttendanceMode.CLOCK_OUT) {
            (row.outGeofenceStatus ?: row.outGeofence?.status ?: row.geofence?.status ?: row.geofenceStatus)?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        } else geofenceStatusOf(row)
        val softOutside = geoStatus == "outside"
        val outMode = mode == AttendanceMode.CLOCK_OUT
        val radius = if (outMode) (row.outGeofence?.radiusM ?: row.outGeofenceRadiusM ?: row.geofence?.radiusM ?: row.geofenceRadiusM)
        else (row.geofence?.radiusM ?: row.geofenceRadiusM)
        val distance = if (outMode) (row.outGeofence?.distanceM ?: row.geofence?.distanceM ?: row.distanceM) else (row.geofence?.distanceM ?: row.distanceM)
        val mock = sentMock || row.isMock == true || row.geofence?.isMock == true || row.flags.any { it.equals("MOCK_LOCATION", true) }
        val whenIso = if (mode == AttendanceMode.CHECK_IN) {
            row.timestamp ?: row.serverTime ?: row.checkInAt
        } else {
            row.outServerTime ?: row.timestamp ?: row.serverTime ?: row.checkOutAt
        }
        // Flagged note: a check-in that went without location is flagged by the server (soft mode) -> the ruling text.
        // Any other server warning is shown only when it reads as plain words (never a code like "no_gps").
        val flagged = when {
            mode == AttendanceMode.CHECK_IN && (sentWithoutLocation || geoStatus == "no_gps") -> LOCATION_OFF_SOFT
            softOutside -> if (mode == AttendanceMode.CLOCK_OUT) GuardGeoLogic.CLOCK_OUT_OUTSIDE_NOTE else GuardGeoLogic.SOFT_OUTSIDE_NOTE
            else -> row.warn?.takeIf { it.isNotBlank() && ' ' in it.trim() && !ErrorCopy.isTechnical(it) }
        }
        return ClockResult(
            mode = mode,
            action = row.action?.takeIf { it.isNotBlank() } ?: actionLabel(mode),
            time = row.actionTimeDisplay?.takeIf { it.isNotBlank() } ?: time12h(row.actionAt ?: whenIso),
            gate = gateLabel(row, fallbackGateName),
            selfieUploaded = selfieUploadedOf(row),
            recordId = (row.recordId ?: row.id)?.takeIf { it.isNotBlank() } ?: "—",
            flaggedNote = flagged,
            flaggedDetail = if (softOutside) GuardGeoLogic.softOutsideDetail(distance, radius) else null,
            mockNote = if (mock) GuardGeoLogic.MOCK_LOCATION_NOTE else null,
            guardPhotoUrl = row.guardPhotoUrl?.takeIf { it.isNotBlank() },
        )
    }

    /** Ruling R1: the three states of the Current Status card. */
    fun statusCard(today: TodayAttendance?): StatusCard {
        // Addendum: the server sends the card. Show it as returned; derive only when it is absent.
        today?.currentStatus?.let { c ->
            val title = c.label?.takeIf { it.isNotBlank() }
            val text = c.text?.takeIf { it.isNotBlank() }
            if (title != null && text != null) {
                return StatusCard(title, title, text, canProceed = !c.shiftComplete && c.nextAction != null, note = c.note?.takeIf { it.isNotBlank() })
            }
        }
        val row = today?.attendance
        return when (today?.state ?: AttendanceState.NONE) {
            AttendanceState.NONE -> StatusCard("Off duty", "Off duty", "Not checked in yet", canProceed = true)
            AttendanceState.PRESENT -> StatusCard(
                "On duty",
                "On duty",
                listOf("Checked in at ${time12h(row?.checkInAt ?: row?.timestamp)}", row?.gateName ?: row?.geofenceName)
                    .filter { !it.isNullOrBlank() }.joinToString(" · "),
                canProceed = true,
            )
            AttendanceState.CLOCKED_OUT -> StatusCard(
                "Checked out",
                "Checked out",
                "Checked in ${time12h(row?.checkInAt)} · Checked out ${time12h(row?.outServerTime ?: row?.checkOutAt)}",
                canProceed = false,
                note = "Your shift for today is complete.",
            )
        }
    }

    /** Errors that mean "the location reading is the problem": retry recaptures selfie AND location. */
    fun locationProblem(code: String): Boolean =
        AttendanceRules.needsFreshReading(code) || code.equals("GPS_REQUIRED", true) || code.equals("LOCATION_REQUIRED", true)

    /**
     * Location permission decision (Product ruling 6). The fence mode IS readable (GET /guards/me/geofence, else
     * /schools/me geoFenceMode) and restrict mode is stopped earlier by [GuardGeoLogic.locationGate]. When the mode could not be
     * read, a guard without location is not stopped here for CHECK-IN: the request goes with gpsMissing=true and the
     * SERVER decides (soft: allowed + flagged, restrict: refused -> [LOCATION_NEEDED]).
     * Clock-out without location is ALWAYS blocked here.
     */
    sealed interface LocationDecision {
        data object Proceed : LocationDecision
        data class ProceedFlagged(val notice: String) : LocationDecision
        data class Blocked(val message: String) : LocationDecision
    }

    fun locationDecision(mode: AttendanceMode, hasPermission: Boolean, hasFix: Boolean): LocationDecision = when {
        hasPermission && hasFix -> LocationDecision.Proceed
        mode == AttendanceMode.CLOCK_OUT -> LocationDecision.Blocked(CHECKOUT_NEEDS_GPS)
        !hasPermission -> LocationDecision.ProceedFlagged(LOCATION_OFF_SOFT)
        else -> LocationDecision.Proceed // permission granted, reading still coming: normal readiness rules apply
    }

    /** Server refused a location-less check-in -> show the restrict-mode copy. */
    fun checkInRefusedWithoutLocation(code: String, sentWithoutLocation: Boolean): Boolean =
        sentWithoutLocation && (locationProblem(code) || code.equals("LOCATION_REQUIRED", true) || code.equals("FORBIDDEN", true))
}
