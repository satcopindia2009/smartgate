package com.satcop.smartvisitor.kiosk.ui.duty

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.ui.components.SgDangerOutlineButton
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardBanner
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardBannerKind
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardCard
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardIconBadge
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
import com.satcop.smartvisitor.kiosk.ui.theme.SgSpacing
import com.satcop.smartvisitor.kiosk.ui.theme.SgType

/** "Gate desk | Patrol" switcher, shown ONLY when the guard has both duties. */
@Composable
fun DutyAreaSwitcher(active: DutyArea?, onSelect: (DutyArea) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = SgSpacing.ScreenMargin, vertical = 8.dp)
            .clip(PillShape).background(KioskColors.brandSoft).padding(4.dp),
    ) {
        for ((area, label) in listOf(DutyArea.GATE to DutyLogic.AREA_GATE, DutyArea.PATROL to DutyLogic.AREA_PATROL)) {
            val on = active == area
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(PillShape)
                    .background(if (on) KioskColors.primary else Color.Transparent)
                    .clickable(role = Role.Tab) { onSelect(area) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) KioskColors.onPrimary else KioskColors.primary, style = SgType.Button, maxLines = 1)
            }
        }
    }
}

/** "Your duty was updated. Tap to refresh." Tapping re-reads duty and re-renders. */
@Composable
fun DutyUpdatedBanner(onTap: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = SgSpacing.ScreenMargin, vertical = 6.dp).clickable(role = Role.Button, onClick = onTap)) {
        GuardBanner(DutyLogic.DUTY_UPDATED_TEXT, GuardBannerKind.INFO, Icons.Outlined.Info)
    }
}

/** 1078: "Your shift is not active now (Day 06:00–14:00)." The area stays visible; new-action buttons are off. */
@Composable
fun ShiftInactiveBanner(text: String) {
    Box(Modifier.fillMaxWidth().padding(horizontal = SgSpacing.ScreenMargin, vertical = 6.dp)) {
        GuardBanner(text, GuardBannerKind.WARNING, Icons.Outlined.Info)
    }
}

/** Gate chooser (two or more gate duties). White chips on the teal lock screen; first gate is the default. */
@Composable
fun GateChooser(gates: List<Pair<String, String>>, chosenId: String?, onChoose: (String) -> Unit, onTeal: Boolean = true) {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Gate", color = if (onTeal) Color.White.copy(alpha = 0.9f) else KioskColors.textMuted, style = SgType.Label)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            for ((id, name) in gates) {
                val on = id == (chosenId ?: gates.firstOrNull()?.first)
                Box(
                    Modifier.heightIn(min = 40.dp).clip(PillShape)
                        .background(if (on) Color.White else Color.Black.copy(alpha = 0.22f))
                        .clickable(role = Role.RadioButton) { onChoose(id) }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(name.ifBlank { "Gate" }, color = if (on) KioskColors.primary else Color.White, style = SgType.Label, maxLines = 1) }
            }
        }
    }
}

/** A guard with no duty today: still clocked in; sees the text and the shared tools. */
@Composable
fun NoDutyHome(
    displayName: String,
    onIncident: () -> Unit,
    onLostFound: () -> Unit,
    onCourier: () -> Unit,
    onFindVisitor: () -> Unit,
    onClockOut: () -> Unit,
    onSignOut: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(KioskColors.bg).verticalScroll(rememberScrollState())
            .padding(horizontal = SgSpacing.ScreenMargin, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
    ) {
        if (displayName.isNotBlank()) Text(displayName, color = KioskColors.text, style = SgType.ScreenTitle)
        GuardBanner(DutyLogic.NO_DUTY_TEXT, GuardBannerKind.INFO, Icons.Outlined.Info)
        GuardCard {
            Row1(Icons.Outlined.Report, "Report incident", onIncident)
            Row1(Icons.Outlined.Inventory2, "Lost & Found", onLostFound)
            Row1(Icons.Outlined.Inventory2, "Courier log", onCourier)
            Row1(Icons.Outlined.Search, "Find visitor", onFindVisitor)
        }
        SgDangerOutlineButton("Clock out", onClockOut, Modifier.fillMaxWidth())
        SgDangerOutlineButton("Sign out", onSignOut, Modifier.fillMaxWidth())
    }
}

@Composable
private fun Row1(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GuardIconBadge(icon, size = 40.dp, bg = KioskColors.successSoft, tint = KioskColors.success)
        Spacer(Modifier.height(0.dp).padding(start = 12.dp))
        Text(title, color = KioskColors.text, style = SgType.BodyStrong, modifier = Modifier.weight(1f).padding(start = 12.dp))
        androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = KioskColors.textMuted)
    }
}
