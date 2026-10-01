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

    /** Reason shown on the face screen when the clock-in after a good face check failed. The guard stays out. */
    fun checkInFailureMessage(e: Throwable, sentWithoutLocation: Boolean, fallbackRadiusM: Double? = null): String {
        val api = (e as? ApiException)?.let(GuardGeoLogic::normalize) ?: return ErrorCopy.forThrowable(e)
        GuardGeoLogic.outsideMessageFrom(api, fallbackRadiusM)?.let { return it }
        if (ClockInLogic.checkInRefusedWithoutLocation(api.code, sentWithoutLocation)) return ClockInLogic.LOCATION_NEEDED
        return ErrorCopy.forApi(api)
    }
}
