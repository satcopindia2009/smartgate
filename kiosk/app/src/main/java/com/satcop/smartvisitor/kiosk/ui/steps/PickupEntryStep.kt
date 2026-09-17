package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.fixture.PickupStory
import com.satcop.smartvisitor.kiosk.ui.components.KioskCyanButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.PanelDivider
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg

@Composable
fun PickupEntryStep(
    onBackToVisitor: () -> Unit,
    onShowWebHint: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Student pickup",
                    color = KioskColors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
                Text(
                    text = "Priority P2 · separate PickupEvent · gate-lobby release (no QR)",
                    color = KioskColors.textMuted,
                    fontSize = 14.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Text(
                text = "P2",
                color = KioskColors.purpleBright,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
                modifier = Modifier
                    .background(KioskColors.purpleDim, RoundedCornerShape(999.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .border(2.dp, KioskColors.cyan, RoundedCornerShape(RadiusLg))
                    .background(KioskColors.cyanDim, RoundedCornerShape(RadiusLg))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("🎒", fontSize = 22.sp)
                Text(
                    text = "Student pickup",
                    color = KioskColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
                Text(
                    text = "Lobby handover · PickupEvent · P2 focus",
                    color = KioskColors.text,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
            }
            Column(
                modifier = Modifier
                    .width(360.dp)
                    .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg))
                    .background(KioskColors.bg, RoundedCornerShape(RadiusLg))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "DAY-1 WIRED FLOW",
                    color = KioskColors.textDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    fontFamily = KioskFont,
                )
                Text(
                    text = PickupStory.WEB_HINT,
                    color = KioskColors.cyanBright,
                    fontSize = 13.sp,
                    fontFamily = KioskFont,
                    lineHeight = 18.sp,
                )
                Text(
                    text = "This kiosk entry does not replace the visitor register.",
                    color = KioskColors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
            }
        }

        Text(
            text = "Pranay GATE · ${PickupStory.STUDENT_ASHA} ${PickupStory.CLASS_ASHA} → " +
                "${PickupStory.COLLECTOR_PARENT} (parent) + ${PickupStory.COLLECTOR_GUARDIAN} (guardian). " +
                "${PickupStory.STUDENT_ROHAN_SHAH} also on list. Login ${PickupStory.GATE_USER_PRANAY} / " +
                "${PickupStory.GATE_PASS_PRANAY}. Demo Aarav/Kabir stays on SCH-DEMO-01. Not Priya.",
            color = KioskColors.textMuted,
            fontSize = 13.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 16.dp),
            lineHeight = 18.sp,
        )

        Text(
            text = "UX 1–7 (gate)",
            color = KioskColors.textDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        PickupStory.gateScreens.forEach { line ->
            Text(
                text = line,
                color = KioskColors.text,
                fontSize = 13.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        PanelDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KioskGhostButton(text = "Back to visitor register", onClick = onBackToVisitor)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.height(1.dp))
                KioskCyanButton(text = "Open pickup-gate web", onClick = onShowWebHint)
            }
        }
        Text(
            text = "Admin CRUD / history = Admin desk (stub). Custody UI = flag + gate_instruction only.",
            color = KioskColors.textDim,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
