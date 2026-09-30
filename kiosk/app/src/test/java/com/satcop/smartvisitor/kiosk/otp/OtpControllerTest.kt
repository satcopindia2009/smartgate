package com.satcop.smartvisitor.kiosk.otp

import com.satcop.smartvisitor.kiosk.data.otp.*
import com.satcop.smartvisitor.kiosk.ui.otp.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test

private class FakeOtp : OtpRepository {
    var sendError: OtpApiException? = null
    var verifyError: OtpApiException? = null
    var mock = false
    val sends = mutableListOf<OtpSendRequest>()
    val verifies = mutableListOf<Pair<String, String>>()
    override suspend fun settings() = OtpSettings(visitorOtpEnabled = true, otpChannel = "sms_then_whatsapp")
    override suspend fun send(req: OtpSendRequest): OtpSent {
        sends += req; sendError?.let { throw it }
        return OtpSent("otp-${sends.size}", "••••••1234", "sms", 300, 60, 3, mock)
    }
    var status = OtpStatus(false)
    val resends = mutableListOf<String>()
    override suspend fun resend(otpId: String, mobile: String?, withAuth: Boolean): OtpSent {
        resends += otpId; sendError?.let { throw it }
        return OtpSent("otp-r${resends.size}", "••••••1234", "sms", 300, 60, 2, mock)
    }
    override suspend fun status(otpId: String, withAuth: Boolean) = status
    override suspend fun verify(otpId: String, code: String, withAuth: Boolean): OtpVerified {
        verifies += otpId to code; verifyError?.let { throw it }
        return OtpVerified("2026-09-30T19:00:00+05:30", "reset-tok")
    }
    override suspend fun resetPassword(resetToken: String, newPassword: String) {}
}

class OtpControllerTest {
    private val repo = FakeOtp()
    private fun c(p: OtpPurpose = OtpPurpose.STAFF_VERIFY, demo: Boolean = false) =
        OtpController(CoroutineScope(Dispatchers.Unconfined), repo, p, Dispatchers.Unconfined, { "att" }, demo)

    @Test fun staffSendHasNoMobileAndShowsMaskedNumber() {
        val o = c(); o.start()
        assertNull(repo.sends.single().mobile)
        assertEquals("••••••1234", o.state.value.maskedMobile)
        assertEquals(60, o.state.value.resendInSec) // from the response (resendAfterSec), not a constant
        assertFalse(o.state.value.resendEnabled)
    }

    @Test fun visitorAndResetSendTheNumber() {
        val o = c(OtpPurpose.PASSWORD_RESET); o.start("9800011100")
        assertEquals("9800011100", repo.sends.single().mobile)
        assertEquals("password_reset", repo.sends.single().purpose.wire)
    }

    @Test fun codeIsSixDigitsOnlyAndVerifyNeedsAll() {
        val o = c(); o.start()
        o.setCode("12a3")
        assertEquals("123", o.state.value.code); assertFalse(o.state.value.canVerify)
        o.setCode("1234567")
        assertEquals("123456", o.state.value.code); assertTrue(o.state.value.canVerify)
    }

    @Test fun successIsVerifiedAndCodeIsCleared() {
        val o = c(); o.start(); o.setCode("123456"); o.verify()
        assertEquals(OtpPhase.SUCCESS, o.state.value.phase)
        assertEquals("", o.state.value.code)
        assertEquals("reset-tok", o.state.value.resetToken)
        assertEquals("Mobile number verified.", o.state.value.message(OtpCopy.EN))
    }

    @Test fun wrongCodeShowsTriesLeftAndClearsBoxes() {
        val o = c(); o.start(); o.setCode("000000")
        repo.verifyError = OtpApiException("OTP_INVALID", triesLeft = 3); o.verify()
        val s = o.state.value
        assertEquals(OtpPhase.WRONG, s.phase); assertEquals("", s.code); assertEquals(1, s.shake)
        assertEquals("That code is not right. 3 tries left.", s.message(OtpCopy.EN))
        assertEquals("यह कोड सही नहीं है। 3 प्रयास बाकी हैं।", s.message(OtpCopy.HI))
    }

    @Test fun usedCodeLooksLikeWrongWithoutTries() {
        val o = c(); o.start(); o.setCode("111111")
        repo.verifyError = OtpApiException("OTP_USED", serverMessage = "That code is not right."); o.verify()
        assertEquals("That code is not right.", o.state.value.message(OtpCopy.EN))
    }

    @Test fun expiredAllowsImmediateResend() {
        val o = c(); o.start(); o.setCode("111111")
        repo.verifyError = OtpApiException("OTP_EXPIRED"); o.verify()
        assertEquals(OtpPhase.EXPIRED, o.state.value.phase)
        assertEquals("This code has expired. Request a new one.", o.state.value.message(OtpCopy.EN))
        assertTrue(o.state.value.resendEnabled)
        o.resend(); assertEquals(2, repo.sends.size)
    }

