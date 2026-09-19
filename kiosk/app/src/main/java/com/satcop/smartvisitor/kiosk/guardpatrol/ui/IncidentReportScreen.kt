package com.satcop.smartvisitor.kiosk.guardpatrol.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.guardpatrol.data.IncidentType
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import java.io.ByteArrayOutputStream

/**
 * Wave 2 Patrol Incident report (AC-PI1/PI2/PI5).
 * Live camera photo required; retention hint under photo (AC-PI4 EN+HI).
 * Additive — Cancel returns to Active without touching Scan/End.
 */
@Composable
fun IncidentReportScreen(
    state: GuardPatrolUiState,
    onType: (IncidentType) -> Unit,
    onNotes: (String) -> Unit,
    onCheckpoint: (String?) -> Unit,
    onPhoto: (ByteArray) -> Unit,
    onClearPhoto: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
) {
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        onPhoto(stream.toByteArray())
    }

    val preview = remember(state.incidentPhotoJpeg) {
        state.incidentPhotoJpeg?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }

    val cps = state.round?.let { r ->
        val order = state.templates.find { it.id == r.templateId }?.checkpointIds.orEmpty()
        order.mapNotNull { id -> state.checkpoints[id]?.let { id to it.name } }
    }.orEmpty()

    val canSubmit = state.incidentPhotoJpeg != null && state.incidentNotes.isNotBlank() && !state.incidentBusy
    val shape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            "Report incident",
            color = KioskColors.text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = KioskFont,
        )
        Text(
            "Live photo required · does not pause Scan / End",
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
        )

        Text("Type", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            IncidentType.entries.forEach { t ->
                val selected = state.incidentType == t
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) KioskColors.cyanDim else KioskColors.card)
                        .border(
                            1.dp,
                            if (selected) KioskColors.cyan else KioskColors.border,
                            RoundedCornerShape(10.dp),
                        )
                        .clickable { onType(t) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        t.display(),
                        color = if (selected) KioskColors.cyanBright else KioskColors.text,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontFamily = KioskFont,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Notes", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = state.incidentNotes,
            onValueChange = onNotes,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp),
            placeholder = {
                Text("What happened?", color = KioskColors.textMuted, fontFamily = KioskFont)
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = KioskColors.text,
                unfocusedTextColor = KioskColors.text,
                focusedBorderColor = KioskColors.cyan,
                unfocusedBorderColor = KioskColors.border,
                cursorColor = KioskColors.cyan,
                focusedContainerColor = KioskColors.card,
                unfocusedContainerColor = KioskColors.card,
            ),
            maxLines = 4,
        )

        if (cps.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(
                "Checkpoint (optional)",
                color = KioskColors.textMuted,
                fontSize = 12.sp,
                fontFamily = KioskFont,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (state.incidentCheckpointId == null) KioskColors.purpleDim else KioskColors.card,
                        )
                        .clickable { onCheckpoint(null) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                ) {
                    Text("None", color = KioskColors.text, fontSize = 11.sp, fontFamily = KioskFont)
                }
                cps.take(4).forEach { (id, name) ->
                    val sel = state.incidentCheckpointId == id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sel) KioskColors.purpleDim else KioskColors.card)
                            .clickable { onCheckpoint(id) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(name, color = KioskColors.text, fontSize = 11.sp, fontFamily = KioskFont)
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Live photo *", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(shape)
                .background(KioskColors.card)
                .border(1.dp, KioskColors.border, shape)
                .clickable(enabled = !state.incidentBusy) { takePicture.launch(null) },
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = "Incident photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                TextButton(
                    onClick = onClearPhoto,
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Icon(Icons.Default.Close, null, tint = KioskColors.red, modifier = Modifier.size(20.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.CameraAlt,
                        null,
                        tint = KioskColors.cyanBright,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Tap to capture live photo",
                        color = KioskColors.textMuted,
                        fontSize = 13.sp,
                        fontFamily = KioskFont,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "EN: Incident photos are kept about 90 days after the incident is closed, then removed. Notes stay in the school audit record.",
            color = KioskColors.textMuted,
            fontSize = 10.sp,
            fontFamily = KioskFont,
            lineHeight = 13.sp,
        )
        Text(
            "HI: घटना बंद होने के बाद फोटो लगभग 90 दिन रखी जाती है, फिर हटा दी जाती है। नोट्स स्कूल ऑडिट रिकॉर्ड में रहते हैं।",
            color = KioskColors.textMuted,
            fontSize = 10.sp,
            fontFamily = KioskFont,
            lineHeight = 13.sp,
            modifier = Modifier.padding(top = 2.dp),
        )

        Spacer(Modifier.height(16.dp))
        if (state.incidentBusy) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                CircularProgressIndicator(
                    color = KioskColors.cyan,
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 3.dp,
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(if (canSubmit) KioskColors.cyan else KioskColors.card)
                    .clickable(enabled = canSubmit, onClick = onSubmit)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Check,
                    null,
                    tint = if (canSubmit) KioskColors.bg else KioskColors.textMuted,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Submit incident",
                    color = if (canSubmit) KioskColors.bg else KioskColors.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", color = KioskColors.textMuted, fontFamily = KioskFont)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
