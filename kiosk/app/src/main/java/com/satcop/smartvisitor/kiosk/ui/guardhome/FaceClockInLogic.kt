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

    /** The check-in request that reuses the face photo. GPS only as a real pair; gpsMissing otherwise. */
    fun buildCheckIn(photoBase64: String, stamp: CaptureStamp, now: Instant, attemptId: String): AttendanceRequest {
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
