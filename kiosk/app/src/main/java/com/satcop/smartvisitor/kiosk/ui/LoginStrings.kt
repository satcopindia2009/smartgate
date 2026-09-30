package com.satcop.smartvisitor.kiosk.ui

/**
 * Login-screen-only string table (board 03 EN | हिं toggle). There is no app-wide language mechanism;
 * only the Login screen is switchable. Hindi copy = Product-approved board 03 text (restyle-content-rulings #3);
 * the three marked UNREVIEWED are extra words the board does not show and need a Product/Compliance look.
 */
data class LoginStrings(
    val title: String,
    val subtitle: String,
    val userIdLabel: String,
    val userIdHint: String,
    val passwordLabel: String,
    val passwordHint: String,
    val forgot: String,
    val login: String,
    val signingIn: String,   // UNREVIEWED
    val faceLogin: String,   // UNREVIEWED
) {
    companion object {
        val EN = LoginStrings(
            title = "Login",
            subtitle = "Sign in to your account",
            userIdLabel = "User ID",
            userIdHint = "Enter your User ID",
            passwordLabel = "Password",
            passwordHint = "Enter your password",
            forgot = "Forgot password?",
            login = "Login",
            signingIn = "Signing in…",
            faceLogin = "Face login",
        )
        val HI = LoginStrings(
            title = "लॉगिन",
            subtitle = "अपने खाते में साइन इन करें",
            userIdLabel = "यूज़र आईडी",
            userIdHint = "अपनी यूज़र आईडी डालें",
            passwordLabel = "पासवर्ड",
            passwordHint = "पासवर्ड डालें",
            forgot = "पासवर्ड भूल गए?",
            login = "लॉगिन",
            signingIn = "साइन इन हो रहा है…",
            faceLogin = "फ़ेस लॉगिन",
        )
        fun of(hindi: Boolean) = if (hindi) HI else EN
    }
}
