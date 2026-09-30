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
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.SgSpacing
import com.satcop.smartvisitor.kiosk.ui.theme.SgType
import com.satcop.smartvisitor.kiosk.ui.theme.HeroShape
import com.satcop.smartvisitor.kiosk.ui.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Report
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import java.time.LocalTime

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
    BackHandler(enabled = state.view != HomeView.HOME) { controller.back() }

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
        modifier = Modifier.fillMaxSize().background(KioskColors.bg),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).statusBarsPadding()
                .padding(horizontal = SgSpacing.ScreenMargin, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
        ) {
            // Greeting row (picture 49)
            Column(Modifier.fillMaxWidth()) {
                Text(ClockInLogic.greeting(displayName, LocalTime.now()), color = KioskColors.text, style = SgType.Greeting.copy(fontSize = 22.sp, lineHeight = 28.sp))
                Text(subtitle.ifBlank { displayName.ifBlank { "Guard" } }, color = KioskColors.textMuted, style = SgType.Label.copy(fontWeight = FontWeight.Normal))
            }

            // Hero: Start patrol
            Row(
                Modifier.fillMaxWidth().clip(HeroShape).background(KioskColors.primary)
                    .clickable(role = Role.Button, onClick = onStartPatrol).padding(SgSpacing.CardPadding + 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(KioskColors.onPrimary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Security, contentDescription = null, tint = KioskColors.onPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Start patrol", color = KioskColors.onPrimary, style = SgType.ScreenTitle)
                    Text("$routeSubtitle · $progressLabel", color = KioskColors.onPrimary.copy(alpha = 0.85f), style = SgType.Label)
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(KioskColors.onPrimary.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = KioskColors.onPrimary)
                }
            }

            AttendanceCard(state = state, controller = controller)

            // Visitors today: count tiles
            Text("Visitors today", color = KioskColors.text, style = SgType.SectionTitle, modifier = Modifier.padding(top = 4.dp))
            val sum = state.summary
            Row(horizontalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards)) {
                CountTile(VisitFilter.TODAYS.label, "${sum.todays}", KioskColors.primary, Modifier.weight(1f)) { controller.openList(VisitFilter.TODAYS) }
                CountTile(VisitFilter.PENDING.label, "${sum.pending}", KioskColors.info, Modifier.weight(1f)) { controller.openList(VisitFilter.PENDING) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards)) {
                CountTile(VisitFilter.REJECTED.label, "${sum.rejected}", KioskColors.danger, Modifier.weight(1f)) { controller.openList(VisitFilter.REJECTED) }
                CountTile(VisitFilter.ALL.label, "${sum.all}", KioskColors.text, Modifier.weight(1f)) { controller.openList(VisitFilter.ALL) }
            }
            if (!state.visitsError.isNullOrBlank()) {
                GuardBanner("${state.visitsError} Pull down to try again.", GuardBannerKind.WARNING)
            }

            // Find visitor, Courier log, Lost & Found, Report incident
            GuardCard(Modifier.padding(top = 4.dp)) {
                NavRow(Icons.Outlined.Search, "Find visitor", "Search by name, phone or visitor code", onClick = controller::openFind)
                NavRow(Icons.Outlined.Inventory2, "Courier log", null, onClick = onCourier)
                NavRow(Icons.Outlined.Inventory2, "Lost & Found", null, onClick = onLostFound)
                NavRow(Icons.Outlined.Report, "Report incident", null, onClick = onReportIncident, divider = false)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CountTile(label: String, stat: String, statColor: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.sgCardSurface().clickable(role = Role.Button, onClick = onClick).padding(SgSpacing.CardPadding),
    ) {
        Text(label, color = KioskColors.textMuted, style = SgType.Label)
        Text(stat, color = statColor, style = SgType.BigNumber)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit, divider: Boolean = true) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GuardIconBadge(icon, size = 40.dp, bg = KioskColors.successSoft, tint = KioskColors.success)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = KioskColors.text, style = SgType.BodyStrong)
                if (subtitle != null) Text(subtitle, color = KioskColors.textMuted, style = SgType.Caption)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = KioskColors.textMuted)
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = KioskColors.border)
    }
}

