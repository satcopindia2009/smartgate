package com.satcop.smartvisitor.kiosk.ui.otp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.otp.OtpPurpose
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
private fun OtpButton(text: String, enabled: Boolean = true, primary: Boolean = true, onClick: () -> Unit) {
    Text(
        text, textAlign = TextAlign.Center, fontFamily = KioskFont, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        color = if (primary) Color.White else KioskColors.systemBlue,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (primary) (if (enabled) KioskColors.systemBlue else KioskColors.border) else Color.Transparent)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 15.dp),
    )
}

/** One 6-box code field; a single hidden text field keeps numeric keypad + SMS autofill working. */
@Composable
fun OtpCodeBoxes(code: String, enabled: Boolean, error: Boolean, shake: Int, onCode: (String) -> Unit) {
    val shift = remember(shake) { mutableStateOf(0) }
    LaunchedEffect(shake) {
        if (shake > 0) { for (d in listOf(-10, 10, -6, 6, 0)) { shift.value = d; delay(40) } }
    }
    BasicTextField(
        value = code, onValueChange = onCode, enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        modifier = Modifier.fillMaxWidth().offset(x = shift.value.dp).semantics { contentDescription = "Enter the code" },
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 0 until 6) {
                    val ch = code.getOrNull(i)?.toString().orEmpty()
                    Box(
                        Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).background(KioskColors.card)
                            .border(BorderStroke(if (error) 2.dp else 1.dp, if (error) KioskColors.red else KioskColors.border), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text(ch, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, color = KioskColors.text) }
                }
            }
        },
    )
}

/** S-OTP1: Enter code. [inline] = the S-OTP5 card layout (no full-screen chrome). */
@Composable
fun OtpEnterCode(
    controller: OtpController,
    strings: OtpStrings,
    onBack: () -> Unit,
    onDone: (resetToken: String?) -> Unit,
    inline: Boolean = false,
    headline: ((String) -> String)? = null,
) {
    val st by controller.state.collectAsState()
    LaunchedEffect(Unit) { var n = 0; while (true) { delay(1000); controller.tick(); if (++n % 5 == 0) controller.pollStatus() } }
    LaunchedEffect(st.phase) { if (st.phase == OtpPhase.SUCCESS) { delay(1500); onDone(st.resetToken) } }
    val msg = st.message(strings)
    val isError = st.phase in setOf(OtpPhase.WRONG, OtpPhase.EXPIRED, OtpPhase.LOCKED, OtpPhase.RESEND_LIMIT, OtpPhase.SEND_FAILED)
    Column(
        modifier = (if (inline) Modifier.fillMaxWidth() else Modifier.fillMaxSize().background(KioskColors.bg).verticalScroll(rememberScrollState()))
            .padding(horizontal = FormTokens.ScreenHPad, vertical = FormTokens.FieldToField),
        verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
    ) {
        if (!inline) {
            Text("←", fontSize = 24.sp, color = KioskColors.systemBlue, modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onBack).semantics { contentDescription = "Back" })
            Text(strings.title, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, color = KioskColors.text)
        }
        Text(
            (headline ?: strings.sentTo)(st.maskedMobile.ifBlank { "••••••" }),
            fontSize = 15.sp, fontFamily = KioskFont, color = KioskColors.textMuted,
        )
        OtpCodeBoxes(st.code, st.boxesEnabled, st.phase == OtpPhase.WRONG, st.shake, controller::setCode)
        if (msg != null) {
            Text(
                msg, fontSize = 14.sp, fontFamily = KioskFont, fontWeight = FontWeight.Medium,
                color = if (st.phase == OtpPhase.SUCCESS) KioskColors.green else if (isError) KioskColors.red else KioskColors.text,
                modifier = Modifier.semantics { contentDescription = msg },
            )
        }
        if (st.phase == OtpPhase.SUCCESS) {
            OtpButton("✓", primary = true) { onDone(st.resetToken) }
        } else if (st.phase != OtpPhase.LOCKED) {
            OtpButton(if (st.phase == OtpPhase.VERIFYING) "…" else strings.verify, enabled = st.canVerify, onClick = controller::verify)
            if (st.resendVisible) {
                when {
                    st.noReceipt -> OtpButton(strings.sendAgain, primary = false) { controller.resend() }
                    st.resendInSec > 0 -> Text(strings.resendIn(st.resendInSec), fontSize = 14.sp, fontFamily = KioskFont, color = KioskColors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    else -> OtpButton(strings.resend, enabled = st.resendEnabled || st.phase == OtpPhase.EXPIRED, primary = false) { controller.resend() }
                }
            }
            if (st.phase == OtpPhase.SEND_FAILED) OtpButton(strings.tryAgain, primary = false) { controller.resend() }
            if (st.otherChannelVisible && st.otpId != null || st.phase == OtpPhase.SEND_FAILED) {
                OtpButton(strings.sendWhatsApp, primary = false) { controller.resend("whatsapp") }
                OtpButton(strings.sendSms, primary = false) { controller.resend("sms") }
            }
        }
    }
}

