package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * Process-scoped JWT session. NEVER persisted: a cold start always begins signed-out and
 * re-requires password + face (face gate policy 1059).
 *
 * [faceVerified] is true only after POST /auth/face-verify returned a face_verified token.
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

    private val _faceRequired = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    /** Emitted when the server answers 403 FACE_REQUIRED for the current token. */
    val faceRequiredEvents: SharedFlow<Unit> get() = _faceRequired

    val isSignedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    /** Signed in AND face-verified: the only state in which app data may be fetched/shown. */
    val dataAccessAllowed: Boolean
        get() = isSignedIn && faceVerified

    fun accept(token: String, user: MeResponse?, faceVerified: Boolean = false) {
        accessToken = token
        this.user = user
        this.faceVerified = faceVerified
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
        accessToken = null
        user = null
        faceVerified = false
    }
}