    @Test fun lockedDisablesEverythingButBack() {
        val o = c(); o.start(); o.setCode("111111")
        repo.verifyError = OtpApiException("OTP_LOCKED", retryAfterSec = 1800); o.verify()
        val s = o.state.value
        assertEquals(OtpPhase.LOCKED, s.phase); assertFalse(s.boxesEnabled); assertFalse(s.canVerify); assertFalse(s.resendVisible)
        assertEquals("Too many attempts. Try again in 30 minutes.", s.message(OtpCopy.EN))
    }

    @Test fun resendCountdownThenEnabledAndLimit() {
        val o = c(); o.start()
        repeat(60) { o.tick() } // cooldown is the server's resendAfterSec (60), not a constant in the app
        assertTrue(o.state.value.resendEnabled)
        repo.sendError = OtpApiException("OTP_RESEND_LIMIT"); o.resend()
        assertEquals(OtpPhase.RESEND_LIMIT, o.state.value.phase); assertFalse(o.state.value.resendVisible)
        assertEquals("You have used all resends. Try again later.", o.state.value.message(OtpCopy.EN))
    }

    @Test fun sendFailedShowsBannerAndOtherChannel() {
        repo.sendError = OtpApiException("OTP_SEND_FAILED"); val o = c(); o.start()
        assertEquals(OtpPhase.SEND_FAILED, o.state.value.phase); assertTrue(o.state.value.otherChannelVisible)
        assertEquals("Could not send the code. Try again or use another option.", o.state.value.message(OtpCopy.EN))
    }

    @Test fun sendAgainFollowsServerShowSendAgainNeverAFailureBanner() {
        val o = c(); o.start(); repeat(120) { o.tick() }
        assertFalse(o.state.value.noReceipt) // no local 60 s timer decides this
        repo.status = OtpStatus(showSendAgain = true); o.pollStatus()
        assertTrue(o.state.value.noReceipt); assertNull(o.state.value.message(OtpCopy.EN))
    }

    @Test fun resendUsesResendEndpointAndTakesNewOtpId() {
        val o = c(); o.start(); repeat(60) { o.tick() }
        repo.status = OtpStatus(true); o.pollStatus()
        o.resend()
        assertEquals(listOf("otp-1"), repo.resends); assertEquals("otp-r1", o.state.value.otpId)
        assertEquals(60, o.state.value.resendInSec); assertFalse(o.state.value.noReceipt)
    }

    @Test fun serverTextIsShownAsReturned() {
        val o = c(); o.start(); o.setCode("111111")
        repo.verifyError = OtpApiException("OTP_INVALID", triesLeft = 4, serverMessage = "That code is not right. 4 tries left."); o.verify()
        assertEquals("That code is not right. 4 tries left.", o.state.value.message(OtpCopy.EN))
        repo.verifyError = OtpApiException("OTP_LOCKED", retryAfterSec = 1800, serverMessage = "Too many attempts. Try again in 30 minutes."); o.setCode("111111"); o.verify()
        assertEquals(OtpPhase.LOCKED, o.state.value.phase); assertEquals("Too many attempts. Try again in 30 minutes.", o.state.value.message(OtpCopy.EN))
    }

    @Test fun resendWaitTakesCooldownFromRetryAfterSec() {
        val o = c(); o.start(); repeat(60) { o.tick() }
        repo.sendError = OtpApiException("OTP_RESEND_WAIT", retryAfterSec = 47, serverMessage = "Resend code in 47 s."); o.resend()
        assertEquals(47, o.state.value.resendInSec)
    }

    @Test fun visitorVerifyIdIsKeptForTheVisit() {
        val o = c(OtpPurpose.VISITOR_VERIFY); o.start("9800011100"); o.setCode("123456"); o.verify()
        assertEquals("otp-1", o.state.value.visitorVerifyId)
    }

    @Test fun changeNumberVoidsTheCode() {
        val o = c(OtpPurpose.VISITOR_VERIFY); o.start("9800011100"); o.setCode("123456")
        o.voidCode()
        assertNull(o.state.value.otpId); assertEquals("", o.state.value.code); assertFalse(o.state.value.canVerify)
    }

    @Test fun entryPointsHiddenUntilBoundAndSettingOn() {
        assertFalse(OtpAvailability.staffVerifyVisible(false))
        assertFalse(OtpAvailability.forgotPasswordVisible(false))
        assertFalse(OtpAvailability.visitorCardVisible(OtpSettings(visitorOtpEnabled = true), bound = false))
        assertFalse(OtpAvailability.visitorCardVisible(OtpSettings(visitorOtpEnabled = false), bound = true))
        assertTrue(OtpAvailability.visitorCardVisible(OtpSettings(visitorOtpEnabled = true), bound = true))
        assertFalse(OtpAvailability.visitorSkipVisible(OtpSettings(visitorOtpEnabled = true, visitorOtpAllowSkip = false)))
        assertTrue(OtpAvailability.visitorSkipVisible(OtpSettings(visitorOtpEnabled = true, visitorOtpAllowSkip = true)))
    }

    @Test fun codeNeverAppearsInStateMessages() {
        val o = c(); o.start(); o.setCode("654321")
        repo.verifyError = OtpApiException("OTP_INVALID", triesLeft = 2); o.verify()
        assertFalse(o.state.value.message(OtpCopy.EN)!!.contains("654321"))
        assertEquals("", o.state.value.code)
    }
}
