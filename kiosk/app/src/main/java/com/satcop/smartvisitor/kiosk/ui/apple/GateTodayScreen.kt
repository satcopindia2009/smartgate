package com.satcop.smartvisitor.kiosk.ui.apple

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.data.model.GuardHistoryEvent
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.ui.components.SgDangerOutlineButton
import com.satcop.smartvisitor.kiosk.ui.components.SgFilterChipRow
import com.satcop.smartvisitor.kiosk.ui.components.SgNavSets
import com.satcop.smartvisitor.kiosk.ui.components.SgPillStyle
import com.satcop.smartvisitor.kiosk.ui.components.SgSearchPill
import com.satcop.smartvisitor.kiosk.ui.components.SgSmallPill
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusChip
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusKind
import com.satcop.smartvisitor.kiosk.ui.components.SgVisitCard
import com.satcop.smartvisitor.kiosk.ui.components.sgCardSurface
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.SgSpacing
import com.satcop.smartvisitor.kiosk.ui.theme.SgType
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val IST: ZoneId = ZoneId.of("Asia/Calcutta")

/**
 * Gate shell (teal restyle): Home · Inside · Log · Profile with the raised centre "Add visitor" button
 * ([SgBottomNav] via [AppleTabBar]). The bar is hidden while the Add Visitor steps are shown.
 *
 * All extra parameters are optional with defaults so existing callers keep compiling; they only feed
 * read-only lists and forward user taps to the existing callbacks.
 */
@Composable
fun GateTodayScreen(
    gateName: String,
    recent: List<InsideVisit>,
    onAddVisitor: () -> Unit,
    onCourier: () -> Unit,
    onHistory: () -> Unit,
    onCheckout: () -> Unit,
    onLostFound: () -> Unit,
    onPickup: () -> Unit,
    onLogout: () -> Unit,
    /** When >1 the Add Visitor steps are shown full screen (bottom nav hidden). */
    registrationStep: Int = 1,
    registrationContent: (@Composable () -> Unit)? = null,
    displayName: String = "",
    /** Everyone currently inside (full list). Falls back to [recent] when empty. */
    insideList: List<InsideVisit> = emptyList(),
    /** Recent gate history rows (all statuses) for Pending and Log. */
    historyEvents: List<GuardHistoryEvent> = emptyList(),
    hostNames: Map<String, String> = emptyMap(),
    onRefreshLists: () -> Unit = {},
    onSelectHistory: (GuardHistoryEvent) -> Unit = {},
    /** Check out one visit by its id (select + confirm). Shown only for visits that are inside. */
    onCheckoutVisit: ((String) -> Unit)? = null,
) {
    var tab by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var insideFilter by remember { mutableIntStateOf(0) }
    var logFilter by remember { mutableIntStateOf(0) }
    var confirmOut by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) { onRefreshLists() }

    val insideRows = insideList.ifEmpty { recent }
    val fullScreenStep = registrationStep > 1 && registrationContent != null

    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        Column(Modifier.weight(1f).fillMaxWidth()) {
            if (fullScreenStep) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { registrationContent!!() }
            } else when (tab) {
                0 -> GateHome(
                    gateName = gateName, displayName = displayName, search = search, onSearch = { search = it },
                    inside = insideRows, events = historyEvents, hostNames = hostNames,
                    onAddVisitor = onAddVisitor, onPickup = onPickup, onCourier = onCourier,
                    onSeeAll = { logFilter = 0; search = ""; tab = 2 },
                    onProfile = { tab = 3 }, onSelectHistory = onSelectHistory,
                )
                1 -> GateInside(
                    rows = insideRows, hostNames = hostNames, search = search, onSearch = { search = it },
                    filter = insideFilter, onFilter = { insideFilter = it },
                    canCheckOut = onCheckoutVisit != null,
                    onCheckOut = { id, name -> confirmOut = id to name },
                )
                2 -> GateLog(
                    events = historyEvents, insideIds = insideRows.map { it.id }.toSet(), search = search, onSearch = { search = it },
                    filter = logFilter, onFilter = { logFilter = it },
                    canCheckOut = onCheckoutVisit != null,
                    onCheckOut = { id, name -> confirmOut = id to name },
                    onSelect = onSelectHistory,
                )
                else -> GateProfile(
                    displayName = displayName, gateName = gateName,
                    onLostFound = onLostFound, onLogout = onLogout,
                )
            }
        }
        AppleTabBar(
            tabs = gateTabs,
            selectedIndex = tab,
            onSelect = { tab = it; search = "" },
            centerLabel = "Add visitor",
            onCenter = onAddVisitor,
            visible = !fullScreenStep,
        )
    }

    confirmOut?.let { (id, name) ->
        AlertDialog(
            onDismissRequest = { confirmOut = null },
            title = { Text("Check out $name?", style = SgType.SectionTitle, color = KioskColors.text) },
            confirmButton = {
                TextButton(onClick = { confirmOut = null; onCheckoutVisit?.invoke(id) }) {
                    Text("Check out", color = KioskColors.primary, style = SgType.Button)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmOut = null }) {
                    Text("Cancel", color = KioskColors.textMuted, style = SgType.Button)
                }
            },
            containerColor = KioskColors.card,
        )
    }
}

