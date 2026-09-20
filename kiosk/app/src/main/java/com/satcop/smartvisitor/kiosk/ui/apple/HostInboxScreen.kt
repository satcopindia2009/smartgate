package com.satcop.smartvisitor.kiosk.ui.apple

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.AfterHoursCopy
import com.satcop.smartvisitor.kiosk.data.model.RejectReasons
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

/** AC-APP1 bottomnav SoT: Inbox · Done · Inside · More */
private val hostTabs = listOf(
    AppleTabItem("Inbox", Icons.Filled.Inbox),
    AppleTabItem("Done", Icons.Filled.CheckCircle),
    AppleTabItem("Inside", Icons.Filled.Person),
    AppleTabItem("More", Icons.Filled.MoreHoriz),
)

/**
 * Host shell from phone-apple-bottomnav (host-home / host-done).
 * Approve/Decline above fold · pinned tab bar · no long-scroll home.
 */
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
) {
    var tab by remember { mutableIntStateOf(0) }
    val topPending = pending.take(1)
    val approvedToday = active.size

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
                0 -> { // Inbox home — host-home.png
                    AppleShellNav(leading = "Roles", trailing = "Edit", onTrailing = onRefresh)
                    AppleShellTitle("Host")
                    AppleShellSub("Needs you · ${pending.size}")
                    if (afterHours) {
                        Text(
                            AfterHoursCopy.HOST_NO_OP,
                            color = KioskColors.systemOrange,
                            fontSize = 13.sp,
                            fontFamily = KioskFont,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                    if (topPending.isEmpty()) {
                        AppleInset {
                            AppleCell("No pending visits", showChevron = false, showDivider = false)
                        }
                    } else {
                        val visit = topPending.first()
                        CompactApproveCard(
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
                    Spacer(Modifier.height(10.dp))
                    AppleGrid2 {
                        AppleTile(
                            icon = "✅",
                            label = "Approved",
                            detail = "Today $approvedToday",
                            onClick = { tab = 1 },
                        )
                        AppleTile(
                            icon = "👤",
                            label = "Inside",
                            detail = "With you ${active.size}",
                            onClick = { tab = 2 },
                        )
                    }
                }
                1 -> { // Done — host-done.png
                    AppleShellNav(leading = " ", trailing = "Filter")
                    AppleShellTitle("Done")
                    AppleShellSub("Today")
                    AppleInset {
                        if (active.isEmpty()) {
                            AppleCell("None yet", showChevron = false, showDivider = false)
                        } else {
                            active.take(5).forEachIndexed { i, visit ->
                                AppleCell(
                                    title = visit.visitorName ?: "Visitor",
                                    subtitle = "Approved · ${visit.status}",
                                    trailing = "OK",
                                    showChevron = false,
                                    showDivider = i < active.take(5).lastIndex,
                                    onClick = { if (!busy) onMeetingDone(visit.id) },
                                )
                            }
                        }
                    }
                    if (showingAfterHours) {
                        Text(
                            AfterHoursCopy.HOST_NO_OP,
                            color = KioskColors.textMuted,
                            fontSize = 14.sp,
                            fontFamily = KioskFont,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                2 -> { // Inside
                    AppleShellNav(leading = " ", trailing = " ")
                    AppleShellTitle("Inside")
                    AppleShellSub("With you · ${active.size}")
                    AppleInset {
                        if (active.isEmpty()) {
                            AppleCell("None right now", showChevron = false, showDivider = false)
                        } else {
                            active.take(5).forEachIndexed { i, visit ->
                                AppleCell(
                                    title = visit.visitorName ?: "Visitor",
                                    subtitle = visit.purpose ?: visit.status,
                                    trailing = "Done",
                                    showChevron = false,
                                    showDivider = i < active.take(5).lastIndex,
                                    onClick = { if (!busy) onMeetingDone(visit.id) },
                                )
                            }
                        }
                    }
                }
                else -> { // More
                    AppleShellNav(leading = " ", trailing = " ")
                    AppleShellTitle("More")
                    val identity = listOf(displayName, schoolId, staffId)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                    AppleSectionHeader("Host")
                    AppleInset {
                        AppleCell(title = identity.ifBlank { "Host" }, showChevron = false, showDivider = true)
                        AppleCell(title = "After-hours info", onClick = onShowAfterHours, showDivider = true)
                        AppleCell(title = "Refresh inbox", onClick = onRefresh, showDivider = false)
                    }
                    AppearanceSegmentedRow()
                    AppleSectionHeader("Account")
                    AppleInset {
                        AppleCell("Sign out", showChevron = false, showDivider = false, onClick = onLogout)
                    }
                }
            }
        }
        AppleTabBar(tabs = hostTabs, selectedIndex = tab, onSelect = { tab = it })
    }
}

@Composable
private fun CompactApproveCard(
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
    val name = visit.visitorName ?: "Visitor"
    val initials = name.split(" ")
        .mapNotNull { it.firstOrNull()?.toString() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }
    AppleInset {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape),
                )
            } else {
                AppleAvatar(initials = initials, size = 40.dp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    color = KioskColors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
                Text(
                    listOfNotNull(visit.visitorType, visit.purpose, visit.gateId)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                        .ifBlank { "Awaiting approval" },
                    color = KioskColors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 2.dp),
                )
                if (rejecting) {
                    Spacer(Modifier.height(8.dp))
                    Text("Decline reason", color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
                    RejectReasons.chips.forEach { reason ->
                        AppleCell(
                            title = reason,
                            trailing = if (rejectReason == reason) "✓" else null,
                            showChevron = false,
                            showDivider = true,
                            onClick = { onPickRejectReason(reason) },
                        )
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ApplePillButton(
                            text = "Cancel",
                            onClick = onCancelReject,
                            positive = false,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        )
                        Spacer(Modifier.width(8.dp))
                        ApplePillButton(
                            text = "Confirm decline",
                            onClick = onConfirmReject,
                            positive = true,
                            modifier = Modifier.weight(1f),
                            enabled = !busy && !rejectReason.isNullOrBlank(),
                        )
                    }
                } else {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        ApplePillButton(
                            text = "Decline",
                            onClick = { onStartReject(visit.id) },
                            positive = false,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        )
                        Spacer(Modifier.width(8.dp))
                        ApplePillButton(
                            text = "Approve",
                            onClick = { onApprove(visit.id) },
                            positive = true,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                        )
                    }
                }
            }
        }
    }
}
