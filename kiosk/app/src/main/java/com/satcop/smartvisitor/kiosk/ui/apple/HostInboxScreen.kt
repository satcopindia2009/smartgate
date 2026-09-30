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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
) {
    var tab by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(focusVisitId) { if (focusVisitId != null) tab = 0 }
    val ordered = if (focusVisitId == null) pending
    else pending.sortedByDescending { it.id == focusVisitId }

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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HostVisitCard(
            visit, photo,
            chip = { SgStatusChip("Pending", SgStatusKind.PENDING) },
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
