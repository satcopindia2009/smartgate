package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

@Composable
fun OutcomeStep(
    draft: RegistrationDraft,
    visit: VisitOut?,
    hosts: List<Staff>,
    gates: List<Gate>,
    blacklistHit: BlacklistEntry?,
    onNewVisitor: () -> Unit,
) {
    val host = hosts.firstOrNull { it.id == (visit?.hostId ?: draft.hostId) }
    val gate = gates.firstOrNull { it.id == (visit?.gateId ?: draft.gateId) }
    val status = visit?.status ?: "pending"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(KioskColors.cyanDim),
            contentAlignment = Alignment.Center,
        ) {
            Text("⏳", fontSize = 28.sp)
        }
        Text(
            text = "Visitor registered",
            color = KioskColors.text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 16.dp),
        )
        Text(
            text = when (status) {
                "pending" -> "Waiting for host approval · no QR yet"
                "approved" -> "Approved · pass ${visit?.passId ?: "ready"}"
                else -> status
            },
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            Meta("Visitor", visit?.visitorName ?: draft.visitorName)
            Meta("Host", host?.name ?: "—")
            Meta("Gate", gate?.name ?: "—")
            Meta("Visit", visit?.id ?: "—")
        }
        Text(
            text = "Status · $status  ·  Photo/ID uploaded  ·  QR scan is a later wave",
            color = KioskColors.textDim,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 20.dp),
            textAlign = TextAlign.Center,
        )
        KioskPrimaryButton(
            text = "Register another visitor",
            onClick = onNewVisitor,
            modifier = Modifier.padding(top = 28.dp),
        )
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
