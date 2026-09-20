package com.satcop.smartvisitor.kiosk.ui.apple

import android.graphics.Bitmap
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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

private val hostTabs = listOf(
    AppleTabItem("📥", "Inbox"),
    AppleTabItem("✅", "Done"),
    AppleTabItem("👤", "Me"),
)

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
    var seg by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }

    val filteredPending = pending.filter {
        search.isBlank() ||
            (it.visitorName ?: "").contains(search, ignoreCase = true) ||
            (it.purpose ?: "").contains(search, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(KioskColors.bg),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            when (tab) {
                0 -> {
                    AppleNavBar(
                        title = "Approvals",
                        leading = "Select",
                        trailing = "⋮",
                        onTrailing = onRefresh,
                    )
                    AppleLargeTitle("Inbox")
                    AppleSearchField(value = search, onValueChange = { search = it }, placeholder = "🔍 Search")
                    Spacer(Modifier.height(8.dp))
                    AppleSegmented(
                        options = listOf("Needs you", "Approved", "All"),
                        selectedIndex = seg,
                        onSelect = { seg = it },
                    )
                    if (afterHours) {
                        Text(
                            AfterHoursCopy.HOST_NO_OP,
                            color = KioskColors.systemOrange,
                            fontSize = 13.sp,
                            fontFamily = KioskFont,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                    when (seg) {
                        0 -> InboxList(
                            visits = filteredPending,
                            photos = photos,
                            busy = busy,
                            rejectingVisitId = rejectingVisitId,
                            rejectReason = rejectReason,
                            onApprove = onApprove,
                            onStartReject = onStartReject,
                            onPickRejectReason = onPickRejectReason,
                            onCancelReject = onCancelReject,
                            onConfirmReject = onConfirmReject,
                        )
                        1 -> ActiveList(active = active, onMeetingDone = onMeetingDone, busy = busy)
                        else -> {
                            InboxList(
                                visits = filteredPending,
                                photos = photos,
                                busy = busy,
                                rejectingVisitId = rejectingVisitId,
                                rejectReason = rejectReason,
                                onApprove = onApprove,
                                onStartReject = onStartReject,
                                onPickRejectReason = onPickRejectReason,
                                onCancelReject = onCancelReject,
                                onConfirmReject = onConfirmReject,
                            )
                            ActiveList(active = active, onMeetingDone = onMeetingDone, busy = busy)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
                1 -> {
                    AppleLargeTitle("Done")
                    ActiveList(active = active, onMeetingDone = onMeetingDone, busy = busy)
                    if (showingAfterHours) {
                        Text(
                            AfterHoursCopy.HOST_NO_OP,
                            color = KioskColors.textMuted,
                            fontSize = 14.sp,
                            fontFamily = KioskFont,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
                else -> {
                    AppleLargeTitle("Me")
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
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
        AppleTabBar(tabs = hostTabs, selectedIndex = tab, onSelect = { tab = it })
    }
}

@Composable
private fun InboxList(
    visits: List<VisitOut>,
    photos: Map<String, Bitmap>,
    busy: Boolean,
    rejectingVisitId: String?,
    rejectReason: String?,
    onApprove: (String) -> Unit,
    onStartReject: (String) -> Unit,
    onPickRejectReason: (String) -> Unit,
    onCancelReject: () -> Unit,
    onConfirmReject: () -> Unit,
) {
    if (visits.isEmpty()) {
        AppleInset {
            AppleCell("No pending visits", showChevron = false, showDivider = false)
        }
        return
    }
    AppleInset {
        visits.forEachIndexed { index, visit ->
            val name = visit.visitorName ?: "Visitor"
            val initials = name.split(" ")
                .mapNotNull { it.firstOrNull()?.toString() }
                .take(2)
                .joinToString("")
                .ifBlank { "?" }
            val photo = photos[visit.id]
            val rejecting = rejectingVisitId == visit.id
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    if (photo != null) {
                        Image(
                            bitmap = photo.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape),
                        )
                    } else {
                        AppleAvatar(
                            initials = initials,
                            color = if (index % 2 == 0) KioskColors.systemBlue else KioskColors.systemOrange,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                color = KioskColors.text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = KioskFont,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "now",
                                color = KioskColors.textMuted,
                                fontSize = 13.sp,
                                fontFamily = KioskFont,
                            )
                        }
                        Text(
                            listOfNotNull(visit.visitorType, visit.purpose, visit.gateId)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { "Awaiting approval" },
                            color = KioskColors.textMuted,
                            fontSize = 14.sp,
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
                if (index < visits.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.33.dp,
                        color = KioskColors.border,
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveList(
    active: List<VisitOut>,
    onMeetingDone: (String) -> Unit,
    busy: Boolean,
) {
    AppleSectionHeader("Inside / approved")
    if (active.isEmpty()) {
        AppleInset {
            AppleCell("None right now", showChevron = false, showDivider = false)
        }
        return
    }
    AppleInset {
        active.forEachIndexed { i, visit ->
            AppleCell(
                title = visit.visitorName ?: "Visitor",
                subtitle = visit.purpose ?: visit.status,
                trailing = "Done",
                showChevron = false,
                showDivider = i < active.lastIndex,
                onClick = { if (!busy) onMeetingDone(visit.id) },
            )
        }
    }
}
