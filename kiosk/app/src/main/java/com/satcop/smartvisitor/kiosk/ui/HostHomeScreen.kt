package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

@Composable
fun HostHomeScreen(
    displayName: String,
    schoolId: String,
    staffId: String,
    compact: Boolean,
    onOpenUrl: (String) -> Unit,
) {
    val identity = listOf(displayName, schoolId, staffId)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        Text(
            text = "Host approve",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        if (identity.isNotBlank()) {
            Text(
                text = identity,
                color = KioskColors.cyanBright,
                fontSize = 14.sp,
                fontFamily = KioskFont,
            )
        }
        Text(
            text = "Approve or reject visits on host-web. This kiosk does not register visitors for the host role.",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        KioskPrimaryButton(
            text = "Open host-web",
            onClick = { onOpenUrl(HostWebLinks.BASE) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(min = 52.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KioskGhostButton(
                text = "Pending",
                onClick = { onOpenUrl(HostWebLinks.PENDING) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
            KioskGhostButton(
                text = "After-hours",
                onClick = { onOpenUrl(HostWebLinks.AFTER_HOURS) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp),
            )
        }
    }
}

@Composable
fun UnsupportedRoleScreen(
    role: String,
    compact: Boolean,
    onLogout: () -> Unit,
) {
    val label = role.trim().ifBlank { "unknown" }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        Text(
            text = "This APK is for Gate or Host",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Signed in as $label. Use Log out, then sign in with a gate or host account.",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        KioskGhostButton(
            text = "Log out",
            onClick = onLogout,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
    }
}
