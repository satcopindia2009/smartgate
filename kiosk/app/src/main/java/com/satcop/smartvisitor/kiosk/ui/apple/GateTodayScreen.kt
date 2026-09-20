package com.satcop.smartvisitor.kiosk.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

private val gateTabs = listOf(
    AppleTabItem("🏠", "Today"),
    AppleTabItem("👤", "Visitors"),
    AppleTabItem("📋", "Log"),
    AppleTabItem("⚙️", "More"),
)

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
) {
    var tab by remember { mutableIntStateOf(0) }
    var seg by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var sheetOpen by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KioskColors.bg),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when (tab) {
                0 -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    AppleNavBar(
                        title = "Gate",
                        leading = "Edit",
                        trailing = "Scan",
                        onTrailing = onPickup,
                    )
                    AppleLargeTitle("Today")
                    AppleSearchField(
                        value = search,
                        onValueChange = { search = it },
                        placeholder = "🔍 Search visitors or hosts",
                    )
                    Spacer(Modifier.height(8.dp))
                    AppleSegmented(
                        options = listOf("Check-in", "Inside", "History"),
                        selectedIndex = seg,
                        onSelect = { idx ->
                            seg = idx
                            when (idx) {
                                1 -> onCheckout()
                                2 -> onHistory()
                            }
                        },
                    )
                    AppleSectionHeader("Quick actions")
                    AppleInset {
                        AppleCell(
                            title = "New walk-in visitor",
                            subtitle = "Parent, vendor, guest…",
                            glyph = "＋",
                            glyphColor = KioskColors.systemBlue,
                            onClick = { sheetOpen = true },
                        )
                        AppleCell(
                            title = "Scan invite QR",
                            glyph = "▦",
                            glyphColor = Color(0xFF64D2FF),
                            onClick = onPickup,
                        )
                        AppleCell(
                            title = "Courier drop-off",
                            glyph = "📦",
                            glyphColor = KioskColors.systemOrange,
                            showDivider = false,
                            onClick = onCourier,
                        )
                    }
                    AppleSectionHeader("Waiting for host")
                    AppleInset {
                        val waiting = recent.filter {
                            it.status.equals("pending", ignoreCase = true) ||
                                it.status.contains("wait", ignoreCase = true)
                        }
                        if (waiting.isEmpty()) {
                            AppleCell(
                                title = "No visitors waiting",
                                subtitle = gateName,
                                showChevron = false,
                                showDivider = false,
                            )
                        } else {
                            waiting.forEachIndexed { i, v ->
                                val name = v.visitorName ?: "Visitor"
                                val initials = name.split(" ")
                                    .mapNotNull { it.firstOrNull()?.toString() }
                                    .take(2)
                                    .joinToString("")
                                    .ifBlank { "?" }
                                WaitingRow(
                                    initials = initials,
                                    title = name,
                                    subtitle = listOfNotNull(v.hostId, v.status).joinToString(" · "),
                                    trailing = "Pending",
                                    showDivider = i < waiting.lastIndex,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                1 -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                ) {
                    AppleLargeTitle("Visitors")
                    AppleInset {
                        AppleCell("Inside now", subtitle = "Checkout verify", onClick = onCheckout)
                        AppleCell(
                            "Lost & Found",
                            glyph = "🎒",
                            glyphColor = KioskColors.systemPurple,
                            onClick = onLostFound,
                            showDivider = false,
                        )
                    }
                }
                2 -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    AppleLargeTitle("Log")
                    AppleInset {
                        AppleCell("Visit history", onClick = onHistory, showDivider = false)
                    }
                }
                else -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 16.dp),
                ) {
                    AppleLargeTitle("More")
                    AppearanceSegmentedRow()
                    AppleSectionHeader("Account")
                    AppleInset {
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
            StepperDots(active = 0, total = 4)
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
private fun WaitingRow(
    initials: String,
    title: String,
    subtitle: String,
    trailing: String,
    showDivider: Boolean,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleAvatar(initials = initials, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = KioskColors.text, fontSize = 17.sp, fontFamily = KioskFont)
                Text(subtitle, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
            }
            Text(trailing, color = KioskColors.textMuted, fontSize = 17.sp, fontFamily = KioskFont)
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
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
