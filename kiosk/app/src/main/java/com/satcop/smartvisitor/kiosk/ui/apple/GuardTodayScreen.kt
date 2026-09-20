package com.satcop.smartvisitor.kiosk.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors

/** AC-APP1 bottomnav SoT: Today · Patrol · Desk · More */
private val guardTabs = listOf(
    AppleTabItem("📅", "Today"),
    AppleTabItem("🛡", "Patrol"),
    AppleTabItem("📦", "Desk"),
    AppleTabItem("⋯", "More"),
)

/**
 * Guard shell from phone-apple-bottomnav (guard-home / patrol / desk).
 * Start patrol CTA above fold · assign→perform via Patrol tab.
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
    val route = routeLabel.ifBlank { "Assigned today" }
    val sub = listOf(
        displayName.ifBlank { "Guard" },
        schoolName.ifBlank { "Shift" },
    ).filter { it.isNotBlank() }.joinToString(" · ")

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
                0 -> { // Today — guard-home.png 1:1
                    AppleShellNav(leading = "Roles", trailing = "🔔", onLeading = onLogout)
                    AppleShellTitle("Guard")
                    AppleShellSub(sub.ifBlank { statusLine.ifBlank { "Shift" } })
                    AppleHeroCta(
                        title = "Start patrol",
                        subtitle = "$route · $done/$total",
                        onClick = {
                            tab = 1
                            onContinuePatrol()
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    AppleGrid2 {
                        AppleTile(label = "Progress", stat = "$done/$total")
                        AppleTile(
                            icon = "⚠",
                            label = "Incidents",
                            detail = "Open —",
                            onClick = onReportIncident,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    AppleInset {
                        AppleCell(title = "Courier log", onClick = onCourier, showDivider = true)
                        AppleCell(title = "Lost & Found", onClick = onLostFound, showDivider = false)
                    }
                }
                1 -> {
                    // Live assign→perform wire fully reachable
                    patrolContent()
                }
                2 -> { // Desk — guard-desk.png
                    AppleShellNav(leading = " ", trailing = " ")
                    AppleShellTitle("Desk")
                    AppleShellSub("Primary desk actions only")
                    AppleHeroCta(
                        title = "Log courier",
                        subtitle = "Parcel at gate",
                        onClick = onCourier,
                    )
                    Spacer(Modifier.height(10.dp))
                    AppleHeroCta(
                        title = "Lost & Found",
                        subtitle = "Lost or Found + photo",
                        onClick = onLostFound,
                        filled = false,
                    )
                    Spacer(Modifier.height(10.dp))
                    AppleInset {
                        AppleCell(
                            title = "Report incident",
                            onClick = onReportIncident,
                            showDivider = false,
                        )
                    }
                }
                else -> { // More
                    AppleShellNav(leading = " ", trailing = " ")
                    AppleShellTitle("More")
                    AppearanceSegmentedRow()
                    AppleSectionHeader("Account")
                    AppleInset {
                        AppleCell(
                            "Sign out",
                            showChevron = false,
                            showDivider = false,
                            onClick = onLogout,
                        )
                    }
                }
            }
        }
        AppleTabBar(tabs = guardTabs, selectedIndex = tab, onSelect = { tab = it })
    }
}
