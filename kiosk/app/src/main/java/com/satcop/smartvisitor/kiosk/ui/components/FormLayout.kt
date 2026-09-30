package com.satcop.smartvisitor.kiosk.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

/**
 * Shared step header (title + optional subtitle). NO horizontal padding: the container applies
 * [FormTokens.ScreenHPad] once, so the header shares the fields' left edge.
 */
@Composable
fun FormHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(bottom = FormTokens.HeaderToForm)) {
        Text(
            text = title,
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                color = KioskColors.textMuted,
                fontSize = 14.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = FormTokens.TitleToSub),
            )
        }
    }
}

/** Vertical stack of form blocks with the shared field-to-field spacing. */
@Composable
fun FormFields(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
    ) { content() }
}

/** Shared small label above an input (same style as [KioskField]'s label). */
@Composable
fun FormLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = KioskColors.inputLabel,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = KioskFont,
        modifier = modifier.padding(bottom = FormTokens.LabelToField),
    )
}
