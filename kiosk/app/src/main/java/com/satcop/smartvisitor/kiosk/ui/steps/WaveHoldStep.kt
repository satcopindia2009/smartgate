package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.PanelDivider
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg

@Composable
fun WaveHoldStep(
    draft: RegistrationDraft,
    hosts: List<Staff>,
    gates: List<Gate>,
    onBack: () -> Unit,
) {
    val host = hosts.firstOrNull { it.id == draft.hostId }
    val gate = gates.firstOrNull { it.id == draft.gateId }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Photo, ID & signature",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Wave 2 — camera, media upload, and blacklist match are not wired yet",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(RadiusLg))
                .background(KioskColors.bg)
                .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Validated draft (fixtures only — not submitted)",
                color = KioskColors.cyanBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KioskFont,
            )
            SummaryRow("visitorName", draft.visitorName)
            SummaryRow("mobile", draft.mobile)
            SummaryRow("visitorType", draft.visitorType)
            SummaryRow("purpose", draft.purpose)
            SummaryRow("hostId", "${draft.hostId} · ${host?.name ?: "—"}")
            SummaryRow("gateId", "${draft.gateId} · ${gate?.name ?: "—"}")
            if (draft.vehicleNumber.isNotBlank()) {
                SummaryRow("vehicleNumber", draft.vehicleNumber)
            }
            if (draft.accompanyingCount.isNotBlank()) {
                SummaryRow("accompanyingCount", draft.accompanyingCount)
            }
            if (draft.notes.isNotBlank()) {
                SummaryRow("notes", draft.notes)
            }
        }

        PanelDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            KioskGhostButton(text = "Back", onClick = onBack)
            Text(
                text = "Submit & notify host — Wave 2",
                color = KioskColors.textDim,
                fontSize = 13.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun SummaryRow(key: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = key,
            color = KioskColors.textDim,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.weight(0.38f),
        )
        Text(
            text = value,
            color = KioskColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            modifier = Modifier.weight(0.62f),
        )
    }
}
