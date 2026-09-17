package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.fixture.DemoHubItem
import com.satcop.smartvisitor.kiosk.data.fixture.DemoHubLinks
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg

@Composable
fun DemoHubStep(
    onOpenGate: () -> Unit,
    onOpenPreview: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                text = "Demo hub",
                color = KioskColors.text,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
            Text(
                text = "One install · gate + after-hours + escort + host + QR",
                color = KioskColors.textMuted,
                fontSize = 14.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        DemoHubLinks.items.forEach { item ->
            DemoHubCard(
                item = item,
                onClick = {
                    if (item.inApp) {
                        onOpenGate()
                    } else {
                        item.url?.let(onOpenPreview)
                    }
                },
            )
        }

        Text(
            text = DemoHubLinks.TUNNEL_NOTE,
            color = KioskColors.peakAmber,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontFamily = KioskFont,
            modifier = Modifier
                .fillMaxWidth()
                .background(KioskColors.orangeDim, RoundedCornerShape(RadiusLg))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun DemoHubCard(
    item: DemoHubItem,
    onClick: () -> Unit,
) {
    val border = if (item.inApp) KioskColors.purple else KioskColors.border
    val bg = if (item.inApp) KioskColors.purpleDim else KioskColors.bg
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg, RoundedCornerShape(RadiusLg))
            .border(if (item.inApp) 2.dp else 1.dp, border, RoundedCornerShape(RadiusLg))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = item.emoji, fontSize = 22.sp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.title,
                color = KioskColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
            Text(
                text = item.subtitle,
                color = KioskColors.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = KioskFont,
            )
            if (item.url != null) {
                Text(
                    text = "Opens in browser",
                    color = KioskColors.cyanBright,
                    fontSize = 11.sp,
                    fontFamily = KioskFont,
                )
            }
        }
    }
}
