package com.satcop.smartvisitor.kiosk.ui

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.AfterHoursCopy
import com.satcop.smartvisitor.kiosk.data.model.RejectReasons
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.media.PlaceholderBitmap
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusSm

@Composable
fun HostHomeScreen(
    displayName: String,
    schoolId: String,
    staffId: String,
    pending: List<VisitOut>,
    photos: Map<String, Bitmap>,
    afterHours: Boolean,
    busy: Boolean,
    rejectingVisitId: String?,
    rejectReason: String?,
    compact: Boolean,
    onRefresh: () -> Unit,
    onApprove: (String) -> Unit,
    onStartReject: (String) -> Unit,
    onPickRejectReason: (String) -> Unit,
    onCancelReject: () -> Unit,
    onConfirmReject: () -> Unit,
    onShowAfterHours: () -> Unit,
    showingAfterHours: Boolean,
) {
    val identity = listOf(displayName, schoolId, staffId)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        Text(
            text = "Host approve",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        if (identity.isNotBlank()) {
            Text(
                text = identity,
                color = KioskColors.cyanBright,
                fontSize = 14.sp,
                fontFamily = KioskFont,
            )
        }
        Text(
            text = "Pending visits for your JWT host. Approve and reject stay in this app.",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        if (afterHours) {
            AfterHoursBanner()
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KioskGhostButton(
                text = if (busy) "Refreshing…" else "Pending",
                onClick = onRefresh,
                enabled = !busy,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
            KioskGhostButton(
                text = "After-hours",
                onClick = onShowAfterHours,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        }
        if (showingAfterHours) {
            AfterHoursPanel()
        } else if (pending.isEmpty()) {
            Text(
                text = "No pending visits",
                color = KioskColors.textMuted,
                fontSize = 15.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        } else {
            pending.forEach { visit ->
                PendingVisitCard(
                    visit = visit,
                    photo = photos[visit.id],
                    busy = busy,
                    rejecting = rejectingVisitId == visit.id,
                    rejectReason = if (rejectingVisitId == visit.id) rejectReason else null,
                    afterHours = afterHours || visit.afterHours,
                    onApprove = { onApprove(visit.id) },
                    onStartReject = { onStartReject(visit.id) },
                    onPickRejectReason = onPickRejectReason,
                    onCancelReject = onCancelReject,
                    onConfirmReject = onConfirmReject,
                )
            }
        }
    }
}

@Composable
private fun AfterHoursBanner() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusSm))
            .background(KioskColors.orangeDim)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = AfterHoursCopy.HOST_NO_OP,
            color = KioskColors.peakAmber,
            fontSize = 13.sp,
            fontFamily = KioskFont,
        )
    }
}

@Composable
private fun AfterHoursPanel() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(KioskColors.bg)
            .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "After-hours policy",
            color = KioskColors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = AfterHoursCopy.HOST_NO_OP,
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        Text(
            text = "Gate can still register. ${AfterHoursCopy.CODE} is returned if a host taps Approve.",
            color = KioskColors.textDim,
            fontSize = 12.sp,
            fontFamily = KioskFont,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PendingVisitCard(
    visit: VisitOut,
    photo: Bitmap?,
    busy: Boolean,
    rejecting: Boolean,
    rejectReason: String?,
    afterHours: Boolean,
    onApprove: () -> Unit,
    onStartReject: () -> Unit,
    onPickRejectReason: (String) -> Unit,
    onCancelReject: () -> Unit,
    onConfirmReject: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(KioskColors.bg)
            .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                if (visit.consentAt.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .border(1.dp, KioskColors.border, CircleShape)
                            .background(KioskColors.card),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("—", color = KioskColors.textMuted, fontSize = 18.sp, fontFamily = KioskFont)
                    }
                    Text(
                        text = "Photo after consent",
                        color = KioskColors.textMuted,
                        fontSize = 10.sp,
                        fontFamily = KioskFont,
                    )
                } else {
                    LivePhoto(photo = photo, name = visit.visitorName.orEmpty())
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = visit.visitorName ?: "Visitor",
                    color = KioskColors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
                Text(
                    text = listOfNotNull(
                        visit.purpose,
                        visit.visitorType,
                        visit.gateId,
                    ).joinToString(" · "),
                    color = KioskColors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
                Text(
                    text = visit.mobile ?: visit.id,
                    color = KioskColors.cyanBright,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
            }
        }
        if (afterHours) {
            Text(
                text = AfterHoursCopy.HOST_NO_OP,
                color = KioskColors.peakAmber,
                fontSize = 12.sp,
                fontFamily = KioskFont,
            )
        }
        if (rejecting) {
            Text(
                text = "Reject reason",
                color = KioskColors.textMuted,
                fontSize = 12.sp,
                fontFamily = KioskFont,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RejectReasons.chips.forEach { reason ->
                    val selected = reason == rejectReason
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(if (selected) KioskColors.redDim else KioskColors.card)
                            .border(
                                1.dp,
                                if (selected) KioskColors.red else KioskColors.border,
                                RoundedCornerShape(RadiusSm),
                            )
                            .clickable { onPickRejectReason(reason) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(reason, color = KioskColors.text, fontSize = 12.sp, fontFamily = KioskFont)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KioskGhostButton(
                    text = "Cancel",
                    onClick = onCancelReject,
                    modifier = Modifier.weight(1f),
                )
                KioskPrimaryButton(
                    text = "Confirm reject",
                    onClick = onConfirmReject,
                    enabled = !busy && !rejectReason.isNullOrBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KioskPrimaryButton(
                    text = if (afterHours) "Approve (no-op)" else "Approve",
                    onClick = onApprove,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
                KioskGhostButton(
                    text = "Reject",
                    onClick = onStartReject,
                    enabled = !busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LivePhoto(photo: Bitmap?, name: String) {
    val bmp = photo ?: PlaceholderBitmap.livePhoto(name, width = 160, height = 160)
    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = name,
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .border(1.dp, KioskColors.border, CircleShape),
    )
}

@Composable
fun UnsupportedRoleScreen(
    role: String,
    compact: Boolean,
    onLogout: () -> Unit,
) {
    val label = role.trim().ifBlank { "unknown" }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        Text(
            text = "This APK is for Gate or Host",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Signed in as $label. Use Log out, then sign in with a gate or host account.",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        KioskGhostButton(
            text = "Log out",
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
    }
}
