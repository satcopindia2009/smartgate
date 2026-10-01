package com.satcop.smartvisitor.kiosk.ui.guardhome

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.geo.CaptureStamp
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 1073: the face step IS the clock-in. One photo -> face-verify -> check-in with the SAME photo + GPS + time. Compose-free. */
object FaceClockInLogic {
    /** Guard sign-in always goes through the face clock-in, whatever faceRequired says. */
    fun isGuardRole(role: String?): Boolean = role?.trim()?.equals("guard", ignoreCase = true) == true

    /** 1076: roles that go lock screen -> face -> clock-in. Host and admin are NOT in it (no face, straight in). */
    private val clockInRoles = setOf("guard", "gate", "gate_staff", "security", "security_head")

    fun normalizeRole(role: String?): String = role?.trim()?.lowercase()?.replace('-', '_').orEmpty()

    /** One shared rule for the client: needsFace = faceRequired OR requiresClockIn(role). */
    fun requiresClockIn(role: String?): Boolean = normalizeRole(role) in clockInRoles

    /**
     * 1076: the server may refuse the guard attendance API for a non-guard role (role/route refusal, not a location or face
     * refusal). Then the role falls back to face-verify only and the card says "Verified". Guards never fall back.
     */
    fun verifyOnlyFallback(role: String?, e: Throwable): Boolean {
        if (normalizeRole(role) == "guard") return false
        val api = e as? ApiException ?: return false
        if (alreadyIn(api)) return false
        if (api.details.containsKey("reasonCode")) return false
        val code = api.code.uppercase()
        return api.httpStatus in setOf(404, 405) ||
            code in setOf("FORBIDDEN", "NOT_FOUND", "ROLE_NOT_ALLOWED", "ROLE_FORBIDDEN", "NOT_A_GUARD", "NOT_GUARD")
    }

    /** The card for a verify-only role. Time is the server login time of the face-verify. */
    fun verifiedResult(loginAtIso: String?, gate: String?): ClockResult = ClockResult(
        mode = AttendanceMode.CHECK_IN, action = "Face verify", time = ClockInLogic.time12h(loginAtIso),
        gate = gate?.takeIf { it.isNotBlank() && it != "—" } ?: "—", selfieUploaded = false, recordId = "—",
        flaggedNote = null, verifyOnly = true,
    )

