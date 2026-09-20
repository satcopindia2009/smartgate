package com.satcop.smartvisitor.kiosk.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

/** AC-APP1 bottomnav SoT: Home · Inside · Log · More */
private val gateTabs = listOf(
    AppleTabItem("Home", Icons.Filled.Home),
    AppleTabItem("Inside", Icons.Filled.Groups),
    AppleTabItem("Log", Icons.Filled.ListAlt),
    AppleTabItem("More", Icons.Filled.MoreHoriz),
)

/**
 * Gate shell from `/workspace/ux-mocks/phone-apple-bottomnav-2026-09-20/`
 * (gate-home / gate-inside). Compact above-fold · pinned Material3 NavigationBar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GateTodayScreen(
    gateName: String,
    recent: List<InsideVisit>,
    selectedVisitorType: String?,
    onSelectVisitorType: (String) -> Unit,
    onContinueRegistration: () -> Unit,
    onCourier: () -> Unit,
    onHistory: () -> Unit,
    onCheckout: () -> Unit,
    onLostFound: () -> Unit,
    onPickup: () -> Unit,
    onLogout: () -> Unit,
    /** When >1, Home tab shows registration steps under pinned NavigationBar (AC-APP1). */
    registrationStep: Int = 1,
    registrationContent: (@Composable () -> Unit)? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val waiting = recent.filter {
        it.status.equals("pending", ignoreCase = true) ||
            it.status.contains("wait", ignoreCase = true)
    }.take(1)
    val inside = recent.filter {
        it.status.equals("inside", ignoreCase = true) ||
            it.status.equals("checked_in", ignoreCase = true) ||
            it.status.equals("approved", ignoreCase = true)
    }.take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KioskColors.bg),
    ) {
        // Fixed chrome + compact content (NO verticalScroll on home — AC-APP3)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (tab) {
                0 -> { // Home — LIGHT/gate-home.png 1:1 (or registration under shell)
                    if (registrationStep > 1 && registrationContent != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            AppleShellNav(leading = "Home", trailing = " ", onLeading = { /* stay */ })
                            AppleShellTitle("Check-in")
                            AppleShellSub("Step $registrationStep · under shell · tabs stay")
                            registrationContent()
                        }
                    } else {
                    AppleShellNav(leading = "Roles", trailing = "Scan", onTrailing = onPickup)
                    AppleShellTitle("Gate")
                    AppleShellSub("${gateName.ifBlank { "Main" }} · compact home · no long scroll")
                    AppleHeroCta(
                        title = "New check-in",
                        subtitle = "Walk-in visitor",
                        onClick = { sheetOpen = true },
                    )
                    Spacer(Modifier.height(10.dp))
                    AppleGrid2 {
                        AppleTile(icon = "📦", label = "Pickup", detail = "Student release", onClick = onPickup)
                        AppleTile(icon = "📬", label = "Courier", detail = "Log parcel", onClick = onCourier)
                    }
                    Spacer(Modifier.height(10.dp))
                    AppleInset {
                        if (waiting.isEmpty()) {
                            AppleCell(
                                title = "No visitors waiting",
                                subtitle = gateName,
                                showChevron = false,
                                showDivider = false,
                            )
                        } else {
                            val v = waiting.first()
                            val name = v.visitorName ?: "Visitor"
                            val initials = name.split(" ")
                                .mapNotNull { it.firstOrNull()?.toString() }
                                .take(2)
                                .joinToString("")
                                .ifBlank { "?" }
                            CompactPendingRow(
                                initials = initials,
                                title = name,
                                subtitle = "Waiting host",
                                pill = "Pending",
                            )
                        }
                    }
                    }
                }
                1 -> { // Inside — gate-inside.png
                    AppleShellNav(leading = " ", trailing = "Search")
                    AppleShellTitle("Inside")
                    AppleShellSub("On campus · ${inside.size.coerceAtLeast(recent.size).coerceAtMost(99)}")
                    AppleInset {
                        if (inside.isEmpty() && recent.isEmpty()) {
                            AppleCell("No one inside", showChevron = false, showDivider = false)
                        } else {
                            val rows = inside.ifEmpty { recent.take(3) }
                            rows.forEachIndexed { i, v ->
                                val name = v.visitorName ?: "Visitor"
                                val initials = name.split(" ")
                                    .mapNotNull { it.firstOrNull()?.toString() }
                                    .take(2)
                                    .joinToString("")
                                    .ifBlank { "?" }
                                CompactPendingRow(
                                    initials = initials,
                                    title = name,
                                    subtitle = listOfNotNull(v.gateId, v.status).joinToString(" · "),
                                    trailing = v.visitorType ?: "",
                                    showDivider = i < rows.lastIndex,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    ApplePrimaryButton("Check out visitor", onClick = onCheckout)
                }
                2 -> { // Log
                    AppleShellNav(leading = " ", trailing = " ")
                    AppleShellTitle("Log")
                    AppleShellSub("Visit history")
                    AppleInset {
                        AppleCell("Visit history", onClick = onHistory, showDivider = false)
                    }
                }
                else -> { // More
                    AppleShellNav(leading = " ", trailing = " ")
                    AppleShellTitle("More")
                    AppearanceSegmentedRow()
                    AppleSectionHeader("Account")
                    AppleInset {
                        AppleCell("Lost & Found", onClick = onLostFound, showDivider = true)
                        AppleCell("Sign out", showChevron = false, showDivider = false, onClick = onLogout)
                    }
                }
            }
        }
        AppleTabBar(tabs = gateTabs, selectedIndex = tab, onSelect = { tab = it })
    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
            containerColor = KioskColors.card,
        ) {
            Text(
                "New visitor",
                color = KioskColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = KioskFont,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            StepperDots(active = 0, total = 3)
            AppleSectionHeader("Type")
            val types = listOf(
                "Parent / Guardian" to "Parent",
                "Vendor" to "Vendor",
                "Guest" to "Guest",
                "Official" to "Official",
            )
            AppleInset {
                types.forEachIndexed { i, (label, key) ->
                    val selected = selectedVisitorType.equals(key, ignoreCase = true)
                    AppleCell(
                        title = label,
                        trailing = if (selected) "✓" else null,
                        showChevron = !selected,
                        showDivider = i < types.lastIndex,
                        onClick = { onSelectVisitorType(key) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ApplePrimaryButton("Continue") {
                if (selectedVisitorType.isNullOrBlank()) {
                    onSelectVisitorType("Parent")
                }
                sheetOpen = false
                onContinueRegistration()
            }
            ApplePlainButton("Cancel") { sheetOpen = false }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun CompactPendingRow(
    initials: String,
    title: String,
    subtitle: String,
    pill: String? = null,
    trailing: String? = null,
    showDivider: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleAvatar(initials = initials, size = 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = KioskColors.text, fontSize = 16.sp, fontFamily = KioskFont)
                Text(subtitle, color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
            }
            if (pill != null) {
                AppleStatusPill(pill)
            } else if (!trailing.isNullOrBlank()) {
                Text(trailing, color = KioskColors.textMuted, fontSize = 15.sp, fontFamily = KioskFont)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 14.dp),
                thickness = 0.33.dp,
                color = KioskColors.border,
            )
        }
    }
}

@Composable
private fun StepperDots(active: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .height(8.dp)
                    .width(if (i == active) 18.dp else 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == active) KioskColors.systemBlue else KioskColors.secondaryFill),
            )
        }
    }
}
