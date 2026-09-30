package com.satcop.smartvisitor.kiosk.ui.otp

import com.satcop.smartvisitor.kiosk.data.otp.OtpApiException
import com.satcop.smartvisitor.kiosk.data.otp.OtpPurpose
import com.satcop.smartvisitor.kiosk.data.otp.OtpRepository
import com.satcop.smartvisitor.kiosk.data.otp.OtpSendRequest
import com.satcop.smartvisitor.kiosk.data.otp.OtpSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Every state of screen S-OTP1 (Product OTP scope, section D). */
enum class OtpPhase { IDLE, SENDING, VERIFYING, WRONG, EXPIRED, LOCKED, RESEND_LIMIT, SEND_FAILED, SUCCESS }

data class OtpUiState(
    val purpose: OtpPurpose = OtpPurpose.STAFF_VERIFY,
    val phase: OtpPhase = OtpPhase.IDLE,
    val otpId: String? = null,
    val maskedMobile: String = "",
    val code: String = "",
    val triesLeft: Int? = null,
    /** Seconds until Resend is allowed (0 = allowed now). */
    val resendInSec: Int = 0,
    val resendsLeft: Int = 3,
    val sinceSendSec: Int = 0,
    val lockedForSec: Int? = null,
    /** Server's own text of the last refusal (OTP_* error body); shown as returned. */
    val serverText: String? = null,
    /** GET /otp/status showSendAgain (server decision after ~60 s without a receipt). Never a failure. */
    val serverShowSendAgain: Boolean = false,
    /** visitor_verify: id to pass as otpId on POST /visits. */
    val visitorVerifyId: String? = null,
    /** Server said meta.mock:true (informational only: the app never shows or embeds the mock code). */
    val mock: Boolean = false,
    val demoTenant: Boolean = false,
    val settings: OtpSettings = OtpSettings(),
    val resetToken: String? = null,
    /** Bumped on every wrong code so the UI can shake the boxes. */
    val shake: Int = 0,
) {
    val canVerify: Boolean get() = code.length == 6 && phase != OtpPhase.SENDING && phase != OtpPhase.VERIFYING &&
        phase != OtpPhase.LOCKED && phase != OtpPhase.SUCCESS && otpId != null
    val boxesEnabled: Boolean get() = phase != OtpPhase.SENDING && phase != OtpPhase.VERIFYING &&
        phase != OtpPhase.LOCKED && phase != OtpPhase.SUCCESS
    /** "Send again" only, never a failure banner. Follows the server's showSendAgain (status endpoint). */
    val noReceipt: Boolean get() = otpId != null && serverShowSendAgain && phase == OtpPhase.IDLE
    val resendVisible: Boolean get() = phase != OtpPhase.LOCKED && phase != OtpPhase.RESEND_LIMIT && phase != OtpPhase.SUCCESS
    val resendEnabled: Boolean get() = resendVisible && resendInSec <= 0 && phase != OtpPhase.SENDING && phase != OtpPhase.VERIFYING
    val otherChannelVisible: Boolean get() = phase == OtpPhase.SEND_FAILED || settings.offersOtherChannel
}

/** Text shown for the current state, by language. Never contains the code or a raw number. */
fun OtpUiState.message(s: OtpStrings): String? = when (phase) {
    // The API's own text wins (it carries the real counts/minutes); the local copy is only the fallback.
    OtpPhase.WRONG -> serverText ?: s.wrong(triesLeft)
    OtpPhase.EXPIRED -> serverText ?: s.expired
    OtpPhase.LOCKED -> serverText ?: s.locked
    OtpPhase.RESEND_LIMIT -> serverText ?: s.resendLimit
    OtpPhase.SEND_FAILED -> serverText ?: s.sendFailed
    OtpPhase.SUCCESS -> s.verified
    else -> null
}

/**
 * Drives S-OTP1 for all three purposes. Compose-free, testable with a fake [OtpRepository].
 * The typed code lives only in [OtpUiState.code] (memory), is cleared after every verify attempt and is never logged.
 */