    /** The check-in request that reuses the face photo. GPS only as a real pair; gpsMissing otherwise. */
    fun buildCheckIn(photoBase64: String, stamp: CaptureStamp, now: Instant, attemptId: String, gateId: String? = null): AttendanceRequest {
        val mock = if (stamp.isMock) true else null
        val pair = stamp.lat != null && stamp.lng != null && stamp.lat in -90.0..90.0 && stamp.lng in -180.0..180.0
        return AttendanceRequest(
            imageBase64 = photoBase64,
            lat = if (pair) stamp.lat else null,
            lng = if (pair) stamp.lng else null,
            accuracyM = if (pair) stamp.accuracyM?.toDouble() else null,
            capturedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC)),
            gpsMissing = !pair,
            attemptId = attemptId,
            isMock = mock,
            gateId = gateId?.takeIf { it.isNotBlank() },
        )
    }

    /** The server already has today's check-in (or the shift is done): the guard is in; open the app. */
    fun alreadyIn(e: Throwable): Boolean {
        val c = (e as? ApiException)?.let { GuardGeoLogic.canonicalCode(it) } ?: return false
        return c == "ALREADY_CHECKED_IN" || c == "ALREADY_CLOCKED_OUT"
    }

    /**
     * 1078: the card row after an already-in check-in error.
     * ALREADY_CHECKED_IN: details.attendance (the first open row, flattened "attendance.*") first, then GET /attendance/me/today.
     * ALREADY_CLOCKED_OUT: never a card (Shift complete). A row from an EARLIER business day is not "checked in today":
     * it is dropped (no card, no stale id/time). A row without any date keeps the old behaviour (used).
     */
    fun rowForAlreadyIn(
        e: Throwable,
        today: com.satcop.smartvisitor.kiosk.data.model.TodayAttendance?,
        businessDate: java.time.LocalDate? = null,
    ): com.satcop.smartvisitor.kiosk.data.model.AttendanceRow? {
        if (!alreadyIn(e) || isClockedOut(e)) return null
        val candidates = listOfNotNull(
            rowFromDetails(e as? ApiException),
            today?.attendance?.takeIf { hasRecord(it) }?.let { r -> if (r.dutyDate.isNullOrBlank() && !today.dutyDate.isNullOrBlank()) r.copy(dutyDate = today.dutyDate) else r },
        )
        return candidates.firstOrNull { it.checkOutAt.isNullOrBlank() && !isStaleRow(it, businessDate) }
    }

    /** True when the row's dutyDate (else the IST date of checkInAt, else nothing) is before today's business date. No date = not stale. */
    fun isStaleRow(r: com.satcop.smartvisitor.kiosk.data.model.AttendanceRow, businessDate: java.time.LocalDate?): Boolean {
        if (businessDate == null) return false
        val duty = r.dutyDate?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { java.time.LocalDate.parse(it.take(10)) }.getOrNull() }
        if (duty != null) return duty.isBefore(businessDate)
        val inAt = (r.checkInAt ?: r.timestamp ?: r.serverTime)?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        val d = runCatching { java.time.OffsetDateTime.parse(inAt).atZoneSameInstant(GuardHomeLogic.IST).toLocalDate() }.getOrNull()
            ?: runCatching { java.time.LocalDate.parse(inAt.take(10)) }.getOrNull() ?: return false
        return d.isBefore(businessDate)
    }

    private fun isClockedOut(e: Throwable): Boolean =
        (e as? ApiException)?.let { GuardGeoLogic.canonicalCode(it) } == "ALREADY_CLOCKED_OUT"

    private fun hasRecord(r: com.satcop.smartvisitor.kiosk.data.model.AttendanceRow): Boolean =
        !r.id.isNullOrBlank() || !r.checkInAt.isNullOrBlank() || !r.serverTime.isNullOrBlank() || !r.timestamp.isNullOrBlank()

    /** Error details are flattened ("attendance.id", "id", ...). Only a row that has an id or a time is usable. */
    fun rowFromDetails(e: ApiException?): com.satcop.smartvisitor.kiosk.data.model.AttendanceRow? {
        val d = e?.details ?: return null
        fun pick(vararg keys: String): String? = keys.firstNotNullOfOrNull { k -> d[k]?.takeIf { it.isNotBlank() } }
        val id = pick("attendance.id", "attendanceId", "id", "recordId")
        val inAt = pick("attendance.checkInAt", "checkInAt", "attendance.timestamp", "timestamp", "attendance.serverTime", "serverTime")
        if (id == null && inAt == null) return null
        return com.satcop.smartvisitor.kiosk.data.model.AttendanceRow(
            id = id, checkInAt = inAt, checkOutAt = pick("attendance.checkOutAt", "checkOutAt"),
            gateName = pick("attendance.gateName", "gateName", "dutyGateName"),
            dutyGateName = pick("attendance.dutyGateName", "dutyGateName"),
            dutyDate = pick("attendance.dutyDate", "dutyDate"),
            attendanceStatus = pick("attendance.attendanceStatus", "attendanceStatus"),
        )
    }

    /** Reason shown on the face screen when the clock-in after a good face check failed. The guard stays out. */
    fun checkInFailureMessage(e: Throwable, sentWithoutLocation: Boolean, fallbackRadiusM: Double? = null): String {
        val api = (e as? ApiException)?.let(GuardGeoLogic::normalize) ?: return ErrorCopy.forThrowable(e)
        GuardGeoLogic.outsideMessageFrom(api, fallbackRadiusM)?.let { return it }
        if (ClockInLogic.checkInRefusedWithoutLocation(api.code, sentWithoutLocation)) return ClockInLogic.LOCATION_NEEDED
        return ErrorCopy.forApi(api)
    }
}
