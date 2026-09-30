package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.SgType

/** Copy of the screen-29 warning (Product-approved text). */
const val VENDOR_UNAPPROVED_TEXT = "This company is not approved yet. You can continue, and Admin will review it."

/**
 * Screen 29: amber warning banner shown at the top of the New vendor form when the entered
 * company is not on the approved-vendor list. Pure UI; the caller decides when to show it.
 */
@Composable
fun VendorUnapprovedBanner(modifier: Modifier = Modifier, text: String = VENDOR_UNAPPROVED_TEXT) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(KioskColors.warningSoft)
            .padding(12.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Warning, contentDescription = null, tint = KioskColors.warning, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (KioskColors.isDark) KioskColors.text else KioskColors.warning, style = SgType.Label, modifier = Modifier.weight(1f))
    }
}