/** Attendance panel: status card, server error + retry, Clock out (danger outline). Logic unchanged. */
@Composable
private fun AttendanceCard(state: GuardHomeState, controller: GuardHomeController) {
    val today = state.attendance
    val att = today?.state ?: AttendanceState.NONE
    GuardCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My attendance", color = KioskColors.textMuted, style = SgType.Label)
                Text(GuardHomeLogic.attendanceHeadline(today), color = KioskColors.text, style = SgType.SectionTitle)
                Text(GuardHomeLogic.attendanceDetail(today), color = KioskColors.textMuted, style = SgType.Label.copy(fontWeight = FontWeight.Normal))
            }
            Spacer(Modifier.width(8.dp))
            SgStatusChip(
                when (att) { AttendanceState.NONE -> "Off duty"; AttendanceState.PRESENT -> "On duty"; AttendanceState.CLOCKED_OUT -> "Checked out" },
                when (att) { AttendanceState.NONE -> SgStatusKind.NEUTRAL; AttendanceState.PRESENT -> SgStatusKind.IN_PROGRESS; AttendanceState.CLOCKED_OUT -> SgStatusKind.COMPLETED },
            )
        }
        if (!state.attendanceError.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            GuardBanner(state.attendanceError, GuardBannerKind.WARNING)
            Spacer(Modifier.height(8.dp))
            SgSmallPill("Try again", controller::refresh, style = SgPillStyle.SOFT)
        }
        when (att) {
            // 1064: no "Check in" button on Home. Check-in only happens on the lock screen (Self Check In).
            AttendanceState.NONE -> Unit
            AttendanceState.PRESENT -> if (today?.canClockOut != false) {
                Spacer(Modifier.height(12.dp))
                SgDangerOutlineButton("Clock out", { controller.openPanel(AttendanceMode.CLOCK_OUT) }, Modifier.fillMaxWidth())
            }
            // CLOCKED_OUT: no button at all. The state comes from the server, so this holds after an app restart.
            AttendanceState.CLOCKED_OUT -> Text(
                "You have clocked out for today.",
                color = KioskColors.textMuted, style = SgType.Label, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun SubTopBar(title: String, subtitle: String?, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        GuardTopBar(title, onBack)
        if (!subtitle.isNullOrBlank()) Text(subtitle, color = KioskColors.textMuted, style = SgType.Label, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun VisitListBody(state: GuardHomeState, controller: GuardHomeController) {
    val rows = GuardHomeLogic.filtered(state.visits, state.today, state.listFilter).map(GuardHomeLogic::row)
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        SubTopBar("Visitors", "Today · ${state.today}", onBack = { controller.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SgSpacing.ScreenMargin, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
        ) {
            val options = VisitFilter.entries
            SgFilterChipRow(
                options = options.map { "${it.label} · ${state.summary.countFor(it)}" },
                selectedIndex = options.indexOf(state.listFilter),
                onSelect = { controller.openList(options[it]) },
            )
            if (rows.isEmpty()) {
                GuardCard { Text("No visitors here yet", color = KioskColors.textMuted, style = SgType.Body) }
            } else {
                rows.forEach { r -> VisitRowCard(r) }
            }
        }
    }
}

@Composable
private fun FindBody(state: GuardHomeState, controller: GuardHomeController) {
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        SubTopBar("Find visitor", "Name, phone or visitor code", onBack = { controller.back() })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = SgSpacing.ScreenMargin, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
        ) {
            SgSearchPill(
                value = state.findQuery,
                onValueChange = controller::setFindQuery,
                placeholder = "Search",
                onSearch = controller::search,
            )
            SgPrimaryButton(if (state.findBusy) "Searching…" else "Search", { if (!state.findBusy) controller.search() }, Modifier.fillMaxWidth())
            val msg = state.findMessage
            if (!msg.isNullOrBlank()) {
                if (msg == GuardHomeLogic.NOT_PRESENT) {
                    Text(msg, color = KioskColors.text, style = SgType.SectionTitle, modifier = Modifier.padding(vertical = 8.dp))
                } else {
                    GuardBanner(msg, GuardBannerKind.WARNING)
                }
            }
            state.findResults.orEmpty().map(GuardHomeLogic::row).forEach { r -> VisitRowCard(r) }
        }
    }
}

private fun statusKind(status: String): SgStatusKind = when (status.lowercase()) {
    "pending" -> SgStatusKind.PENDING
    "approved" -> SgStatusKind.BRAND
    "inside" -> SgStatusKind.IN_PROGRESS
    "completed", "force_completed" -> SgStatusKind.COMPLETED
    "rejected" -> SgStatusKind.REJECTED
    else -> SgStatusKind.NEUTRAL
}

@Composable
private fun VisitRowCard(r: GuardVisitRow) {
    val meta = listOf(r.code, r.mobileMasked).filter { it.isNotBlank() }.joinToString(" · ")
    val line2 = listOf(r.purpose, r.hostLabel).filter { it.isNotBlank() }.joinToString(" · ")
    SgVisitCard(
        name = r.name,
        subtitle = listOf(meta, line2).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { null },
        timeText = r.timeLabel.ifBlank { null },
        status = { SgStatusChip(r.statusLabel, statusKind(r.status)) },
    )
    if (!r.rejectReason.isNullOrBlank()) {
        Text("Reason: ${r.rejectReason}", color = KioskColors.danger, style = SgType.Label, modifier = Modifier.padding(start = 4.dp))
    }
}
