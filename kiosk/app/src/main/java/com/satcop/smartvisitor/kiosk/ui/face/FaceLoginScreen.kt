package com.satcop.smartvisitor.kiosk.ui.face

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.StaffFaceConsent
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.ControlShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

enum class FaceLoginPhase {
    HUB,
    CONSENT_ENROLL,
    CAPTURE_ENROLL,
    CAPTURE_VERIFY,
}

@Composable
fun FaceLoginScreen(
    phase: FaceLoginPhase,
    username: String,
    enrolled: Boolean,
    consentAgreed: Boolean,
    consentAt: String?,
    busy: Boolean,
    message: String?,
    messageIsError: Boolean = false,
    onUsername: (String) -> Unit,
    onAgreeConsent: () -> Unit,
    onDeclineConsent: () -> Unit,
    onStartEnroll: () -> Unit,
    onStartVerify: () -> Unit,
    onCaptured: (ByteArray) -> Unit,
    onCancelCapture: () -> Unit,
    onUsePassword: () -> Unit,
) {
    val capturePhase =
        phase == FaceLoginPhase.CAPTURE_ENROLL || phase == FaceLoginPhase.CAPTURE_VERIFY
    // Crashfix 1046: scroll HUB/CONSENT only — never scroll CameraX preview.
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (capturePhase) Modifier
                else Modifier.verticalScroll(rememberScrollState()),
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!capturePhase) {
            Text(
                "Face login",
                color = KioskColors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
            Text(
                "Unlock with your face, or use your password.",
                color = KioskColors.textMuted,
                fontSize = 13.sp,
                fontFamily = KioskFont,
            )
        }

        when (phase) {
            FaceLoginPhase.CONSENT_ENROLL -> {
                Text(
                    "Please read and agree before your face is enrolled.",
                    color = KioskColors.warning,
                    fontSize = 13.sp,
                    fontFamily = KioskFont,
                )
                StaffFaceConsentPanel(onAgree = onAgreeConsent, onDecline = onDeclineConsent)
            }
            FaceLoginPhase.CAPTURE_ENROLL -> FaceCaptureScreen(
                mode = FaceCaptureMode.ENROLL,
                busy = busy,
                message = message,
                messageIsError = messageIsError,
                onCaptured = onCaptured,
                onCancel = onCancelCapture,
            )
            FaceLoginPhase.CAPTURE_VERIFY -> FaceCaptureScreen(
                mode = FaceCaptureMode.VERIFY,
                busy = busy,
                message = message,
                messageIsError = messageIsError,
                onCaptured = onCaptured,
                onCancel = onCancelCapture,
            )
            FaceLoginPhase.HUB -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                        .background(KioskColors.brandSoft)
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        if (enrolled) "Face enrolled"
                        else "Face not enrolled. Sign in with your password to enroll.",
                        color = if (enrolled) KioskColors.success else KioskColors.warning,
                        fontFamily = KioskFont,
                        fontWeight = FontWeight.Medium,
                    )
                    if (consentAgreed && !consentAt.isNullOrBlank()) {
                        Text(
                            "Consent recorded · ${StaffFaceConsent.VERSION}",
                            color = KioskColors.textMuted,
                            fontSize = 12.sp,
                            fontFamily = KioskFont,
                        )
                    } else {
                        Text(
                            "Consent is needed before you can enroll your face.",
                            color = KioskColors.textMuted,
                            fontSize = 12.sp,
                            fontFamily = KioskFont,
                        )
                    }
                }
                KioskField(
                    label = "User ID",
                    value = username,
                    onValueChange = onUsername,
                    placeholder = "Enter your User ID",
                    keyboardType = KeyboardType.Ascii,
                    capitalization = KeyboardCapitalization.None,
                    autoCorrect = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                SgPrimaryButton(
                    text = if (enrolled) "Re-enroll face" else "Enroll face",
                    onClick = onStartEnroll,
                    enabled = !busy && username.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                )
                SgSecondaryButton(
                    text = "Unlock with face",
                    onClick = onStartVerify,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
                SgSecondaryButton(
                    text = "Use password instead",
                    onClick = onUsePassword,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!message.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(message, color = if (messageIsError) KioskColors.errorText else KioskColors.primary, fontSize = 13.sp, fontFamily = KioskFont)
                }
            }
        }
    }
}
