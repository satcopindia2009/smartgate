package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.MeResponse

/**
 * Process-scoped JWT session (DataStore can wrap this later).
 * Logout must clear the token so the next screen is Login.
 */
class AuthSession {
    @Volatile
    var accessToken: String? = null
        private set

    @Volatile
    var user: MeResponse? = null
        private set

    val isSignedIn: Boolean
        get() = !accessToken.isNullOrBlank()

    fun accept(token: String, user: MeResponse?) {
        accessToken = token
        this.user = user
    }

    fun updateUser(user: MeResponse) {
        this.user = user
    }

    fun clear() {
        accessToken = null
        user = null
    }
}