class OtpController(
    private val scope: CoroutineScope,
    private val repo: OtpRepository,
    private val purpose: OtpPurpose,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val newAttemptId: () -> String = { java.util.UUID.randomUUID().toString() },
    demoTenant: Boolean = false,
) {
    private val _state = MutableStateFlow(OtpUiState(purpose = purpose, demoTenant = demoTenant))
    val state: StateFlow<OtpUiState> = _state.asStateFlow()

    /** Mobile for password_reset / visitor_verify (staff_verify uses the token's user). */
    private var mobile: String? = null
    private val withAuth: Boolean get() = purpose != OtpPurpose.PASSWORD_RESET
    private var lastResent = false

    fun loadSettings() {
        scope.launch {
            val s = runCatching { withContext(io) { repo.settings() } }.getOrNull() ?: return@launch
            _state.update { it.copy(settings = s) }
        }
    }

    fun start(mobileTenDigits: String? = null, channel: String? = null) {
        mobile = mobileTenDigits ?: mobile
        send(channel)
    }

    fun setCode(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(6)
        _state.update { it.copy(code = digits, phase = if (it.phase == OtpPhase.WRONG) OtpPhase.IDLE else it.phase) }
    }

    fun resend(channel: String? = null) {
        val s = _state.value
        val allowed = s.resendEnabled || s.noReceipt || s.phase == OtpPhase.EXPIRED || s.phase == OtpPhase.SEND_FAILED
        if (!allowed) return
        send(channel, asResend = s.otpId != null && channel == null && s.phase != OtpPhase.EXPIRED && s.phase != OtpPhase.SEND_FAILED)
    }

    /** Wrong number in Add Visitor: the old code is void (server never accepts it once we start over). */
    fun voidCode() {
        _state.update { OtpUiState(purpose = purpose, demoTenant = it.demoTenant, settings = it.settings) }
    }

    private fun send(channel: String?, asResend: Boolean = false) {
        if (_state.value.phase == OtpPhase.SENDING) return
        val previousId = _state.value.otpId
        _state.update { it.copy(phase = OtpPhase.SENDING, code = "", serverText = null) }
        scope.launch {
            val r = runCatching {
                withContext(io) {
                    if (asResend && previousId != null) repo.resend(previousId, if (purpose == OtpPurpose.STAFF_VERIFY) null else mobile, withAuth) else repo.send(
                        OtpSendRequest(
                            purpose = purpose,
                            mobile = if (purpose == OtpPurpose.STAFF_VERIFY) null else mobile,
                            channel = channel,
                            attemptId = newAttemptId(),
                            consentAt = if (purpose == OtpPurpose.VISITOR_VERIFY) java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata")).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME) else null,
                        ),
                    )
                }
            }
            val sent = r.getOrNull()
            if (sent != null) {
                _state.update {
                    it.copy(
                        phase = OtpPhase.IDLE, otpId = sent.otpId, maskedMobile = sent.maskedMobile, code = "",
                        triesLeft = null, resendInSec = sent.resendAfterSec, resendsLeft = sent.resendsLeft,
                        sinceSendSec = 0, mock = sent.mock, serverText = null, serverShowSendAgain = false,
                        demoTenant = sent.demo,
                    )
                }
                return@launch
            }
            val e = r.exceptionOrNull() as? OtpApiException
            _state.update {
                it.copy(
                    phase = phaseForSendError(e), lockedForSec = e?.retryAfterSec, serverText = e?.serverMessage,
                    // Cooldown comes from the server (retryAfterSec); nothing is assumed when it is absent.
                    resendInSec = if (e?.code == "OTP_RESEND_WAIT") (e.retryAfterSec ?: it.resendInSec) else it.resendInSec,
                )
            }
        }
    }

    fun verify() {
        val s = _state.value
        if (!s.canVerify) return
        val id = s.otpId ?: return
        val code = s.code
        _state.update { it.copy(phase = OtpPhase.VERIFYING) }
        scope.launch {
            val r = runCatching { withContext(io) { repo.verify(id, code, withAuth) } }
            val ok = r.getOrNull()
            if (ok != null) {
                _state.update { it.copy(phase = OtpPhase.SUCCESS, code = "", resetToken = ok.resetToken, visitorVerifyId = ok.visitorVerifyId ?: if (purpose == OtpPurpose.VISITOR_VERIFY) id else null, serverText = null) }
                return@launch
            }
            val e = r.exceptionOrNull() as? OtpApiException
            _state.update {
                when (e?.code?.uppercase()) {
                    // OTP_USED carries the same words as OTP_INVALID (no tries count): show the server text as is.
                    "OTP_INVALID", "OTP_USED" -> it.copy(
                        phase = OtpPhase.WRONG, code = "", triesLeft = e.triesLeft.takeIf { _ -> e.code.equals("OTP_INVALID", true) },
                        shake = it.shake + 1, serverText = e.serverMessage,
                    )
                    "OTP_EXPIRED" -> it.copy(phase = OtpPhase.EXPIRED, code = "", resendInSec = 0, serverText = e.serverMessage)
                    "OTP_LOCKED" -> it.copy(phase = OtpPhase.LOCKED, code = "", lockedForSec = e.retryAfterSec, serverText = e.serverMessage)
                    else -> it.copy(phase = OtpPhase.SEND_FAILED, code = "", serverText = e?.serverMessage)
                }
            }
        }
    }

    /**
     * Asks the server whether to offer "Send again" (GET /otp/status). The screen calls this every few seconds while the
     * code screen is open and nothing was entered; the answer, not a local 60 s timer, drives the link. Errors are ignored
     * (a missing receipt or a failed poll is never shown as a failure).
     */
    fun pollStatus() {
        val s = _state.value
        val id = s.otpId ?: return
        if (s.phase != OtpPhase.IDLE || s.serverShowSendAgain) return
        scope.launch {
            val st = runCatching { withContext(io) { repo.status(id, withAuth) } }.getOrNull() ?: return@launch
            _state.update { if (it.otpId == id) it.copy(serverShowSendAgain = st.showSendAgain) else it }
        }
    }

    /** Called once per second by the screen while it is visible. */
    fun tick() {
        _state.update {
            if (it.otpId == null) it
            else it.copy(
                resendInSec = (it.resendInSec - 1).coerceAtLeast(0), sinceSendSec = it.sinceSendSec + 1,
                lockedForSec = it.lockedForSec?.let { l -> (l - 1).coerceAtLeast(0) },
            )
        }
    }

    companion object {
        fun phaseForSendError(e: OtpApiException?): OtpPhase = when (e?.code?.uppercase()) {
            "OTP_RESEND_LIMIT", "OTP_DAILY_LIMIT" -> OtpPhase.RESEND_LIMIT
            "OTP_LOCKED" -> OtpPhase.LOCKED
            "OTP_RESEND_WAIT" -> OtpPhase.IDLE
            else -> OtpPhase.SEND_FAILED // OTP_SEND_FAILED, BLACKLISTED, network, unknown: plain text, never raw
        }
    }
}
