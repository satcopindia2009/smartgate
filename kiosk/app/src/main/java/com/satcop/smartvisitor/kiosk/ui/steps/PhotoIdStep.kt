package com.satcop.smartvisitor.kiosk.ui.steps

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
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.IdType
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.LocalKioskCompact
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.PanelDivider
import com.satcop.smartvisitor.kiosk.ui.components.SignaturePad
import com.satcop.smartvisitor.kiosk.ui.components.strokesToBitmap
import com.satcop.smartvisitor.kiosk.ui.media.PlaceholderBitmap
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
    onBlockSample: () -> Unit,
    onAlertSample: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    val context = LocalContext.current
    val compact = LocalKioskCompact.current
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        onLivePhoto(bmp)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null) else onLivePhoto(null)
    }
    fun captureLive() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) camera.launch(null) else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            onIdImage(null)
            return@rememberLauncherForActivityResult
        }
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull()
        onIdImage(decoded)
    }

    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = "Photo, ID & signature",
                color = KioskColors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
            Text(
                text = "Live photo required · ID number or ID image (V1)",
                color = KioskColors.textMuted,
                fontSize = 14.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (compact) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KioskGhostButton(
                        text = "Block sample",
                        onClick = onBlockSample,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    KioskGhostButton(
                        text = "Alert sample",
                        onClick = onAlertSample,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KioskGhostButton(text = "Block sample", onClick = onBlockSample)
                    KioskGhostButton(text = "Alert sample", onClick = onAlertSample)
                }
            }
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
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CaptureBox(
                    label = "Live photo",
                    filled = livePhoto != null || draft.livePhotoCaptured,
                    title = if (draft.livePhotoCaptured) "Captured" else "Tap to capture",
                    subtitle = "Camera · demo placeholder if no camera",
                    error = errors[FieldKeys.LIVE_PHOTO],
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { captureLive() },
                    preview = livePreview,
                )
                CaptureBox(
                    label = "Govt ID upload",
                    filled = idImage != null || draft.idImageCaptured,
                    title = if (draft.idImageCaptured) "ID attached" else "Tap to upload ID",
                    subtitle = "Aadhaar / DL / Voter · optional if number entered",
                    error = null,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { gallery.launch("image/*") },
                    preview = idPreview,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                CaptureBox(
                    label = "Live photo",
                    filled = livePhoto != null || draft.livePhotoCaptured,
                    title = if (draft.livePhotoCaptured) "Captured" else "Tap to capture",
                    subtitle = "Camera · demo placeholder if no camera",
                    error = errors[FieldKeys.LIVE_PHOTO],
                    modifier = Modifier.weight(1f),
                    onClick = { captureLive() },
                    preview = livePreview,
                )
                CaptureBox(
                    label = "Govt ID upload",
                    filled = idImage != null || draft.idImageCaptured,
                    title = if (draft.idImageCaptured) "ID attached" else "Tap to upload ID",
                    subtitle = "Aadhaar / DL / Voter · optional if number entered",
                    error = null,
                    modifier = Modifier.weight(1f),
                    onClick = { gallery.launch("image/*") },
                    preview = idPreview,
                )
            }
        }
        if (errors[FieldKeys.LIVE_PHOTO] != null) {
            Text(
                text = errors[FieldKeys.LIVE_PHOTO].orEmpty(),
                color = KioskColors.red,
                fontSize = 12.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        Text(
            text = "ID type",
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IdType.entries.forEach { type ->
                val selected = draft.idType == type.apiValue
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (selected) KioskColors.purpleDim else KioskColors.card)
                        .border(
                            1.dp,
                            if (selected) KioskColors.purple else KioskColors.border,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { onIdType(type.apiValue) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = type.apiValue,
                        color = if (selected) Color.White else KioskColors.textMuted,
                        fontSize = 13.sp,
                        fontFamily = KioskFont,
                    )
                }
            }
        }

        KioskField(
            label = "Govt ID number (V1 — required unless ID image)",
            value = draft.idNumber,
            onValueChange = onIdNumber,
            placeholder = "Number or leave blank if uploading ID photo",
            error = errors[FieldKeys.ID],
            capitalization = KeyboardCapitalization.Characters,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        Text(
            text = "Digital signature (optional)",
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        SignaturePad(
            strokes = strokes,
            onStrokes = { next ->
                strokes = next
                onSignature(strokesToBitmap(next, 800, 220))
            },
        )
        KioskGhostButton(
            text = "Clear signature",
            onClick = {
                strokes = emptyList()
                onClearSignature()
            },
            modifier = Modifier.padding(top = 8.dp),
        )

        PanelDivider()
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                KioskGhostButton(
                    text = "Back",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
                CyanSubmitButton(
                    text = if (submitting) "Submitting…" else "Submit & notify host",
                    enabled = !submitting && !blocked,
                    onClick = onSubmit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                KioskGhostButton(text = "Back", onClick = onBack)
                CyanSubmitButton(
                    text = if (submitting) "Submitting…" else "Submit & notify host",
                    enabled = !submitting && !blocked,
                    onClick = onSubmit,
                )
            }
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
        error != null -> KioskColors.red
        filled -> KioskColors.cyan
        else -> KioskColors.border
    }
    Column(modifier) {
        Text(
            text = label,
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .clip(RoundedCornerShape(RadiusLg))
                .background(if (filled) KioskColors.cyanDim else KioskColors.bg)
                .border(2.dp, border, RoundedCornerShape(RadiusLg))
                .clickable(onClick = onClick)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            preview()
            Spacer(Modifier.height(10.dp))
            Text(title, color = if (filled) KioskColors.cyanBright else KioskColors.text, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
            Text(subtitle, color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
        }
    }
}

@Composable
private fun InitialsBubble(initials: String) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF6366F1)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
    }
}

@Composable
private fun BlockBanner(hit: BlacklistEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(Color(0x26EF4444))
            .border(1.dp, KioskColors.red, RoundedCornerShape(RadiusMd))
            .padding(14.dp),
    ) {
        Text("BLACKLIST BLOCK", color = Color(0xFFF87171), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Text(
            "${hit.name ?: "Visitor"} · ${hit.reason ?: "Do not issue pass"}",
            color = KioskColors.text,
            fontSize = 14.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text("Pass not issued. Escalate to Security Head.", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
    }
}

@Composable
private fun AlertBanner(hit: BlacklistEntry) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(KioskColors.orangeDim)
            .padding(14.dp),
    ) {
        Text("BLACKLIST ALERT", color = KioskColors.peakAmberBright, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Text(
            "${hit.name ?: "Visitor"} · ${hit.reason ?: "Watch only"}",
            color = KioskColors.peakAmber,
            fontSize = 13.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun CyanSubmitButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF06B6D4), Color(0xFF0891B2)))
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) brush else androidx.compose.ui.graphics.Brush.linearGradient(listOf(KioskColors.border, KioskColors.border)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFamily = KioskFont)
    }
}
