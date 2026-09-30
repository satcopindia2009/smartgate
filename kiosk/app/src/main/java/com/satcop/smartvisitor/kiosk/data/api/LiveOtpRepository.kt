package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.otp.OtpApiException
import com.satcop.smartvisitor.kiosk.data.otp.OtpRepository
import com.satcop.smartvisitor.kiosk.data.otp.OtpSendRequest
import com.satcop.smartvisitor.kiosk.data.otp.OtpSent
import com.satcop.smartvisitor.kiosk.data.otp.OtpSettings
import com.satcop.smartvisitor.kiosk.data.otp.OtpStatus
import com.satcop.smartvisitor.kiosk.data.otp.OtpVerified
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OTP bound to the Backend contract 2026-09-30 (POST /otp/send, /otp/resend, /otp/verify, /otp/password-reset,
 * GET /otp/status/{otpId}). Everything shown or timed comes from the responses; nothing is hard-coded here.
 * No test headers are ever sent (X-Test-* are QA/admin aids only).
 */
class LiveOtpRepository(private val api: LiveVisitorApi = LiveVisitorApi()) : OtpRepository {
    override suspend fun settings(): OtpSettings = io {
        parseSettings(api.schoolSettingsText())
    }

    override suspend fun send(req: OtpSendRequest): OtpSent = io {
        val body = buildJsonObject {
            put("purpose", req.purpose.wire)
            // staff_verify: the mobile comes from the account, never from the app.
            if (req.purpose != com.satcop.smartvisitor.kiosk.data.otp.OtpPurpose.STAFF_VERIFY && !req.mobile.isNullOrBlank()) put("mobile", req.mobile)
            req.channel?.let { put("channel", it) }
            put("attemptId", req.attemptId)
            req.consentAt?.let { put("consentAt", it) }
        }
        val auth = req.purpose != com.satcop.smartvisitor.kiosk.data.otp.OtpPurpose.PASSWORD_RESET
        call { parseSent(api.otpRequest("POST", "/otp/send", body.toString(), auth)) }
    }

    override suspend fun resend(otpId: String, mobile: String?, withAuth: Boolean): OtpSent = io {
        val body = buildJsonObject {
            put("otpId", otpId)
            if (!mobile.isNullOrBlank()) put("mobile", mobile)
        }
        call { parseSent(api.otpRequest("POST", "/otp/resend", body.toString(), withAuth)) }
    }

    override suspend fun verify(otpId: String, code: String, withAuth: Boolean): OtpVerified = io {
        val body = buildJsonObject { put("otpId", otpId); put("code", code) }
        call { parseVerified(api.otpRequest("POST", "/otp/verify", body.toString(), withAuth)) }
    }

    override suspend fun status(otpId: String, withAuth: Boolean): OtpStatus = io {
        call { parseStatus(api.otpRequest("GET", "/otp/status/$otpId", null, withAuth)) }
    }

    override suspend fun resetPassword(resetToken: String, newPassword: String) {
        io {
            val body = buildJsonObject { put("resetToken", resetToken); put("newPassword", newPassword) }
            call { api.otpRequest("POST", "/otp/password-reset", body.toString(), false) }
        }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    /** Maps the API error body to [OtpApiException] (keeps the server text); session/face errors pass through. */
    private inline fun <T> call(block: () -> T): T = try {
        block()
    } catch (e: ApiException) {
        throw mapError(e)
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        private val otpCodes = setOf(
            "OTP_INVALID", "OTP_EXPIRED", "OTP_USED", "OTP_LOCKED", "OTP_RESEND_WAIT", "OTP_RESEND_LIMIT", "OTP_DAILY_LIMIT",
            "OTP_SEND_FAILED", "OTP_REQUIRED", "NO_MOBILE", "INVALID_MOBILE", "RESET_TOKEN_INVALID", "BLACKLISTED", "VALIDATION",
        )

        fun mapError(e: ApiException): Exception {
            val code = e.code.uppercase()
            // Session end / face gate keep their own paths (they are not OTP errors).
            if (code !in otpCodes) return e
            return OtpApiException(
                code = code,
                triesLeft = e.details["triesLeft"]?.toIntOrNull(),
                retryAfterSec = e.details["retryAfterSec"]?.toIntOrNull(),
                serverMessage = e.message.takeIf { it.isNotBlank() && !ErrorCopy.isTechnical(it) },
            )
        }

        fun parseSent(text: String): OtpSent {
            val o = json.parseToJsonElement(text).jsonObject
            return OtpSent(
                otpId = o["otpId"]!!.jsonPrimitive.content,
                maskedMobile = o["maskedMobile"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                channel = o["channel"]?.jsonPrimitive?.contentOrNull ?: "sms",
                expiresInSec = o["expiresInSec"]?.jsonPrimitive?.intOrNull ?: 0,
                resendAfterSec = o["resendAfterSec"]?.jsonPrimitive?.intOrNull ?: 0,
                resendsLeft = o["resendsLeft"]?.jsonPrimitive?.intOrNull ?: 0,
                mock = (o["meta"] as? JsonObject)?.get("mock")?.jsonPrimitive?.booleanOrNull == true,
                demo = (o["meta"] as? JsonObject)?.get("demo")?.jsonPrimitive?.booleanOrNull == true,
            )
        }

        fun parseVerified(text: String): OtpVerified {
            val o = json.parseToJsonElement(text).jsonObject
            if (o["verified"]?.jsonPrimitive?.booleanOrNull != true) throw OtpApiException("OTP_INVALID")
            return OtpVerified(
                verifiedAt = o["verifiedAt"]?.jsonPrimitive?.contentOrNull,
                resetToken = o["resetToken"]?.jsonPrimitive?.contentOrNull,
                visitorVerifyId = o["visitorVerifyId"]?.jsonPrimitive?.contentOrNull,
            )
        }

        fun parseStatus(text: String): OtpStatus {
            val o = json.parseToJsonElement(text).jsonObject
            return OtpStatus(
                showSendAgain = o["showSendAgain"]?.jsonPrimitive?.booleanOrNull == true,
                expiresInSec = o["expiresInSec"]?.jsonPrimitive?.intOrNull,
            )
        }

        /** otp* keys of the school object; absent keys keep the safe defaults (visitor OTP off). */
        fun parseSettings(text: String): OtpSettings {
            val o = json.parseToJsonElement(text).jsonObject
            return OtpSettings(
                otpMode = o["otpMode"]?.jsonPrimitive?.contentOrNull ?: "live",
                otpChannel = o["otpChannel"]?.jsonPrimitive?.contentOrNull ?: "sms",
                visitorOtpEnabled = o["visitorOtpEnabled"]?.jsonPrimitive?.booleanOrNull == true,
                visitorOtpAllowSkip = o["visitorOtpAllowSkip"]?.jsonPrimitive?.booleanOrNull == true,
            )
        }
    }
}