private val gateTabs = SgNavSets.Gate.map { AppleTabItem(it.label, it.outlined) }

// ───────────────────────── Home ─────────────────────────

@Composable
private fun GateHome(
    gateName: String,
    displayName: String,
    search: String,
    onSearch: (String) -> Unit,
    inside: List<InsideVisit>,
    events: List<GuardHistoryEvent>,
    hostNames: Map<String, String>,
    onAddVisitor: () -> Unit,
    onPickup: () -> Unit,
    onCourier: () -> Unit,
    onSeeAll: () -> Unit,
    onProfile: () -> Unit,
    onSelectHistory: (GuardHistoryEvent) -> Unit,
) {
    val pending = events.filter { isVisit(it) && statusKey(it.status) == "pending" }
    val q = search.trim()
    val waiting = pending.filter { q.isEmpty() || it.title.contains(q, true) || it.subtitle.orEmpty().contains(q, true) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = SgSpacing.ScreenMargin).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val who = displayName.trim().split(" ").firstOrNull().orEmpty()
                Text(
                    if (who.isBlank()) greeting() else "${greeting()}, $who",
                    style = SgType.Greeting, color = KioskColors.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (gateName.isNotBlank()) Text(gateName, style = SgType.Caption, color = KioskColors.textMuted)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(KioskColors.brandSoft)
                    .clickable(role = Role.Button, onClick = onProfile),
                contentAlignment = Alignment.Center,
            ) { Text(initialsOf(displayName), style = SgType.BodyStrong, color = KioskColors.primary) }
        }
        SgSearchPill(value = search, onValueChange = onSearch, placeholder = "Search visitor or host")
        HeroAddVisitor(onAddVisitor)
        Row(horizontalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards)) {
            CountTile("Inside now", inside.size.toString(), KioskColors.warning, Modifier.weight(1f))
            CountTile("Pending approval", pending.size.toString(), KioskColors.info, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards)) {
            QuickTile(Icons.Outlined.Groups, "Pickup", "Student release", onPickup, Modifier.weight(1f))
            QuickTile(Icons.Outlined.LocalShipping, "Courier", "Log parcel", onCourier, Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Waiting for host", style = SgType.SectionTitle, color = KioskColors.text, modifier = Modifier.weight(1f))
            if (pending.isNotEmpty()) {
                Text(
                    "See all", style = SgType.Label, color = KioskColors.primary,
                    modifier = Modifier.clickable(role = Role.Button, onClick = onSeeAll).padding(vertical = 8.dp),
                )
            }
        }
        if (waiting.isEmpty()) {
            Text("No visitors waiting", style = SgType.Body, color = KioskColors.textMuted)
        } else {
            waiting.take(3).forEach { ev -> EventCard(ev, hostNames, onClick = { onSelectHistory(ev) }) }
        }
    }
}

@Composable
private fun HeroAddVisitor(onClick: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.horizontalGradient(listOf(KioskColors.primaryPressed, KioskColors.primary)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(SgSpacing.CardPadding + 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(KioskColors.onPrimary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Add, contentDescription = null, tint = KioskColors.onPrimary, modifier = Modifier.size(28.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Add visitor", style = SgType.ScreenTitle, color = KioskColors.onPrimary)
            Text("Walk-in visitor or vendor", style = SgType.Caption, color = KioskColors.onPrimary.copy(alpha = 0.9f))
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(KioskColors.onPrimary.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = KioskColors.onPrimary) }
    }
}

@Composable
private fun CountTile(label: String, value: String, valueColor: Color, modifier: Modifier) {
    Column(modifier.sgCardSurface().padding(SgSpacing.CardPadding)) {
        Text(label, style = SgType.Caption, color = KioskColors.textMuted, maxLines = 1)
        Text(value, style = SgType.BigNumber, color = valueColor)
    }
}

@Composable
private fun QuickTile(icon: ImageVector, title: String, sub: String, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.sgCardSurface().clickable(role = Role.Button, onClick = onClick).padding(SgSpacing.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(KioskColors.brandSoft),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = KioskColors.primary, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = SgType.BodyStrong, color = KioskColors.text, maxLines = 1)
            Text(sub, style = SgType.Caption, color = KioskColors.textMuted, maxLines = 1)
        }
    }
}

