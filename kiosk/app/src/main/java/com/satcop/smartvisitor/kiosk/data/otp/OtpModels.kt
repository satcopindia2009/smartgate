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
)

data class OtpVerified(val verifiedAt: String?, val resetToken: String?)

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
) : RuntimeException(code)

/**
 * Data seam for every OTP screen. The final endpoint names are published by Backend; until they land the app binds
 * [UnboundOtpRepository] and the entry points stay hidden ([OtpAvailability]).
 */
interface OtpRepository {
    suspend fun settings(): OtpSettings
    suspend fun send(req: OtpSendRequest): OtpSent
    suspend fun verify(otpId: String, code: String): OtpVerified
    suspend fun resetPassword(resetToken: String, newPassword: String)
}

/** Placeholder until Backend's OTP contract is bound: every call fails as "unavailable" (never a fake success). */
object UnboundOtpRepository : OtpRepository {
    private fun unavailable(): Nothing = throw OtpApiException("OTP_SEND_FAILED")
    override suspend fun settings(): OtpSettings = OtpSettings()
    override suspend fun send(req: OtpSendRequest): OtpSent = unavailable()
    override suspend fun verify(otpId: String, code: String): OtpVerified = unavailable()
    override suspend fun resetPassword(resetToken: String, newPassword: String) = unavailable()
}

/** Entry points (Verify mobile, Forgot password, Add Visitor OTP card) show only when the contract is bound. */
object OtpAvailability {
    /** Flip to true in the commit that binds the published endpoints. */
    const val CONTRACT_BOUND: Boolean = false

    fun staffVerifyVisible(bound: Boolean = CONTRACT_BOUND) = bound
    fun forgotPasswordVisible(bound: Boolean = CONTRACT_BOUND) = bound
    fun visitorCardVisible(s: OtpSettings, bound: Boolean = CONTRACT_BOUND) = bound && s.visitorOtpEnabled
    fun visitorSkipVisible(s: OtpSettings) = s.visitorOtpEnabled && s.visitorOtpAllowSkip
}
