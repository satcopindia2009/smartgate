package com.satcop.smartvisitor.kiosk.qa

import com.satcop.smartvisitor.kiosk.data.otp.OtpApiException
import com.satcop.smartvisitor.kiosk.data.otp.OtpRepository
import com.satcop.smartvisitor.kiosk.data.otp.OtpSendRequest
import com.satcop.smartvisitor.kiosk.data.otp.OtpSent
import com.satcop.smartvisitor.kiosk.data.otp.OtpSettings
import com.satcop.smartvisitor.kiosk.data.otp.OtpVerified
import kotlinx.coroutines.delay

/** Forced OTP states for QA, set by [QaSwitchReceiver] (adb broadcast). Debugqa build only. */
object QaOtpState {
    /** ok | fail | limit | daily | wait */
    @Volatile var send: String = "ok"
    /** ok | wrong | expired | locked | used  (ok = real behaviour: 123456 accepted, other codes count down 5 tries) */
    @Volatile var verify: String = "ok"
    @Volatile var visitorOtpEnabled: Boolean = true
    @Volatile var visitorOtpAllowSkip: Boolean = false
    @Volatile var otpChannel: String = "sms_then_whatsapp"

    fun reset() {
        send = "ok"; verify = "ok"; visitorOtpEnabled = true; visitorOtpAllowSkip = false; otpChannel = "sms_then_whatsapp"
    }
}

/** Stand-in for the OTP API in the QA build: mock code 123456, same error codes as the Product contract. */
class QaOtpRepository : OtpRepository {
    private var counter = 0
    private var resends = 0
    private val tries = mutableMapOf<String, Int>()

    override suspend fun settings() = OtpSettings(
        otpMode = "mock", otpChannel = QaOtpState.otpChannel,
        visitorOtpEnabled = QaOtpState.visitorOtpEnabled, visitorOtpAllowSkip = QaOtpState.visitorOtpAllowSkip,
    )

    override suspend fun send(req: OtpSendRequest): OtpSent {
        delay(400)
        when (QaOtpState.send.lowercase()) {
            "fail" -> throw OtpApiException("OTP_SEND_FAILED")
            "limit" -> throw OtpApiException("OTP_RESEND_LIMIT")
            "daily" -> throw OtpApiException("OTP_DAILY_LIMIT")
            "wait" -> throw OtpApiException("OTP_RESEND_WAIT", retryAfterSec = 30)
        }
        val id = "qa-otp-${++counter}"
        tries[id] = 5
        resends++
        return OtpSent(id, "\u2022\u2022\u2022\u2022\u2022\u20221234", req.channel ?: "sms", 300, 60, (3 - resends + 1).coerceAtLeast(0), mock = true, demo = true)
    }

    override suspend fun resend(otpId: String, mobile: String?, withAuth: Boolean): OtpSent =
        send(OtpSendRequest(com.satcop.smartvisitor.kiosk.data.otp.OtpPurpose.STAFF_VERIFY, mobile, null, "qa-resend"))

    override suspend fun verify(otpId: String, code: String, withAuth: Boolean): OtpVerified {
        delay(400)
        when (QaOtpState.verify.lowercase()) {
            "wrong" -> throw OtpApiException("OTP_INVALID", triesLeft = 3)
            "expired" -> throw OtpApiException("OTP_EXPIRED")
            "locked" -> throw OtpApiException("OTP_LOCKED", retryAfterSec = 1800)
            "used" -> throw OtpApiException("OTP_USED")
        }
        val left = tries[otpId] ?: throw OtpApiException("OTP_EXPIRED")
        if (left <= 0) throw OtpApiException("OTP_EXPIRED")
        if (code != "123456") {
            tries[otpId] = left - 1
            throw OtpApiException("OTP_INVALID", triesLeft = left - 1)
        }
        return OtpVerified("2026-09-30T19:00:00+05:30", "qa-reset-token", visitorVerifyId = otpId)
    }

    override suspend fun resetPassword(resetToken: String, newPassword: String) {
        delay(300)
    }
}