// ───────────────────────── Inside ─────────────────────────

@Composable
private fun GateInside(
    rows: List<InsideVisit>,
    hostNames: Map<String, String>,
    search: String,
    onSearch: (String) -> Unit,
    filter: Int,
    onFilter: (Int) -> Unit,
    canCheckOut: Boolean,
    onCheckOut: (String, String) -> Unit,
) {
    val q = search.trim()
    val shown = rows.filter { v ->
        val vendor = v.visitorType.equals("vendor", true)
        (filter == 0 || (filter == 1 && !vendor) || (filter == 2 && vendor)) &&
            (q.isEmpty() || (v.visitorName ?: "").contains(q, true) || (hostNames[v.hostId] ?: "").contains(q, true))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = SgSpacing.ScreenMargin).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
    ) {
        Text("Inside now · ${rows.size}", style = SgType.ScreenTitle, color = KioskColors.text)
        SgSearchPill(value = search, onValueChange = onSearch, placeholder = "Search name or company")
        SgFilterChipRow(listOf("All", "Visitors", "Vendors"), filter, onFilter)
        if (shown.isEmpty()) {
            Text("No one inside", style = SgType.Body, color = KioskColors.textMuted)
        }
        shown.forEach { v ->
            val name = v.visitorName ?: "Visitor"
            val vendor = v.visitorType.equals("vendor", true)
            SgVisitCard(
                name = name,
                initials = initialsOf(name),
                subtitle = hostNames[v.hostId],
                timeText = whenText(v.timeIn),
                status = { SgStatusChip("Checked in", SgStatusKind.IN_PROGRESS) },
                extraChip = if (vendor) ({ SgStatusChip("Vendor", SgStatusKind.BRAND) }) else null,
                actions = if (canCheckOut) ({
                    SgSmallPill("Check out", onClick = { onCheckOut(v.id, name) }, modifier = Modifier.weight(1f))
                }) else null,
            )
        }
    }
}

// ───────────────────────── Log ─────────────────────────

private val logFilters = listOf("Pending approval", "Approved", "Checked in", "Checked out", "Rejected")
private val logKeys = listOf("pending", "approved", "inside", "completed", "rejected")

@Composable
private fun GateLog(
    events: List<GuardHistoryEvent>,
    insideIds: Set<String>,
    search: String,
    onSearch: (String) -> Unit,
    filter: Int,
    onFilter: (Int) -> Unit,
    canCheckOut: Boolean,
    onCheckOut: (String, String) -> Unit,
    onSelect: (GuardHistoryEvent) -> Unit,
) {
    val key = logKeys[filter]
    val q = search.trim()
    val shown = events.filter { ev ->
        isVisit(ev) &&
            statusKey(ev.status) == key &&
            // Vendors have no approval flow: only in / out rows.
            (!ev.kind.equals("vendor", true) || key == "inside" || key == "completed") &&
            (q.isEmpty() || ev.title.contains(q, true) || ev.company.orEmpty().contains(q, true))
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = SgSpacing.ScreenMargin).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
    ) {
        Text("Visit log", style = SgType.ScreenTitle, color = KioskColors.text)
        SgSearchPill(value = search, onValueChange = onSearch, placeholder = "Search name or company")
        SgFilterChipRow(logFilters, filter, onFilter)
        if (shown.isEmpty()) {
            Text("No entries for this filter", style = SgType.Body, color = KioskColors.textMuted)
        }
        shown.forEach { ev ->
            EventCard(
                ev, emptyMap(), onClick = { onSelect(ev) },
                checkOut = if (canCheckOut && key == "inside" && ev.id in insideIds) ({ onCheckOut(ev.id, ev.title) }) else null,
            )
        }
    }
}

