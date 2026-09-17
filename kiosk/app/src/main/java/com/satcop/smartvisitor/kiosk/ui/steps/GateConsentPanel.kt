package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.GateConsent
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg

@Composable
fun GateConsentPanel(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    var langHi by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(KioskColors.card)
            .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LangChip("English", selected = !langHi) { langHi = false }
            LangChip("हिन्दी", selected = langHi) { langHi = true }
        }
        Text(
            text = if (langHi) GateConsent.TITLE_HI else GateConsent.TITLE_EN,
            color = KioskColors.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = if (langHi) GateConsent.BODY_HI else GateConsent.BODY_EN,
            color = KioskColors.textMuted,
            fontSize = 13.sp,
            fontFamily = KioskFont,
            lineHeight = 18.sp,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KioskGhostButton(
                text = GateConsent.DECLINE,
                onClick = onDecline,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
            KioskPrimaryButton(
                text = if (langHi) GateConsent.AGREE_HI else GateConsent.AGREE_EN,
                onClick = onAgree,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
private fun LangChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        color = if (selected) KioskColors.cyanBright else KioskColors.textMuted,
        fontSize = 12.sp,
        fontFamily = KioskFont,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) KioskColors.cyanDim else KioskColors.bg)
            .border(1.dp, if (selected) KioskColors.cyan else KioskColors.border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
