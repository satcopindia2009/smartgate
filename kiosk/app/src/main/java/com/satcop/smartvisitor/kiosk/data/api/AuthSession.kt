package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-scoped JWT session. NEVER persisted: a cold start always begins signed-out and
 * re-requires password + face (face gate policy 1059).
 *
 * 1059b: [faceVerified] is true after POST /auth/face-verify, OR straight from login when the server says the role
 * needs no face (faceVerified:true for host/admin, faceRequired:false for gate). Previously EVERY role was forced
 * through the face step, which dead-ended gate/host/admin.
 * Original: [faceVerified] is true only after POST /auth/face-verify returned a face_verified token.
 * Data endpoints refuse to run (client side) until then; the server enforces the same (403 FACE_REQUIRED).
 */
class AuthSession {
    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var user: MeResponse? = null
        private set

    @Volatile
    var faceVerified: Boolean = false
        private set

    /** Server `faceRequired` from login (true only for enforced roles, i.e. guard). */
    @Volatile
    var faceRequired: Boolean = false
        private set

    /** Epoch ms of the server cut-off (sessionExpiresAt / expiresIn); null = unknown. */
    @Volatile
    var expiresAtMs: Long? = null
        private set

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emitted once when the token is over (client clock passed the cut-off, or the server said 401 TOKEN_EXPIRED). */
    val sessionExpiredEvents: SharedFlow<Unit> get() = _sessionExpired

    private val _faceRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emitted when the server answers 403 FACE_REQUIRED for the current token. */
    val faceRequiredEvents: SharedFlow<Unit> get() = _faceRequired

    private val _duty = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** 1077: duty error codes from the server (NO_GATE_DUTY, NO_PATROL_DUTY, NOT_CLOCKED_IN, GATE_NOT_ON_DUTY). */
    val dutyEvents: SharedFlow<String> get() = _duty

    fun noteDutyError(code: String) { _duty.tryEmit(code) }

    val isSignedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    /** Signed in AND face-verified: the only state in which app data may be fetched/shown. */
    val dataAccessAllowed: Boolean
        get() = isSignedIn && faceVerified

    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        isSignedIn && SessionExpiry.isExpired(expiresAtMs, nowMs)

    /** Returns true if it fired (the session was signed in). */
    fun markExpired(): Boolean {
        if (!isSignedIn) return false
        _sessionExpired.tryEmit(Unit)
        return true
    }

    fun accept(
        token: String,
        user: MeResponse?,
        faceVerified: Boolean = false,
        faceRequired: Boolean = true,
        expiresAtMs: Long? = this.expiresAtMs,
    ) {
        accessToken = token
        this.expiresAtMs = expiresAtMs
        this.user = user
        this.faceVerified = faceVerified
        this.faceRequired = faceRequired
    }

    fun updateUser(user: MeResponse) {
        this.user = user
    }

    /** Server said the token is not face verified -> drop verified flag (token kept only to re-upgrade). */
    fun markFaceRequired() {
        faceVerified = false
        _faceRequired.tryEmit(Unit)
    }

    fun clear() {
        expiresAtMs = null
        faceRequired = false
        accessToken = null
        user = null
        faceVerified = false
    }
}
