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
import com.satcop.smartvisitor.kiosk.data.model.AfterHoursCopy
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.LocalKioskCompact
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusChip
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusKind
import com.satcop.smartvisitor.kiosk.ui.components.sgCardSurface
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusSm
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember

/** Screen 26 "Visitor pass": name, host, status chip, QR (only when the server issued a token/pass), Done. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OutcomeStep(
    draft: RegistrationDraft,
    visit: VisitOut?,
    hosts: List<Staff>,
    gates: List<Gate>,
    blacklistHit: BlacklistEntry?,
    dataSource: DataSource,
    afterHoursHint: Boolean = false,
    onNewVisitor: () -> Unit,
) {
    val host = hosts.firstOrNull { it.id == (visit?.hostId ?: draft.hostId) }
    val gate = gates.firstOrNull { it.id == (visit?.gateId ?: draft.gateId) }
    val status = visit?.status ?: "pending"
    val qrPayload = visit?.qrToken?.takeIf { it.isNotBlank() } ?: visit?.passId?.takeIf { it.isNotBlank() }
    // Product ruling: the QR is display only and shows for every status, including pending.
    val showQr = qrPayload != null
    val timeLabel = visit?.timeOut ?: visit?.timeIn
    val (chipLabel, chipKind) = when (status) {
        "approved" -> "Approved" to SgStatusKind.COMPLETED
        "inside" -> "Checked in" to SgStatusKind.IN_PROGRESS
        "completed" -> "Checked out" to SgStatusKind.COMPLETED
        "rejected" -> "Rejected" to SgStatusKind.REJECTED
        else -> "Pending approval" to SgStatusKind.PENDING
    }
    val name = visit?.visitorName ?: draft.visitorName

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        com.satcop.smartvisitor.kiosk.ui.addvisitor.AvTopBar("Visitor pass", onClose = onNewVisitor)
        Column(
            modifier = Modifier.fillMaxWidth().sgCardSurface().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier.size(72.dp).clip(CircleShape).background(KioskColors.brandSoft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "V" },
                    color = KioskColors.primary, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont,
                )
            }
            Text(name, color = KioskColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, textAlign = TextAlign.Center)
            Text("Host: ${host?.name ?: visit?.hostId ?: "Host"}", color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont)
            SgStatusChip(label = chipLabel, kind = chipKind)
            if (showQr && qrPayload != null) {
                val bmp = remember(qrPayload) { com.satcop.smartvisitor.kiosk.ui.media.QrBitmap.encode(qrPayload, 512) }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Visitor pass QR",
                        modifier = Modifier.padding(top = 8.dp).size(200.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
                    )
                    Text("Visitor pass QR · display only", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
                }
            } else if (status == "pending") {
                Text(
                    "Waiting for host approval. The pass is issued once the host approves.",
                    color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                listOfNotNull(gate?.name, timeLabel?.displayTime()).joinToString(" · "),
                color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont,
            )
        }
        if (blacklistHit?.severity == "Alert") {
            Text(
                text = "Watch-list alert: ${blacklistHit.name ?: "visitor"}. The security head has been told.",
                color = KioskColors.warning, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = KioskFont,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusSm)).background(KioskColors.warningSoft).padding(12.dp),
            )
        }
        val afterHours = afterHoursHint || visit?.afterHours == true
        if (afterHours && status == "pending") {
            Text(
                text = "After hours or holiday: the Admin or Security Head must approve.",
                color = KioskColors.warning, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = KioskFont,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(RadiusSm)).background(KioskColors.warningSoft).padding(12.dp),
            )
        }
        SgPrimaryButton(text = "Done", onClick = onNewVisitor, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}

private fun String.displayTime(): String {
    if (this == "—" || length < 16) return this
    return drop(11).take(5)
}
