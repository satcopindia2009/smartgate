package com.satcop.smartvisitor.kiosk.ui.steps

import com.satcop.smartvisitor.kiosk.qa.QaHooks
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.IdType
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.LocalKioskCompact
import com.satcop.smartvisitor.kiosk.ui.components.FormHeader
import com.satcop.smartvisitor.kiosk.ui.components.FormLabel
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.PanelDivider
import com.satcop.smartvisitor.kiosk.ui.components.SignaturePad
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
import com.satcop.smartvisitor.kiosk.ui.components.strokesToBitmap
import com.satcop.smartvisitor.kiosk.ui.media.PlaceholderBitmap
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusMd

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoIdStep(
    draft: RegistrationDraft,
    livePhoto: Bitmap?,
    idImage: Bitmap?,
    errors: Map<String, String>,
    submitting: Boolean,
    blocked: Boolean,
    blacklistHit: BlacklistEntry?,
    onIdType: (String) -> Unit,
    onIdNumber: (String) -> Unit,
    onLivePhoto: (Bitmap?) -> Unit,
    onIdImage: (Bitmap?) -> Unit,
    onSignature: (Bitmap?) -> Unit,
    onClearSignature: () -> Unit,
    onBlockSample: () -> Unit = {},
    onAlertSample: () -> Unit = {},
    onAgreeConsent: () -> Unit,
    onDeclineConsent: () -> Unit,
    onUseSavedId: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val compact = LocalKioskCompact.current
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    // Pending capture target: live visitor photo vs govt ID document (camera only — no gallery).
    var pendingCapture by remember { mutableStateOf("live") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        when (pendingCapture) {
            "id" -> onIdImage(bmp)
            else -> onLivePhoto(bmp)
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null)
        else if (pendingCapture == "id") onIdImage(null)
        else onLivePhoto(null)
    }
    fun captureWithCamera(target: String) {
        pendingCapture = target
        QaHooks.frame(if (target == "id") "ID photo" else "Visitor photo")?.let { f ->
            if (target == "id") onIdImage(f) else onLivePhoto(f)
            return
        }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) camera.launch(null) else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    if (!draft.consentAgreed) {
        Column(
            modifier = Modifier.fillMaxWidth(),
        ) {
            FormHeader(
                title = "Visitor notice (required)",
                subtitle = "Please read and agree before we take a live photo or capture ID.",
            )
            GateConsentPanel(onAgree = onAgreeConsent, onDecline = onDeclineConsent)
            KioskGhostButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FormTokens.FieldToField)
                    .heightIn(min = FormTokens.MinTouch),
            )
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            FormHeader(
                title = "Photo, ID & signature",
                subtitle = "Take the visitor photo and the ID photo, then enter the ID number.",
                modifier = Modifier.padding(bottom = 0.dp),
            )
        }

        if (blocked && blacklistHit != null) {
            BlockBanner(hit = blacklistHit)
        } else if (blacklistHit?.severity == "Alert") {
            AlertBanner(hit = blacklistHit)
        }

        val livePreview: @Composable () -> Unit = {
            if (livePhoto != null) {
                Image(
                    bitmap = livePhoto.asImageBitmap(),
                    contentDescription = "Live photo",
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                InitialsBubble(PlaceholderBitmap.initials(draft.visitorName))
            }
        }
        val idPreview: @Composable () -> Unit = {
            if (idImage != null) {
                Image(
                    bitmap = idImage.asImageBitmap(),
                    contentDescription = "ID image",
                    modifier = Modifier
                        .height(72.dp)
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text("🪪", fontSize = 28.sp)
            }
        }
        // Gallery SoT phone: side-by-side Visitor photo | ID card (AC-PH portrait).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = FormTokens.HeaderToForm),
            horizontalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) {
            CaptureBox(
                label = "Visitor photo",
                filled = livePhoto != null || draft.livePhotoCaptured,
                title = if (draft.livePhotoCaptured) "Captured" else "Tap to capture",
                subtitle = "Live camera",
                error = errors[FieldKeys.LIVE_PHOTO],
                modifier = Modifier.weight(1f),
                onClick = { captureWithCamera("live") },
                preview = livePreview,
            )
            CaptureBox(
                label = "ID card",
                filled = idImage != null || draft.idImageCaptured,
                title = if (draft.idImageCaptured) "ID captured" else "Tap to capture ID",
                subtitle = "Aadhaar / DL",
                error = errors[FieldKeys.ID_IMAGE],
                modifier = Modifier.weight(1f),
                onClick = { captureWithCamera("id") },
                preview = idPreview,
            )
        }
        if (errors[FieldKeys.LIVE_PHOTO] != null) {
            Text(
                text = errors[FieldKeys.LIVE_PHOTO].orEmpty(),
                color = KioskColors.red,
                fontSize = 12.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = FormTokens.ErrorGap),
            )
        }

        val savedRef = draft.savedId
        if (savedRef != null && draft.useSavedId) {
            // Server-provided reference only ("Aadhaar ••••1234"); never a full number, never sent back.
            FormLabel("ID number", modifier = Modifier.padding(top = FormTokens.FieldToField))
            Text(
                text = savedRef.display,
                color = KioskColors.inputText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = FormTokens.MinTouch)
                    .clip(RoundedCornerShape(RadiusMd))
                    .background(KioskColors.inputBg)
                    .border(1.dp, KioskColors.inputBorder, RoundedCornerShape(RadiusMd))
                    .padding(horizontal = FormTokens.ControlHPad, vertical = 12.dp),
            )
            Text(
                text = "Required for every entry.",
                color = KioskColors.textMuted,
                fontSize = 12.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = FormTokens.ErrorGap),
            )
            KioskGhostButton(
                text = "Enter a new ID number",
                onClick = { onUseSavedId(false) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FormTokens.ButtonGap)
                    .heightIn(min = FormTokens.MinTouch),
            )
        } else {
            FormLabel("ID type", modifier = Modifier.padding(top = FormTokens.FieldToField))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
                verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
            ) {
                IdType.entries.forEach { type ->
                    val selected = draft.idType == type.apiValue
                    Box(
                        modifier = Modifier
                            .heightIn(min = FormTokens.MinTouch)
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) KioskColors.primary else KioskColors.secondaryFill)
                            .clickable { onIdType(type.apiValue) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = type.apiValue,
                            color = if (selected) KioskColors.onPrimary else KioskColors.text,
                            fontSize = 13.sp,
                            fontFamily = KioskFont,
                        )
                    }
                }
            }

            KioskField(
                label = "ID number",
                value = draft.idNumber,
                onValueChange = onIdNumber,
                placeholder = "Enter ID number to continue",
                error = errors[FieldKeys.ID],
                capitalization = KeyboardCapitalization.Characters,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FormTokens.FieldToField),
            )

            if (savedRef != null) {
                KioskGhostButton(
                    text = "Use saved ID (${savedRef.display})",
                    onClick = { onUseSavedId(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = FormTokens.ButtonGap)
                        .heightIn(min = FormTokens.MinTouch),
                )
            }
        }
        errors[FieldKeys.ID_IMAGE]?.let {
            Text(
                text = it,
                color = KioskColors.danger,
                fontSize = 12.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = FormTokens.ErrorGap),
            )
        }

        FormLabel("Visitor signature", modifier = Modifier.padding(top = FormTokens.FieldToField))
        SignaturePad(
            strokes = strokes,
            onStrokes = { next ->
                strokes = next
                onSignature(strokesToBitmap(next, 800, 220))
            },
        )
        SgSecondaryButton(
            text = "Clear signature",
            onClick = {
                strokes = emptyList()
                onClearSignature()
            },
            modifier = Modifier.padding(top = FormTokens.ButtonGap).fillMaxWidth(),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = FormTokens.SectionGap),
            verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
        ) {
            SgPrimaryButton(
                text = if (submitting) "Submitting…" else "Submit",
                enabled = !submitting && !blocked && (draft.idNumber.trim().isNotEmpty() || (draft.useSavedId && draft.savedId != null)),
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
            SgSecondaryButton(text = "Back", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CaptureBox(
    label: String,
    filled: Boolean,
    title: String,
    subtitle: String,
    error: String?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    preview: @Composable () -> Unit,
) {
    val border = when {
        error != null -> KioskColors.danger
        filled -> KioskColors.primary
        else -> KioskColors.border
    }
    Column(modifier) {
        FormLabel(label)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(if (filled) KioskColors.brandSoft else KioskColors.inputBg)
                .border(1.5.dp, border, RoundedCornerShape(RadiusLg))
                .clickable(onClick = onClick)
                .padding(FormTokens.HeaderToForm),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            preview()
            Spacer(Modifier.height(10.dp))
            Text(title, color = if (filled) KioskColors.primary else KioskColors.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, fontFamily = KioskFont, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text(subtitle, color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        if (error != null) {
            Text(error, color = KioskColors.danger, fontSize = 12.sp, fontFamily = KioskFont, modifier = Modifier.padding(top = FormTokens.ErrorGap))
        }
    }
}

@Composable
private fun InitialsBubble(initials: String) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(KioskColors.brandSoft),
        contentAlignment = Alignment.Center,
    ) {
        Text("📷", fontSize = 26.sp)
    }
}

@Composable
private fun BlockBanner(hit: BlacklistEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FormTokens.FieldToField)
            .clip(RoundedCornerShape(RadiusMd))
            .background(KioskColors.dangerSoft)
            .border(1.dp, KioskColors.danger, RoundedCornerShape(RadiusMd))
            .padding(14.dp),
    ) {
        Text("Entry not allowed", color = KioskColors.danger, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Text(
            "${hit.name ?: "Visitor"} · ${hit.reason ?: "Do not issue pass"}",
            color = KioskColors.text,
            fontSize = 14.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text("Do not let this visitor in. Call the security head.", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
    }
}

@Composable
private fun AlertBanner(hit: BlacklistEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FormTokens.FieldToField)
            .clip(RoundedCornerShape(RadiusMd))
            .background(KioskColors.warningSoft)
            .padding(14.dp),
    ) {
        Text("Watch-list alert", color = KioskColors.warning, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Text(
            "${hit.name ?: "Visitor"} · ${hit.reason ?: "Watch only"}",
            color = KioskColors.warning,
            fontSize = 13.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
