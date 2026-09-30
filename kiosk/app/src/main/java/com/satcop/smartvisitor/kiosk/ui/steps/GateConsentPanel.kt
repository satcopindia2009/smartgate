package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.GateConsent
import com.satcop.smartvisitor.kiosk.ui.components.SgFilterChipRow
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.components.sgCardSurface
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

/** Visitor notice: teal language chips (English / हिन्दी), notice in a card, Agree (primary) and Decline (secondary). Copy is unchanged. */
@Composable
fun GateConsentPanel(
    onAgree: () -> Unit,
    onDecline: () -> Unit,
) {
    var lang by remember { mutableIntStateOf(0) }
    val langHi = lang == 1
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .sgCardSurface()
            .padding(FormTokens.ScreenHPad),
        verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
    ) {
        SgFilterChipRow(options = listOf("English", "हिन्दी"), selectedIndex = lang, onSelect = { lang = it })
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
        Column(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap)) {
            SgPrimaryButton(
                text = if (langHi) GateConsent.AGREE_HI else GateConsent.AGREE_EN,
                onClick = onAgree,
                modifier = Modifier.fillMaxWidth(),
            )
            SgSecondaryButton(text = GateConsent.DECLINE, onClick = onDecline, modifier = Modifier.fillMaxWidth())
        }
    }
}