/** S-OTP2 row inside Profile/Settings. Hidden entirely until the OTP contract is bound. */
@Composable
fun StaffVerifyRow(maskedMobile: String, verified: Boolean, strings: OtpStrings, onVerifyNow: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(FormTokens.ScreenHPad), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Mobile number $maskedMobile", modifier = Modifier.weight(1f), fontFamily = KioskFont, fontSize = 15.sp, color = KioskColors.text)
            Text(
                if (verified) strings.verifiedChip else strings.notVerified, fontSize = 12.sp, fontFamily = KioskFont, fontWeight = FontWeight.SemiBold,
                color = if (verified) Color.White else KioskColors.textMuted,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(if (verified) KioskColors.green else KioskColors.secondaryFill).padding(horizontal = 12.dp, vertical = 5.dp),
            )
        }
        if (!verified) {
            OtpButton(strings.verifyNow, onClick = onVerifyNow)
            Text(strings.consentStaff, fontSize = 11.sp, fontFamily = KioskFont, color = KioskColors.textMuted)
        }
    }
}

/** S-OTP3 (mobile number) then S-OTP1, S-OTP4 (new password). Entry: Login "Forgot password?". */
enum class ForgotStep { MOBILE, CODE, NEW_PASSWORD, DONE }

@Composable
fun ForgotPasswordFlow(
    controller: OtpController,
    strings: OtpStrings,
    onSavePassword: suspend (resetToken: String, newPassword: String) -> String?,
    onExit: () -> Unit,
) {
    var step by remember { mutableStateOf(ForgotStep.MOBILE) }
    var mobile by remember { mutableStateOf("") }
    var sentNote by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf<String?>(null) }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var pwError by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    when (step) {
        ForgotStep.MOBILE -> Column(
            Modifier.fillMaxSize().background(KioskColors.bg).verticalScroll(rememberScrollState()).padding(FormTokens.ScreenHPad),
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            Text("←", fontSize = 24.sp, color = KioskColors.systemBlue, modifier = Modifier.heightIn(min = 48.dp).clickable(onClick = onExit))
            Text(strings.forgotTitle, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, color = KioskColors.text)
            KioskField(label = "Mobile number", value = mobile, onValueChange = { mobile = it.filter(Char::isDigit).take(10) }, keyboardType = KeyboardType.Phone, modifier = Modifier.fillMaxWidth())
            if (sentNote) Text(strings.forgotGeneric, fontSize = 14.sp, fontFamily = KioskFont, color = KioskColors.textMuted)
            OtpButton(strings.sendCode, enabled = mobile.length == 10) {
                controller.start(mobile)
                sentNote = true
                step = ForgotStep.CODE
            }
        }
        ForgotStep.CODE -> OtpEnterCode(controller, strings, onBack = { step = ForgotStep.MOBILE }, onDone = { t -> token = t; step = ForgotStep.NEW_PASSWORD })
        ForgotStep.NEW_PASSWORD -> Column(
            Modifier.fillMaxSize().background(KioskColors.bg).verticalScroll(rememberScrollState()).padding(FormTokens.ScreenHPad),
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            Text("New password", fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, color = KioskColors.text)
            KioskField(label = "New password", value = pw, onValueChange = { pw = it }, passwordToggle = true, modifier = Modifier.fillMaxWidth())
            KioskField(label = "Confirm password", value = pw2, onValueChange = { pw2 = it }, passwordToggle = true, error = pwError, modifier = Modifier.fillMaxWidth())
            Text("Use at least 6 characters.", fontSize = 12.sp, fontFamily = KioskFont, color = KioskColors.textMuted)
            OtpButton(if (saving) "…" else "Save password", enabled = !saving && pw.length >= 6 && pw == pw2) {
                val t = token ?: return@OtpButton
                saving = true
                scope.launch {
                    val err = onSavePassword(t, pw)
                    saving = false
                    if (err == null) step = ForgotStep.DONE else pwError = err
                }
            }
        }
        ForgotStep.DONE -> Column(
            Modifier.fillMaxSize().background(KioskColors.bg).padding(FormTokens.ScreenHPad),
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField, Alignment.CenterVertically),
        ) {
            Text(strings.passwordChanged, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, color = KioskColors.text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            OtpButton("Sign in", onClick = onExit)
        }
    }
}

/** S-OTP5: "Verify visitor's mobile" card inside Add Visitor. Only composed when OtpAvailability.visitorCardVisible. */
@Composable
fun VisitorOtpCard(
    controller: OtpController,
    strings: OtpStrings,
    verified: Boolean,
    allowSkip: Boolean,
    onVerified: () -> Unit,
    onChangeNumber: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KioskColors.card)
            .border(1.dp, KioskColors.border, RoundedCornerShape(14.dp)).padding(FormTokens.ScreenHPad),
        verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
    ) {
        Text(strings.visitorCardTitle, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, color = KioskColors.text)
        if (verified) {
            Text(strings.mobileVerifiedChip + " ✓", fontSize = 13.sp, fontFamily = KioskFont, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50)).background(KioskColors.green).padding(horizontal = 12.dp, vertical = 5.dp))
        } else {
            OtpEnterCode(controller, strings, onBack = {}, onDone = { onVerified() }, inline = true, headline = strings.visitorAsk)
            Text(strings.consentVisitor, fontSize = 11.sp, fontFamily = KioskFont, color = KioskColors.textMuted)
            Text(strings.changeNumber, fontSize = 15.sp, fontFamily = KioskFont, color = KioskColors.systemBlue, modifier = Modifier.heightIn(min = 44.dp).clickable { controller.voidCode(); onChangeNumber() })
            if (allowSkip) Text(strings.skip, fontSize = 15.sp, fontFamily = KioskFont, color = KioskColors.systemBlue, modifier = Modifier.heightIn(min = 44.dp).clickable(onClick = onSkip))
        }
    }
}
