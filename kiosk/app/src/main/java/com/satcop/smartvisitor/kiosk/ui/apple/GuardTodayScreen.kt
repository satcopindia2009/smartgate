package com.satcop.smartvisitor.kiosk.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

private val guardTabs = listOf(
    AppleTabItem("📅", "Today"),
    AppleTabItem("🛡", "Patrol"),
    AppleTabItem("📦", "Desk"),
    AppleTabItem("⋯", "More"),
)

/**
 * Apple Guard "Today" shell. Patrol tab / Continue patrol keep assign→perform
 * (my-schedules / assignmentId start) reachable via [patrolContent].
 */
@Composable
fun GuardTodayShell(
    displayName: String,
    schoolName: String,
    statusLine: String,
    assignmentCount: Int,
    progressDone: Int,
    progressTotal: Int,
    routeLabel: String,
    nextCheckpoint: String?,
    onContinuePatrol: () -> Unit,
    onCourier: () -> Unit,
    onLostFound: () -> Unit,
    onReportIncident: () -> Unit,
    onLogout: () -> Unit = {},
    /** Assigned-today perform UI (existing StartRoundScreen content). */
    patrolContent: @Composable () -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    val total = progressTotal.coerceAtLeast(assignmentCount).coerceAtLeast(0)
    val done = progressDone.coerceIn(0, total.coerceAtLeast(0))

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
                    AppleNavBar(leading = "Account", trailing = "🔔", onLeading = onLogout)
                    AppleLargeTitle("Today")
                    Text(
                        text = listOf(displayName.ifBlank { "Guard" }, schoolName.ifBlank { "Shift" })
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        color = KioskColors.textMuted,
                        fontSize = 13.sp,
                        fontFamily = KioskFont,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 4.dp),
                    )
                    AppleInset {
                        AppleCell(
                            title = "Patrol progress",
                            subtitle = routeLabel.ifBlank { "Assigned today" },
                            trailing = "$done/$total",
                            showChevron = false,
                            showDivider = true,
                        )
                        AppleCell(
                            title = "Open incidents",
                            trailing = "—",
                            showChevron = false,
                            showDivider = true,
                        )
                        AppleCell(
                            title = "Couriers logged",
                            trailing = "—",
                            showChevron = false,
                            showDivider = false,
                        )
                    }
                    // Accent the progress trailing with system blue via a second label strip
                    Text(
                        text = statusLine,
                        color = if (statusLine.contains("LIVE", ignoreCase = true)) {
                            KioskColors.systemGreen
                        } else {
                            KioskColors.systemOrange
                        },
                        fontSize = 12.sp,
                        fontFamily = KioskFont,
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                    )
                    AppleSectionHeader("Tasks")
                    AppleInset {
                        AppleCell(
                            title = "Continue patrol",
                            subtitle = nextCheckpoint?.let { "Next: $it" }
                                ?: if (assignmentCount > 0) "$assignmentCount assigned" else "No assignment yet",
                            glyph = "🛡",
                            glyphColor = KioskColors.systemGreen,
                            onClick = {
                                tab = 1
                                onContinuePatrol()
                            },
                        )
                        AppleCell(
                            title = "Log courier",
                            glyph = "📦",
                            glyphColor = KioskColors.systemOrange,
                            onClick = onCourier,
                        )
                        AppleCell(
                            title = "Lost & Found entry",
                            glyph = "🎒",
                            glyphColor = KioskColors.systemPurple,
                            onClick = onLostFound,
                        )
                        AppleCell(
                            title = "Report incident",
                            glyph = "⚠",
                            glyphColor = KioskColors.systemRed,
                            showDivider = false,
                            onClick = onReportIncident,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                1 -> {
                    // Keep live assign→perform wire fully reachable
                    patrolContent()
                }
                2 -> Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                ) {
                    AppleLargeTitle("Desk")
                    AppleInset {
                        AppleCell(
                            title = "Log courier",
                            glyph = "📦",
                            glyphColor = KioskColors.systemOrange,
                            onClick = onCourier,
                        )
                        AppleCell(
                            title = "Lost & Found",
                            glyph = "🎒",
                            glyphColor = KioskColors.systemPurple,
                            onClick = onLostFound,
                            showDivider = false,
                        )
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
        AppleTabBar(tabs = guardTabs, selectedIndex = tab, onSelect = { tab = it })
    }
}
