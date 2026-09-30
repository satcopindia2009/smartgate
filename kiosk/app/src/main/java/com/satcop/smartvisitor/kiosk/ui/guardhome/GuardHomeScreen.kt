package com.satcop.smartvisitor.kiosk.ui.guardhome

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.satcop.smartvisitor.kiosk.data.model.AttendanceState
import com.satcop.smartvisitor.kiosk.ui.apple.AppleCell
import com.satcop.smartvisitor.kiosk.ui.apple.AppleGrid2
import com.satcop.smartvisitor.kiosk.ui.apple.AppleHeroCta
import com.satcop.smartvisitor.kiosk.ui.apple.AppleInset
import com.satcop.smartvisitor.kiosk.ui.apple.ApplePlainButton
import com.satcop.smartvisitor.kiosk.ui.apple.ApplePrimaryButton
import com.satcop.smartvisitor.kiosk.ui.apple.AppleSearchField
import com.satcop.smartvisitor.kiosk.ui.apple.AppleSectionHeader
import com.satcop.smartvisitor.kiosk.ui.apple.AppleSegmented
import com.satcop.smartvisitor.kiosk.ui.apple.AppleShellNav
import com.satcop.smartvisitor.kiosk.ui.apple.AppleShellSub
import com.satcop.smartvisitor.kiosk.ui.apple.AppleShellTitle
import com.satcop.smartvisitor.kiosk.ui.apple.AppleStatusPill
import com.satcop.smartvisitor.kiosk.ui.apple.AppleTile
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

