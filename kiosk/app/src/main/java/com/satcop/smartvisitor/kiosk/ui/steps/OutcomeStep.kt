package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.LocalKioskCompact
import com.satcop.smartvisitor.kiosk.ui.components.KioskCyanButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutcomeStep(
    draft: RegistrationDraft,
    visit: VisitOut?,
    hosts: List<Staff>,
    gates: List<Gate>,
    blacklistHit: BlacklistEntry?,
    dataSource: DataSource,
    busy: Boolean,
    onRefresh: () -> Unit,
    onDemoApprove: () -> Unit,
    onCheckIn: () -> Unit,
    onCheckOut: () -> Unit,
    onLoadStory: () -> Unit,
    onNewVisitor: () -> Unit,
    showStory: Boolean = true,
) {
    val host = hosts.firstOrNull { it.id == (visit?.hostId ?: draft.hostId) }
    val gate = gates.firstOrNull { it.id == (visit?.gateId ?: draft.gateId) }
    val status = visit?.status ?: "pending"
    val showQr = status in setOf("approved", "inside", "completed") && !visit?.passId.isNullOrBlank()
    val canDemoApprove = status == "pending" &&
        (dataSource == DataSource.FIXTURES || visit?.id?.startsWith("V-LOCAL") == true)
    val timeLabel = visit?.timeOut ?: visit?.timeIn ?: "—"
    val compact = LocalKioskCompact.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        OutcomeGlyph(status = status)
        Text(
            text = headline(status),
            color = KioskColors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = subtitle(status, visit, dataSource),
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 6.dp, bottom = 20.dp),
            textAlign = TextAlign.Center,
        )
        if (blacklistHit?.severity == "Alert") {
            Text(
                text = "ALERT · ${blacklistHit.name ?: "watchlist"} · Security Head notified",
                color = KioskColors.peakAmber,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KioskFont,
                modifier = Modifier.padding(bottom = 16.dp),
                textAlign = TextAlign.Center,
            )
        }
        if (showQr) {
            PassQrBox(passId = visit?.passId, token = visit?.qrToken)
        }
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (showQr) 20.dp else 0.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Meta("Visitor", visit?.visitorName ?: draft.visitorName)
            Meta("Host", host?.name ?: visit?.hostId ?: "Host")
            Meta(if (visit?.timeOut != null) "Time-out" else "Time-in", timeLabel.displayTime())
            Meta("Gate", gate?.name ?: visit?.gateId ?: "Gate")
        }
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (status == "pending") {
                    KioskGhostButton(
                        text = if (busy) "Refreshing…" else "Refresh",
                        onClick = onRefresh,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (canDemoApprove) {
                        KioskCyanButton(
                            text = "Demo host approve",
                            onClick = onDemoApprove,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (status == "approved") {
                    KioskCyanButton(
                        text = if (busy) "Scanning…" else "Check in",
                        onClick = onCheckIn,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (status == "inside") {
                    KioskCyanButton(
                        text = if (busy) "Scanning…" else "Check out",
                        onClick = onCheckOut,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (showStory) {
                    KioskGhostButton(
                        text = "Load P-4F21 story",
                        onClick = onLoadStory,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                KioskPrimaryButton(
                    text = "Register another visitor",
                    onClick = onNewVisitor,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                if (status == "pending") {
                    KioskGhostButton(text = if (busy) "Refreshing…" else "Refresh", onClick = onRefresh, enabled = !busy)
                    if (canDemoApprove) {
                        KioskCyanButton(text = "Demo host approve", onClick = onDemoApprove, enabled = !busy)
                    }
                }
                if (status == "approved") {
                    KioskCyanButton(text = if (busy) "Scanning…" else "Check in", onClick = onCheckIn, enabled = !busy)
                }
                if (status == "inside") {
                    KioskCyanButton(text = if (busy) "Scanning…" else "Check out", onClick = onCheckOut, enabled = !busy)
                }
            }
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showStory) {
                    KioskGhostButton(text = "Load P-4F21 story", onClick = onLoadStory, enabled = !busy)
                }
            }
            KioskPrimaryButton(
                text = "Register another visitor",
                onClick = onNewVisitor,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

@Composable
private fun OutcomeGlyph(status: String) {
    val (bg, glyph) = when (status) {
        "rejected" -> KioskColors.redDim to "✕"
        "pending" -> KioskColors.orangeDim to "⏳"
        else -> KioskColors.greenDim to "✓"
    }
    val fg = when (status) {
        "rejected" -> KioskColors.red
        "pending" -> KioskColors.peakAmber
        else -> KioskColors.greenBright
    }
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, color = fg, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PassQrBox(passId: String?, token: String?) {
    Box(
        modifier = Modifier
            .size(200.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(KioskColors.bg)
                .border(1.dp, KioskColors.borderSubtle, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "SATCOP",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
                Text(
                    "PASS",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
                if (!passId.isNullOrBlank()) {
                    Text(
                        passId,
                        color = KioskColors.cyanBright,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = KioskFont,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
                if (!token.isNullOrBlank()) {
                    Text(
                        token.take(18),
                        color = KioskColors.textDim,
                        fontSize = 10.sp,
                        fontFamily = KioskFont,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Meta(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
        Text(
            value,
            color = KioskColors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

private fun headline(status: String): String = when (status) {
    "pending" -> "Waiting for host"
    "approved" -> "Visitor registered"
    "inside" -> "Inside campus"
    "completed" -> "Checked out"
    "rejected" -> "Host rejected"
    else -> "Visitor registered"
}

private fun subtitle(status: String, visit: VisitOut?, source: DataSource): String = when (status) {
    "pending" -> if (source == DataSource.FIXTURES) {
        "No QR yet · Demo host approve issues pass"
    } else {
        "No QR yet · host must approve · then Refresh"
    }
    "approved" -> "Pass issued · ${visit?.passId ?: "ready"} · show QR at gate"
    "inside" -> "Checked in · present QR on exit"
    "completed" -> "Visit complete · ${visit?.passId ?: ""}"
    "rejected" -> visit?.rejectReason ?: "Gate will inform the visitor"
    else -> status
}

private fun String.displayTime(): String {
    if (this == "—" || length < 16) return this
    return drop(11).take(5)
}