@Composable
private fun EventCard(
    ev: GuardHistoryEvent,
    hostNames: Map<String, String>,
    onClick: () -> Unit,
    checkOut: (() -> Unit)? = null,
) {
    val key = statusKey(ev.status)
    val vendor = ev.kind.equals("vendor", true)
    val sub = ev.company?.takeIf { it.isNotBlank() }
        ?: ev.subtitle?.substringAfter(" · ", ev.subtitle)?.takeIf { it.isNotBlank() && !it.equals(ev.status, true) }
    SgVisitCard(
        name = ev.title,
        initials = initialsOf(ev.title),
        subtitle = sub,
        timeText = whenText(ev.occurredAt),
        onClick = onClick,
        status = { SgStatusChip(statusLabel(key, ev.status), statusKind(key)) },
        extraChip = if (vendor) ({ SgStatusChip("Vendor", SgStatusKind.BRAND) }) else null,
        actions = if (checkOut != null) ({ SgSmallPill("Check out", onClick = checkOut, modifier = Modifier.weight(1f)) }) else null,
    )
}

// ───────────────────────── Profile ─────────────────────────

@Composable
private fun GateProfile(displayName: String, gateName: String, onLostFound: () -> Unit, onLogout: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = SgSpacing.ScreenMargin).padding(top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
    ) {
        Text("Profile", style = SgType.ScreenTitle, color = KioskColors.text)
        Row(
            Modifier.fillMaxWidth().sgCardSurface().padding(SgSpacing.CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(KioskColors.brandSoft),
                contentAlignment = Alignment.Center,
            ) { Text(initialsOf(displayName), style = SgType.SectionTitle, color = KioskColors.primary) }
            Spacer(Modifier.width(14.dp))
            Column {
                if (displayName.isNotBlank()) Text(displayName, style = SgType.SectionTitle, color = KioskColors.text)
                Text("Gate" + if (gateName.isNotBlank()) " · $gateName" else "", style = SgType.Caption, color = KioskColors.textMuted)
            }
        }
        AppearanceSegmentedRow()
        com.satcop.smartvisitor.kiosk.ui.otp.StaffVerifyEntry()
        Column(Modifier.fillMaxWidth().sgCardSurface()) {
            AppleCell("Lost & Found", onClick = onLostFound, showDivider = false)
        }
        SgDangerOutlineButton("Sign out", onClick = onLogout, modifier = Modifier.fillMaxWidth())
    }
}

// ───────────────────────── helpers ─────────────────────────

private fun isVisit(ev: GuardHistoryEvent) =
    ev.kind.equals("visitor", true) || ev.kind.equals("vendor", true) || ev.kind.equals("visit", true)

/** Normalises server status strings onto the five log buckets. */
private fun statusKey(status: String): String = when (status.trim().lowercase(Locale.ROOT).replace(' ', '_')) {
    "pending", "waiting", "pending_approval" -> "pending"
    "approved" -> "approved"
    "inside", "checked_in" -> "inside"
    "completed", "checked_out", "force_completed" -> "completed"
    "rejected" -> "rejected"
    else -> status.trim().lowercase(Locale.ROOT)
}

private fun statusLabel(key: String, raw: String) = when (key) {
    "pending" -> "Pending approval"
    "approved" -> "Approved"
    "inside" -> "Checked in"
    "completed" -> "Checked out"
    "rejected" -> "Rejected"
    else -> raw.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun statusKind(key: String) = when (key) {
    "pending" -> SgStatusKind.PENDING
    "approved" -> SgStatusKind.COMPLETED
    "inside" -> SgStatusKind.IN_PROGRESS
    "rejected" -> SgStatusKind.REJECTED
    else -> SgStatusKind.NEUTRAL
}

internal fun initialsOf(name: String): String =
    name.trim().split(" ").filter { it.isNotBlank() }.mapNotNull { it.firstOrNull()?.uppercase() }.take(2)
        .joinToString("").ifBlank { "•" }

private fun greeting(): String {
    val h = ZonedDateTime.now(IST).hour
    return when {
        h < 12 -> "Good Morning"
        h < 17 -> "Good Afternoon"
        else -> "Good Evening"
    }
}

/** "Today, 09:50 am" / "30 Sep, 09:50 am" in IST; null when there is no timestamp. Unparseable text is shown as given. */
internal fun whenText(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val zoned: ZonedDateTime = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(IST) }.getOrNull()
        ?: runCatching { LocalDateTime.parse(iso).atZone(IST) }.getOrNull()
        ?: return iso
    val time = zoned.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)).lowercase(Locale.ENGLISH)
    val today = ZonedDateTime.now(IST).toLocalDate()
    return if (zoned.toLocalDate() == today) "Today, $time"
    else zoned.format(DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)) + ", " + time
}
