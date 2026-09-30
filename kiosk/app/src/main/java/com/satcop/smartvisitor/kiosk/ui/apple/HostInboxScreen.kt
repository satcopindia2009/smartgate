package com.satcop.smartvisitor.kiosk.ui.apple

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.data.model.AfterHoursCopy
import com.satcop.smartvisitor.kiosk.data.model.RejectReasons
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.ui.components.SgDangerOutlineButton
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgNavSets
import com.satcop.smartvisitor.kiosk.ui.components.SgPillStyle
import com.satcop.smartvisitor.kiosk.ui.components.SgSmallPill
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusChip
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusKind
import com.satcop.smartvisitor.kiosk.ui.components.SgVisitCard
import com.satcop.smartvisitor.kiosk.ui.components.sgCardSurface
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.SgSpacing
import com.satcop.smartvisitor.kiosk.ui.theme.SgType

/** Host tabs: Inbox · Done · Inside · Profile (4 tabs, no centre button). */
private val hostTabs = SgNavSets.Host.map { AppleTabItem(it.label, it.outlined) }

/**
 * Host shell (teal restyle): Inbox with visit cards (Reject outline + Approve solid pills),
 * Done, Inside, Profile. Same parameters and callbacks as before.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HostInboxScreen(
    displayName: String,
    schoolId: String,
    staffId: String,
    pending: List<VisitOut>,
    active: List<VisitOut>,
    photos: Map<String, Bitmap>,
    afterHours: Boolean,
    busy: Boolean,
    rejectingVisitId: String?,
    rejectReason: String?,
    onRefresh: () -> Unit,
    onApprove: (String) -> Unit,
    onStartReject: (String) -> Unit,
    onPickRejectReason: (String) -> Unit,
    onCancelReject: () -> Unit,
    onConfirmReject: () -> Unit,
    onMeetingDone: (String) -> Unit,
    onShowAfterHours: () -> Unit,
    showingAfterHours: Boolean,
    onLogout: () -> Unit = {},
    /** Pull-to-refresh spinner state + handler (silent list reload, no toast spam). */
    refreshing: Boolean = false,
    onPullRefresh: () -> Unit = onRefresh,
    /** Visit to show first in the Inbox (tapped system notification). */
    focusVisitId: String? = null,
    /** Persistent message for a failed Approve (after hours etc.). */
    notice: String? = null,
    onDismissNotice: () -> Unit = {},
    gateNames: Map<String, String> = emptyMap(),
) {
    var tab by remember { mutableIntStateOf(0) }
    var detailId by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(focusVisitId) { if (focusVisitId != null) tab = 0 }
    val detailVisit = detailId?.let { id -> pending.firstOrNull { it.id == id } ?: active.firstOrNull { it.id == id } }
    androidx.activity.compose.BackHandler(enabled = detailVisit != null) { detailId = null; onCancelReject() }
    val ordered = if (focusVisitId == null) pending
    else pending.sortedByDescending { it.id == focusVisitId }

    if (detailVisit != null) {
        HostVisitDetail(
            visit = detailVisit,
            photo = photos[detailVisit.id],
            hostName = displayName,
            isPending = pending.any { it.id == detailVisit.id },
            busy = busy,
            rejecting = rejectingVisitId == detailVisit.id,
            rejectReason = rejectReason,
            onBack = { detailId = null; onCancelReject(); onDismissNotice() },
            notice = notice,
            gateName = detailVisit.gateId?.let { gateNames[it] },
            onApprove = onApprove,
            onStartReject = onStartReject,
            onPickRejectReason = onPickRejectReason,
            onCancelReject = onCancelReject,
            onConfirmReject = onConfirmReject,
        )
        return
    }
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = onPullRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = SgSpacing.ScreenMargin).padding(top = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
            ) {
                when (tab) {
                    0 -> {
                        Text("Needs you · ${pending.size}", style = SgType.ScreenTitle, color = KioskColors.text)
                        if (notice != null) NoticeBanner(notice)
                        if (afterHours) Text(AfterHoursCopy.HOST_NO_OP, style = SgType.Label, color = KioskColors.warning)
                        if (ordered.isEmpty()) {
                            Text("No pending visits", style = SgType.Body, color = KioskColors.textMuted)
                        }
                        ordered.forEach { visit ->
                            InboxCard(
                                visit = visit,
                                photo = photos[visit.id],
                                busy = busy,
                                rejecting = rejectingVisitId == visit.id,
                                rejectReason = rejectReason,
                                onApprove = onApprove,
                                onStartReject = onStartReject,
                                onPickRejectReason = onPickRejectReason,
                                onCancelReject = onCancelReject,
                                onConfirmReject = onConfirmReject,
                                onOpen = { detailId = visit.id },
                            )
                        }
                    }
                    1 -> {
                        Text("Done", style = SgType.ScreenTitle, color = KioskColors.text)
                        if (active.isEmpty()) Text("None yet", style = SgType.Body, color = KioskColors.textMuted)
                        active.forEach { visit ->
                            HostVisitCard(
                                visit, photos[visit.id],
                                chip = { SgStatusChip("Approved", SgStatusKind.COMPLETED) },
                                onClick = { if (!busy) onMeetingDone(visit.id) },
                            )
                        }
                        if (showingAfterHours) Text(AfterHoursCopy.HOST_NO_OP, style = SgType.Body, color = KioskColors.textMuted)
                    }
                    2 -> {
                        Text("Inside · ${active.size}", style = SgType.ScreenTitle, color = KioskColors.text)
                        if (active.isEmpty()) Text("None right now", style = SgType.Body, color = KioskColors.textMuted)
                        active.forEach { visit ->
                            HostVisitCard(
                                visit, photos[visit.id],
                                chip = { SgStatusChip("With you", SgStatusKind.IN_PROGRESS) },
                                actions = { SgSmallPill("Done", onClick = { onMeetingDone(visit.id) }, enabled = !busy, modifier = Modifier.weight(1f)) },
                            )
                        }
                    }
                    else -> {
                        Text("Profile", style = SgType.ScreenTitle, color = KioskColors.text)
                        val identity = listOf(displayName, schoolId, staffId).filter { it.isNotBlank() }.joinToString(" · ")
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
                                Text(identity.ifBlank { "Host" }, style = SgType.SectionTitle, color = KioskColors.text)
                                Text("Host", style = SgType.Caption, color = KioskColors.textMuted)
                            }
                        }
                        AppearanceSegmentedRow()
                        com.satcop.smartvisitor.kiosk.ui.otp.StaffVerifyEntry()
                        Column(Modifier.fillMaxWidth().sgCardSurface()) {
                            AppleCell(title = "After-hours info", onClick = onShowAfterHours, showDivider = true)
                            AppleCell(title = "Refresh inbox", onClick = onRefresh, showDivider = false)
                        }
                        SgDangerOutlineButton("Sign out", onClick = onLogout, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        AppleTabBar(tabs = hostTabs, selectedIndex = tab, onSelect = { tab = it })
    }
}

