package com.satcop.smartvisitor.kiosk.data.otp

/** Why a code is being sent (Product OTP scope, section B). */
enum class OtpPurpose(val wire: String) {
    STAFF_VERIFY("staff_verify"), PASSWORD_RESET("password_reset"), VISITOR_VERIFY("visitor_verify")
}

data class OtpSendRequest(
    val purpose: OtpPurpose,
    /** Not sent for staff_verify (server uses the token's user). 10 digits for the others. */
    val mobile: String? = null,
    /** "sms" | "whatsapp" | null = school default. */
    val channel: String? = null,
    /** One id per user tap, so a retried request is not a second send. */
    val attemptId: String,
    /** visitor_verify: when the guard accepted the visitor notice (ISO +05:30). */
    val consentAt: String? = null,
)

data class OtpSent(
    val otpId: String,
    /** Already masked by the server, e.g. "••••••1234". Never a raw number. */
    val maskedMobile: String,
    val channel: String,
    val expiresInSec: Int,
    val resendAfterSec: Int,
    val resendsLeft: Int,
    /** meta.mock from the response; the demo chip needs this AND a demo tenant. */
    val mock: Boolean,
    /** meta.demo from the response (demo tenant). */
    val demo: Boolean = false,
)

data class OtpVerified(
    val verifiedAt: String?,
    val resetToken: String?,
    /** visitor_verify only: pass as otpId on POST /visits. */
    val visitorVerifyId: String? = null,
)

/** GET /otp/status/{otpId}: the "Send again" link shows only when the SERVER says so. A missing receipt is never a failure. */
data class OtpStatus(val showSendAgain: Boolean, val expiresInSec: Int? = null)

/** School settings from GET /schools/me/settings (Product OTP scope). */
data class OtpSettings(
    val otpMode: String = "live",
    val otpChannel: String = "sms",
    val visitorOtpEnabled: Boolean = false,
    val visitorOtpAllowSkip: Boolean = false,
) {
    val offersOtherChannel: Boolean get() = otpChannel.equals("sms_then_whatsapp", true)
}

/** Server refusal with the OTP codes of the contract: OTP_INVALID (triesLeft), OTP_EXPIRED, OTP_USED, OTP_LOCKED (retryAfterSec), ... */
class OtpApiException(
    val code: String,
    val triesLeft: Int? = null,
    val retryAfterSec: Int? = null,
    /** The API's own error text (shown as returned); null for client-side failures. */
    val serverMessage: String? = null,
) : RuntimeException(serverMessage ?: code)

/**
 * Data seam for every OTP screen. Release binds [com.satcop.smartvisitor.kiosk.data.api.LiveOtpRepository] (the live
 * contract 2026-09-30); the QA-only build swaps in a fake. Timers/limits are never hard-coded: they come from responses.
 */
interface OtpRepository {
    suspend fun settings(): OtpSettings
    suspend fun send(req: OtpSendRequest): OtpSent
    /** New otpId, the previous code is void. [mobile] is needed for password_reset / visitor_verify. */
    suspend fun resend(otpId: String, mobile: String? = null, withAuth: Boolean = true): OtpSent
    suspend fun verify(otpId: String, code: String, withAuth: Boolean = true): OtpVerified
    suspend fun status(otpId: String, withAuth: Boolean = true): OtpStatus = OtpStatus(false)
    suspend fun resetPassword(resetToken: String, newPassword: String)
}

/** Entry points (Verify mobile, Forgot password, Add Visitor OTP card) show only when the contract is bound. */
object OtpAvailability {
    /** Backend OTP contract (2026-09-30) is live and bound. Visitor OTP still needs the school setting. */
    const val CONTRACT_BOUND: Boolean = true

    /** True when the real contract is bound, or inside the QA-only build (fake repository). Release: bound only. */
    val reachable: Boolean get() = CONTRACT_BOUND || com.satcop.smartvisitor.kiosk.qa.QaHooks.otpEntryPoints

    fun staffVerifyVisible(bound: Boolean = reachable) = bound
    fun forgotPasswordVisible(bound: Boolean = reachable) = bound
    fun visitorCardVisible(s: OtpSettings, bound: Boolean = reachable) = bound && s.visitorOtpEnabled
    fun visitorSkipVisible(s: OtpSettings) = s.visitorOtpEnabled && s.visitorOtpAllowSkip
}
