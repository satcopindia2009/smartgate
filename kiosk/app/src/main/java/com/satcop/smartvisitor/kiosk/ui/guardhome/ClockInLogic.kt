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
    /** Signed URL exactly as the server returned it (?t= intact); loaded via MediaUrl, never built from a key. */
    val guardPhotoUrl: String? = null,
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
    fun greetingWord(now: LocalTime): String = when {
        now >= LocalTime.of(5, 0) && now < LocalTime.NOON -> "Morning"
        now >= LocalTime.NOON && now < LocalTime.of(17, 0) -> "Afternoon"
        else -> "Evening"
    }

    fun firstName(displayName: String): String =
        displayName.trim().split(Regex("\\s+")).firstOrNull().orEmpty().ifBlank { "Guard" }

    fun greeting(displayName: String, now: LocalTime): String = "Good ${greetingWord(now)}, ${firstName(displayName)}"

    fun lockState(today: TodayAttendance?, loaded: Boolean): LockState = when {
        today == null -> if (loaded) LockState.LOCKED else LockState.UNKNOWN
        today.state == AttendanceState.PRESENT -> LockState.UNLOCKED
        today.state == AttendanceState.CLOCKED_OUT -> LockState.SHIFT_COMPLETE
        else -> LockState.LOCKED
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
        listOf(row?.gateName, fallbackGateName, row?.geofenceName)
            .firstOrNull { !it.isNullOrBlank() }?.trim() ?: "—"

    fun resultFrom(mode: AttendanceMode, row: AttendanceRow, fallbackGateName: String? = null, sentWithoutLocation: Boolean = false): ClockResult {
        val whenIso = if (mode == AttendanceMode.CHECK_IN) {
            row.timestamp ?: row.serverTime ?: row.checkInAt
        } else {
            row.outServerTime ?: row.timestamp ?: row.serverTime ?: row.checkOutAt
        }
        // Flagged note: a check-in that went without location is flagged by the server (soft mode) -> the ruling text.
        // Any other server warning is shown only when it reads as plain words (never a code like "no_gps").
        val flagged = when {
            mode == AttendanceMode.CHECK_IN && (sentWithoutLocation || row.geofenceStatus.equals("no_gps", true)) -> LOCATION_OFF_SOFT
            else -> row.warn?.takeIf { it.isNotBlank() && ' ' in it.trim() && !ErrorCopy.isTechnical(it) }
        }
        return ClockResult(
            mode = mode,
            action = row.action?.takeIf { it.isNotBlank() } ?: actionLabel(mode),
            time = row.actionTimeDisplay?.takeIf { it.isNotBlank() } ?: time12h(row.actionAt ?: whenIso),
            gate = gateLabel(row, fallbackGateName),
            selfieUploaded = row.selfieUploaded ?: !(row.photoKey.isNullOrBlank() && row.photoUrl.isNullOrBlank()),
            recordId = (row.recordId ?: row.id)?.takeIf { it.isNotBlank() } ?: "—",
            flaggedNote = flagged,
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
     * Location permission decision (Product ruling 6). The app cannot read the school fence mode (settings are
     * admin-only), so a guard without location is not stopped by the app for CHECK-IN: the request goes with
     * gpsMissing=true and the SERVER decides (soft: allowed + flagged, restrict: refused -> [LOCATION_NEEDED]).
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
