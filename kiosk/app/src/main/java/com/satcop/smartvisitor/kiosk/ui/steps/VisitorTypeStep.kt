@file:OptIn(ExperimentalLayoutApi::class)

package com.satcop.smartvisitor.kiosk.ui.steps

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.data.model.VisitorType
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.PanelDivider
import com.satcop.smartvisitor.kiosk.ui.components.RecentChip
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun VisitorTypeStep(
    selectedType: String,
    schoolName: String,
    clockLabel: String,
    recent: List<InsideVisit>,
    gates: List<Gate>,
    onSelectType: (String) -> Unit,
    onPrefill: () -> Unit,
    onDemoHub: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column {
                Text(
                    text = "Who is visiting?",
                    color = KioskColors.text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
                Text(
                    text = "Select visitor type to begin registration",
                    color = KioskColors.textMuted,
                    fontSize = 14.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KioskGhostButton(text = "Demo hub", onClick = onDemoHub)
                KioskGhostButton(text = "Prefill sample", onClick = onPrefill)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FlowRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                VisitorType.entries.forEach { type ->
                    TypeCard(
                        type = type,
                        selected = type.apiValue == selectedType,
                        onClick = { onSelectType(type.apiValue) },
                        modifier = Modifier.width(168.dp),
                    )
                }
            }
            KioskContextStrip(
                schoolName = schoolName,
                clockLabel = clockLabel,
                recent = recent,
                gates = gates,
                modifier = Modifier.width(280.dp),
            )
        }

        PanelDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            KioskPrimaryButton(text = "Continue", onClick = onContinue)
        }
    }
}

@Composable
private fun TypeCard(
    type: VisitorType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) KioskColors.purple else KioskColors.border
    val bg = if (selected) KioskColors.purpleDim else KioskColors.bg
    Column(
        modifier = modifier
            .heightIn(min = 108.dp)
            .clip(RoundedCornerShape(RadiusLg))
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(RadiusLg),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = type.emoji, fontSize = 22.sp)
        Text(
            text = type.apiValue,
            color = KioskColors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = type.help,
            color = if (selected) Color(0xBFF3F4F6) else KioskColors.textMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontFamily = KioskFont,
        )
    }
}

@Composable
private fun KioskContextStrip(
    schoolName: String,
    clockLabel: String,
    recent: List<InsideVisit>,
    gates: List<Gate>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(RadiusLg))
            .background(KioskColors.bg)
            .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        StripBlock(label = "School") {
            Text(
                text = schoolName,
                color = KioskColors.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
            Text(
                text = clockLabel.ifBlank { "—" },
                color = KioskColors.cyanBright,
                fontSize = 13.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        StripBlock(label = "Queue tip") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(KioskColors.orangeDim)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    text = DemoFixtures.PEAK_TIP_TITLE,
                    color = KioskColors.peakAmberBright,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
                Text(
                    text = DemoFixtures.PEAK_TIP_BODY,
                    color = KioskColors.peakAmber,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        StripBlock(label = "Recent check-ins", showDivider = false) {
            if (recent.isEmpty()) {
                RecentChip(label = "No recent yet")
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    recent.forEach { visit ->
                        val gate = gates.firstOrNull { it.id == visit.gateId }?.name
                        RecentChip(
                            label = listOfNotNull(
                                visit.visitorName,
                                formatTimeIn(visit.timeIn),
                                gate,
                            ).joinToString(" · "),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StripBlock(
    label: String,
    showDivider: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (showDivider) {
                    Modifier
                        .padding(bottom = 0.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Text(
            text = label.uppercase(),
            color = KioskColors.textDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        content()
        if (showDivider) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(KioskColors.borderSubtle),
            )
        }
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

private fun formatTimeIn(iso: String?): String {
    if (iso.isNullOrBlank()) return "—"
    return runCatching {
        OffsetDateTime.parse(iso).format(timeFmt)
    }.getOrDefault(iso)
}
