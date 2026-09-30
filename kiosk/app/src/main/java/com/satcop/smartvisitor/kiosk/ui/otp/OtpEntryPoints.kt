package com.satcop.smartvisitor.kiosk.ui.otp

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.addvisitor.MaskedDisplay
import com.satcop.smartvisitor.kiosk.data.api.AppAuth
import com.satcop.smartvisitor.kiosk.data.otp.OtpAvailability
import com.satcop.smartvisitor.kiosk.data.otp.OtpPurpose
import com.satcop.smartvisitor.kiosk.data.otp.OtpRepository
import com.satcop.smartvisitor.kiosk.data.otp.OtpSettings
import com.satcop.smartvisitor.kiosk.data.api.LiveOtpRepository
import com.satcop.smartvisitor.kiosk.qa.QaHooks
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

private val liveOtp: OtpRepository by lazy { LiveOtpRepository() }

/** Which repository the OTP screens talk to: the bound API, or (QA-only build) the fake one. Release: never the fake. */
internal fun otpRepo(): OtpRepository = QaHooks.otpRepository() ?: liveOtp

private val fullScreen = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)

/**
 * S-OTP2: "Verify mobile number" row for Settings (Guard Settings tab, Gate/Host Settings tab).
 * Renders nothing until the OTP contract is bound (or in the QA-only build).
 */
@Composable
fun StaffVerifyEntry(strings: OtpStrings = OtpCopy.EN) {
    if (!OtpAvailability.staffVerifyVisible()) return
    val scope = rememberCoroutineScope()
    val user = AppAuth.session.user
    val masked = MaskedDisplay.mobile(user?.phone).ifBlank { "••••••" }
    var verified by remember { mutableStateOf(user?.mobileVerified == true) }
    var open by remember { mutableStateOf(false) }
    val controller = remember { OtpController(scope, otpRepo(), OtpPurpose.STAFF_VERIFY) }
    StaffVerifyRow(maskedMobile = masked, verified = verified, strings = strings) {
        open = true
        controller.loadSettings()
        controller.start()
    }
    if (open) {
        Dialog(onDismissRequest = { open = false }, properties = fullScreen) {
            Box(Modifier.fillMaxSize()) {
                OtpEnterCode(controller, strings, onBack = { open = false }, onDone = { verified = true; open = false })
            }
        }
    }
}

/** S-OTP3 entry: "Forgot password?" link on Login. Hidden until bound. */
@Composable
fun ForgotPasswordEntry(strings: OtpStrings = OtpCopy.EN, onSignedOutDone: () -> Unit = {}, linkText: String = "Forgot password?") {
    if (!OtpAvailability.forgotPasswordVisible()) return
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val controller = remember(open) { OtpController(scope, otpRepo(), OtpPurpose.PASSWORD_RESET) }
    Text(
        linkText, color = KioskColors.systemBlue, fontSize = 14.sp, fontFamily = KioskFont,
        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open = true }.padding(top = 8.dp),
    )
    if (open) {
        Dialog(onDismissRequest = { open = false }, properties = fullScreen) {
            Box(Modifier.fillMaxSize()) {
                ForgotPasswordFlow(
                    controller = controller, strings = strings,
                    onSavePassword = { token, pw ->
                        try { otpRepo().resetPassword(token, pw); null } catch (e: com.satcop.smartvisitor.kiosk.data.otp.OtpApiException) {
                            e.serverMessage ?: OtpCopy.EN.sendFailed
                        } catch (e: Exception) { OtpCopy.EN.sendFailed }
                    },
                    onExit = { open = false; onSignedOutDone() },
                )
            }
        }
    }
}

/**
 * S-OTP5 section inside Add Visitor. [onGate] reports whether the form may continue (verified, or skipped, or OTP off).
 * Composes nothing (and reports "may continue") unless the contract is bound / QA build AND visitorOtpEnabled.
 */
@Composable
fun VisitorOtpSection(mobileTenDigits: String, strings: OtpStrings = OtpCopy.EN, onChangeNumber: () -> Unit, onGate: (Boolean) -> Unit, onVerifyId: (String?) -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(OtpSettings()) }
    var loaded by remember { mutableStateOf(false) }
    var verified by remember(mobileTenDigits) { mutableStateOf(false) }
    var skipped by remember(mobileTenDigits) { mutableStateOf(false) }
    val controller = remember(mobileTenDigits) { OtpController(scope, otpRepo(), OtpPurpose.VISITOR_VERIFY) }
    LaunchedEffect(Unit) {
        if (OtpAvailability.reachable) {
            settings = runCatching { otpRepo().settings() }.getOrDefault(OtpSettings())
        }
        loaded = true
    }
    val shown = loaded && OtpAvailability.visitorCardVisible(settings)
    LaunchedEffect(shown, mobileTenDigits) { if (shown && mobileTenDigits.length == 10) controller.start(mobileTenDigits) }
    LaunchedEffect(shown, verified, skipped) { onGate(!shown || verified || skipped) }
    if (shown) {
        VisitorOtpCard(
            controller = controller, strings = strings, verified = verified,
            allowSkip = OtpAvailability.visitorSkipVisible(settings),
            onVerified = { verified = true; onVerifyId(controller.state.value.visitorVerifyId) }, onChangeNumber = { onVerifyId(null); onChangeNumber() }, onSkip = { skipped = true; onVerifyId(null) },
        )
    }
}