/** Guard Home (Today tab): attendance, live visitor summary, Find Visitor, quick actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuardHomeScreen(
    controller: GuardHomeController,
    displayName: String,
    subtitle: String,
    routeSubtitle: String,
    progressLabel: String,
    onStartPatrol: () -> Unit,
    onCourier: () -> Unit,
    onLostFound: () -> Unit,
    onReportIncident: () -> Unit,
    onLogout: () -> Unit,
    onToast: (String) -> Unit,
) {
    val state by controller.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Fresh read every time the app returns to the foreground (no stale counts, no stale attendance).
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) controller.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    LaunchedEffect(state.toast) {
        state.toast?.let { onToast(it); controller.clearToast() }
    }
    BackHandler(enabled = state.panel != null || state.view != HomeView.HOME) { controller.back() }

    val panel = state.panel
    if (panel != null) {
        AttendancePanel(mode = panel, state = state, controller = controller)
        return
    }

    when (state.view) {
        HomeView.HOME -> HomeBody(
            state = state, controller = controller, displayName = displayName, subtitle = subtitle,
            routeSubtitle = routeSubtitle, progressLabel = progressLabel,
            onStartPatrol = onStartPatrol, onCourier = onCourier, onLostFound = onLostFound,
            onReportIncident = onReportIncident, onLogout = onLogout,
        )
        HomeView.LIST -> VisitListBody(state = state, controller = controller)
        HomeView.FIND -> FindBody(state = state, controller = controller)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeBody(
    state: GuardHomeState,
    controller: GuardHomeController,
    displayName: String,
    subtitle: String,
    routeSubtitle: String,
    progressLabel: String,
    onStartPatrol: () -> Unit,
    onCourier: () -> Unit,
    onLostFound: () -> Unit,
    onReportIncident: () -> Unit,
    onLogout: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.loading,
        onRefresh = controller::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AppleShellNav(leading = "Roles", trailing = " ", onLeading = onLogout)
            AppleShellTitle("Guard")
            AppleShellSub(subtitle.ifBlank { displayName.ifBlank { "Guard" } })

            AttendanceCard(state = state, controller = controller)

            AppleSectionHeader("Visitors today")
            val sum = state.summary
            AppleGrid2 {
                AppleTile(label = VisitFilter.TODAYS.label, stat = "${sum.todays}", onClick = { controller.openList(VisitFilter.TODAYS) })
                AppleTile(label = VisitFilter.PENDING.label, stat = "${sum.pending}", onClick = { controller.openList(VisitFilter.PENDING) })
            }
            Spacer(Modifier.height(10.dp))
            AppleGrid2 {
                AppleTile(label = VisitFilter.REJECTED.label, stat = "${sum.rejected}", onClick = { controller.openList(VisitFilter.REJECTED) })
                AppleTile(label = VisitFilter.ALL.label, stat = "${sum.all}", onClick = { controller.openList(VisitFilter.ALL) })
            }
            if (!state.visitsError.isNullOrBlank()) {
                Text(
                    "${state.visitsError} Pull down to try again.",
                    color = KioskColors.orange, fontSize = 13.sp, fontFamily = KioskFont,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(10.dp))
            AppleInset {
                AppleCell(
                    title = "Find Visitor",
                    subtitle = "Search by name, phone or visitor code",
                    glyph = "🔍",
                    showDivider = false,
                    onClick = controller::openFind,
                )
            }

            AppleSectionHeader("Quick actions")
            AppleHeroCta(title = "Start patrol", subtitle = "$routeSubtitle · $progressLabel", onClick = onStartPatrol)
            Spacer(Modifier.height(10.dp))
            AppleInset {
                AppleCell(title = "Courier log", onClick = onCourier, showDivider = true)
                AppleCell(title = "Lost & Found", onClick = onLostFound, showDivider = true)
                AppleCell(title = "Report incident", onClick = onReportIncident, showDivider = false)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AttendanceCard(state: GuardHomeState, controller: GuardHomeController) {
    val today = state.attendance
    AppleSectionHeader("My attendance")
    AppleInset {
        AppleCell(
            title = GuardHomeLogic.attendanceHeadline(today),
            subtitle = GuardHomeLogic.attendanceDetail(today),
            showChevron = false,
            showDivider = false,
        )
    }
    if (!state.attendanceError.isNullOrBlank()) {
        Text(
            state.attendanceError,
            color = KioskColors.orange, fontSize = 13.sp, fontFamily = KioskFont,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        ApplePlainButton("Try again", onClick = controller::refresh)
    }
    when (today?.state ?: AttendanceState.NONE) {
        AttendanceState.NONE -> if (today == null || today.canCheckIn) {
            Spacer(Modifier.height(8.dp))
            ApplePrimaryButton("Check in") { controller.openPanel(AttendanceMode.CHECK_IN) }
        }
        AttendanceState.PRESENT -> if (today?.canClockOut != false) {
            Spacer(Modifier.height(8.dp))
            ApplePrimaryButton("Clock out") { controller.openPanel(AttendanceMode.CLOCK_OUT) }
        }
        // CLOCKED_OUT: no button at all. The state comes from the server, so this holds after an app restart.
        AttendanceState.CLOCKED_OUT -> Text(
            "You have clocked out for today.",
            color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun VisitListBody(state: GuardHomeState, controller: GuardHomeController) {
    val rows = GuardHomeLogic.filtered(state.visits, state.today, state.listFilter).map(GuardHomeLogic::row)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppleShellNav(leading = "Back", trailing = " ", onLeading = { controller.back() })
        AppleShellTitle("Visitors")
        AppleShellSub("Today · ${state.today}")
        val options = VisitFilter.entries
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
            // Large text: a 4-way segmented control would clip labels, so show a plain list with a tick.
            AppleInset {
                options.forEachIndexed { i, f ->
                    AppleCell(
                        title = f.label,
                        trailing = "${state.summary.countFor(f)}" + if (f == state.listFilter) "  ✓" else "",
                        showChevron = false,
                        showDivider = i < options.lastIndex,
                        onClick = { controller.openList(f) },
                    )
                }
            }
        } else {
            AppleSegmented(
                options = options.map { it.label },
                selectedIndex = options.indexOf(state.listFilter),
                onSelect = { controller.openList(options[it]) },
            )
        }
        Spacer(Modifier.height(10.dp))
        if (rows.isEmpty()) {
            AppleInset { AppleCell("No visitors here yet", showChevron = false, showDivider = false) }
        } else {
            AppleInset {
                rows.forEachIndexed { i, r -> VisitRowCell(r, showDivider = i < rows.lastIndex) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun FindBody(state: GuardHomeState, controller: GuardHomeController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        AppleShellNav(leading = "Back", trailing = " ", onLeading = { controller.back() })
        AppleShellTitle("Find Visitor")
        AppleShellSub("Name, phone or visitor code")
        AppleSearchField(
            value = state.findQuery,
            onValueChange = controller::setFindQuery,
            placeholder = "Search",
            onSearch = controller::search,
        )
        Spacer(Modifier.height(10.dp))
        ApplePrimaryButton(if (state.findBusy) "Searching…" else "Search") {
            if (!state.findBusy) controller.search()
        }
        Spacer(Modifier.height(10.dp))
        val msg = state.findMessage
        if (!msg.isNullOrBlank()) {
            Text(
                msg,
                color = if (msg == GuardHomeLogic.NOT_PRESENT) KioskColors.text else KioskColors.orange,
                fontSize = 17.sp, fontFamily = KioskFont, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val results = state.findResults.orEmpty().map(GuardHomeLogic::row)
        if (results.isNotEmpty()) {
            AppleInset {
                results.forEachIndexed { i, r -> VisitRowCell(r, showDivider = i < results.lastIndex) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun VisitRowCell(r: GuardVisitRow, showDivider: Boolean) {
    Column {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    r.name, color = KioskColors.text, fontSize = 17.sp, fontFamily = KioskFont,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(4.dp))
                AppleStatusPill(r.statusLabel)
            }
            val meta = listOf(r.code, r.mobileMasked).filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotBlank()) Text(meta, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
            val line2 = listOf(r.purpose, r.hostLabel, r.timeLabel).filter { it.isNotBlank() }.joinToString(" · ")
            if (line2.isNotBlank()) Text(line2, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
            if (!r.rejectReason.isNullOrBlank()) {
                Text(
                    "Reason: ${r.rejectReason}",
                    color = KioskColors.red, fontSize = 13.sp, fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (showDivider) HorizontalDivider(modifier = Modifier.padding(start = 16.dp), thickness = 0.33.dp, color = KioskColors.border)
    }
}