@Composable
private fun HostVisitCard(
    visit: VisitOut,
    photo: Bitmap?,
    chip: @Composable () -> Unit,
    onClick: (() -> Unit)? = null,
    actions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    val name = visit.visitorName ?: "Visitor"
    SgVisitCard(
        name = name,
        initials = initialsOf(name),
        subtitle = visit.purpose?.takeIf { it.isNotBlank() },
        timeText = whenText(visit.createdAt ?: visit.timeIn),
        avatar = photo?.let { bmp -> { Image(bitmap = bmp.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize()) } },
        status = chip,
        onClick = onClick,
        actions = actions,
    )
}

@Composable
private fun InboxCard(
    visit: VisitOut,
    photo: Bitmap?,
    busy: Boolean,
    rejecting: Boolean,
    rejectReason: String?,
    onApprove: (String) -> Unit,
    onStartReject: (String) -> Unit,
    onPickRejectReason: (String) -> Unit,
    onCancelReject: () -> Unit,
    onConfirmReject: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HostVisitCard(
            visit, photo,
            onClick = onOpen,
            chip = { SgStatusChip("Pending approval", SgStatusKind.PENDING) },
            actions = if (rejecting) null else ({
                SgSmallPill("Reject", onClick = { onStartReject(visit.id) }, style = SgPillStyle.OUTLINE_DANGER, enabled = !busy, modifier = Modifier.weight(1f))
                SgSmallPill("Approve", onClick = { onApprove(visit.id) }, enabled = !busy, modifier = Modifier.weight(1f))
            }),
        )
        if (rejecting) {
            Column(
                Modifier.fillMaxWidth().sgCardSurface().padding(SgSpacing.CardPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Reason for rejecting", style = SgType.SectionTitle, color = KioskColors.text)
                RejectReasons.chips.forEach { reason ->
                    val on = rejectReason == reason
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if (on) KioskColors.primary else KioskColors.secondaryFill)
                            .clickable(role = Role.RadioButton) { onPickRejectReason(reason) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(reason, style = SgType.Body, color = if (on) KioskColors.onPrimary else KioskColors.text)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SgSmallPill("Cancel", onClick = onCancelReject, style = SgPillStyle.SOFT, enabled = !busy, modifier = Modifier.weight(1f))
                    SgSmallPill("Confirm reject", onClick = onConfirmReject, enabled = !busy && !rejectReason.isNullOrBlank(), modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

// ───────────────────────── visit detail ─────────────────────────

@Composable
private fun DetailSection(title: String, rows: List<Pair<String, String?>>) {
    val shown = rows.filter { !it.second.isNullOrBlank() }
    if (shown.isEmpty()) return
    Column(Modifier.fillMaxWidth().sgCardSurface().padding(SgSpacing.CardPadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = SgType.SectionTitle, color = KioskColors.text)
        shown.forEach { (k, v) ->
            Column {
                Text(k, style = SgType.Caption, color = KioskColors.textMuted)
                Text(v!!, style = SgType.Body, color = KioskColors.text)
            }
        }
    }
}

/**
 * Host visit detail: photo + name, sections Visitor / Host / Visit, pinned Approve / Reject.
 * Mobile is always shown masked (XXXXXX1234) with a Call link (ACTION_DIAL) when the API gave a dialable number.
 * ID is shown exactly as the API returns it (masked by the server).
 */
@Composable
private fun HostVisitDetail(
    visit: VisitOut,
    photo: Bitmap?,
    hostName: String,
    isPending: Boolean,
    busy: Boolean,
    rejecting: Boolean,
    rejectReason: String?,
    onBack: () -> Unit,
    notice: String? = null,
    gateName: String? = null,
    onApprove: (String) -> Unit,
    onStartReject: (String) -> Unit,
    onPickRejectReason: (String) -> Unit,
    onCancelReject: () -> Unit,
    onConfirmReject: () -> Unit,
) {
    val name = visit.visitorName ?: "Visitor"
    Column(Modifier.fillMaxSize().background(KioskColors.bg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.material3.Icon(
                    androidx.compose.material.icons.Icons.Filled.ArrowBack,
                    contentDescription = "Back", tint = KioskColors.text,
                )
            }
            Text("Visit details", style = SgType.SectionTitle, color = KioskColors.text)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = SgSpacing.ScreenMargin).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(SgSpacing.GapBetweenCards),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(KioskColors.brandSoft), contentAlignment = Alignment.Center) {
                    if (photo != null) {
                        Image(bitmap = photo.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(initialsOf(name), style = SgType.ScreenTitle, color = KioskColors.primary)
                    }
                }
                Text(name, style = SgType.ScreenTitle, color = KioskColors.text)
                if (isPending) SgStatusChip("Pending approval", SgStatusKind.PENDING)
                else SgStatusChip(visit.status.replace('_', ' ').replaceFirstChar { it.uppercase() }, SgStatusKind.IN_PROGRESS)
            }
            DetailSection(
                "Visitor",
                listOf(
                    "Type" to visit.visitorType?.replaceFirstChar { it.uppercase() },
                    "Company" to visit.company,
                    "Mobile number" to HostCall.maskedMobile(visit.mobile).ifBlank { null },
                    "ID" to listOfNotNull(visit.idType, visit.idNumber).filter { it.isNotBlank() }.joinToString(" ").ifBlank { null },
                ),
            )
            HostCall.dialNumber(visit.mobile)?.let { number ->
                val ctx = androidx.compose.ui.platform.LocalContext.current
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                        .clickable(role = Role.Button) { HostCall.dial(ctx, number) }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Call, contentDescription = null,
                        tint = KioskColors.primary, modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Call", style = SgType.BodyStrong, color = KioskColors.primary)
                }
            }
            DetailSection("Host", listOf("Name" to hostName))
            DetailSection("Visit", listOf(
                "Purpose" to visit.purpose,
                "Gate" to gateName,
                "Requested" to whenText(visit.createdAt ?: visit.timeIn),
                "Notes" to visit.notes,
            ))
            if (rejecting) {
                Column(Modifier.fillMaxWidth().sgCardSurface().padding(SgSpacing.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reason for rejecting", style = SgType.SectionTitle, color = KioskColors.text)
                    RejectReasons.chips.forEach { reason ->
                        val on = rejectReason == reason
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .background(if (on) KioskColors.primary else KioskColors.secondaryFill)
                                .clickable(role = Role.RadioButton) { onPickRejectReason(reason) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) { Text(reason, style = SgType.Body, color = if (on) KioskColors.onPrimary else KioskColors.text) }
                    }
                }
            }
        }
        if (isPending && notice != null) {
            Box(Modifier.fillMaxWidth().padding(horizontal = SgSpacing.ScreenMargin).padding(bottom = 8.dp)) { NoticeBanner(notice) }
        }
        if (isPending) {
            Row(
                Modifier.fillMaxWidth().background(KioskColors.tabBarBg)
                    .padding(horizontal = SgSpacing.ScreenMargin, vertical = 12.dp)
                    .androidx_navPad(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (rejecting) {
                    SgSecondaryButton("Cancel", onClick = onCancelReject, enabled = !busy, modifier = Modifier.weight(1f))
                    SgPrimaryButton("Confirm reject", onClick = onConfirmReject, enabled = !busy && !rejectReason.isNullOrBlank(), modifier = Modifier.weight(1f))
                } else {
                    SgDangerOutlineButton("Reject", onClick = { onStartReject(visit.id) }, enabled = !busy, modifier = Modifier.weight(1f))
                    SgPrimaryButton("Approve", onClick = { onApprove(visit.id) }, enabled = !busy, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NoticeBanner(text: String) {
    Text(
        text, style = SgType.Body, color = KioskColors.warning,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(KioskColors.warningSoft).padding(12.dp),
    )
}

private fun Modifier.androidx_navPad(): Modifier = this.navigationBarsPadding()
