package com.satcop.smartvisitor.kiosk.guardpatrol.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolEngine
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolFixtures
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundStatus
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundTemplate
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.ChipShape
import com.satcop.smartvisitor.kiosk.ui.theme.ControlShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import java.util.Date

@Composable
fun GuardPatrolApp(vm: GuardPatrolViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val lastToastKind = remember { mutableStateOf(ToastKind.INFO) }
    val activity = LocalContext.current as? Activity
    BackHandler {
        when (state.screen) {
            GuardPatrolScreen.ACTIVE, GuardPatrolScreen.RESULT -> vm.backToStart()
            GuardPatrolScreen.START -> activity?.finishAffinity()
        }
    }

    LaunchedEffect(state.toast?.id) {
        val toast = state.toast ?: return@LaunchedEffect
        lastToastKind.value = toast.kind
        snackbarHostState.showSnackbar(toast.message)
        vm.clearToast()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KioskColors.bg),
    ) {
        Column(Modifier.fillMaxSize()) {
            Phase2Banner()
            when (state.screen) {
                GuardPatrolScreen.START -> StartRoundScreen(
                    state = state,
                    onSelect = vm::selectTemplate,
                    onStart = vm::startRound,
                    onStartAssignment = vm::startFromAssignment,
                    onToggleLive = vm::setUseLive,
                )
                GuardPatrolScreen.ACTIVE -> ActiveRoundScreen(
                    state = state,
                    onScanQr = { vm.openScanPicker("QR") },
                    onScanNfc = { vm.openScanPicker("NFC") },
                    onEnd = vm::endRound,
                    onToggleOffCampus = vm::setSimulateOffCampus,
                    onBackTemplates = vm::backToStart,
                )
                GuardPatrolScreen.RESULT -> EndResultScreen(
                    state = state,
                    onAnother = vm::backToStart,
                )
            }
        }

        Text(
            text = if (state.useLive) "LIVE" else "DEMO",
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(12.dp),
            color = KioskColors.watermark,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.4.sp,
            fontFamily = KioskFont,
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp),
        ) { data ->
            val kind = lastToastKind.value
            val bg = when (kind) {
                ToastKind.SUCCESS -> KioskColors.greenDim
                ToastKind.WARNING -> KioskColors.orangeDim
                ToastKind.ERROR -> KioskColors.redDim
                ToastKind.INFO -> KioskColors.card
            }
            val fg = when (kind) {
                ToastKind.SUCCESS -> KioskColors.greenBright
                ToastKind.WARNING -> KioskColors.peakAmberBright
                ToastKind.ERROR -> KioskColors.red
                ToastKind.INFO -> KioskColors.text
            }
            Snackbar(
                containerColor = bg,
                contentColor = fg,
                shape = ControlShape,
            ) { Text(data.visuals.message, fontFamily = KioskFont, fontSize = 13.sp) }
        }

        if (state.showScanPicker && state.round != null) {
            val tpl = state.templates.find { it.id == state.round!!.templateId }
                ?: GuardPatrolFixtures.template(state.round!!.templateId)
            if (tpl != null) {
                CheckpointPickerDialog(
                    mode = state.scanPickerMode,
                    checkpointIds = tpl.checkpointIds,
                    checkpoints = state.checkpoints,
                    onPick = vm::scanCheckpoint,
                    onDismiss = vm::dismissScanPicker,
                )
            }
        }
    }
}

@Composable
private fun Phase2Banner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(KioskColors.cyanDim)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Phase 2 · Guard Patrol — Assigned today + Start+Active",
            color = KioskColors.cyanBright,
            fontSize = 11.sp,
            fontFamily = KioskFont,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StartRoundScreen(
    state: GuardPatrolUiState,
    onSelect: (String) -> Unit,
    onStart: () -> Unit,
    onStartAssignment: (String) -> Unit,
    onToggleLive: (Boolean) -> Unit,
) {
    val allowSelfStart = !state.requireAssignment
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        HeaderBlock(
            title = "Start round",
            subtitle = state.schoolName,
        )
        Spacer(Modifier.height(10.dp))
        RoleChip(guardLabel = state.guardLabel)
        Spacer(Modifier.height(6.dp))
        Text(
            text = state.statusLine,
            color = if (state.useLive && state.liveReady) KioskColors.greenBright else KioskColors.textDim,
            fontSize = 11.sp,
            fontFamily = KioskFont,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleLive(!state.useLive) },
        ) {
            Checkbox(
                checked = state.useLive,
                onCheckedChange = onToggleLive,
                colors = CheckboxDefaults.colors(
                    checkedColor = KioskColors.cyan,
                    uncheckedColor = KioskColors.border,
                ),
            )
            Text(
                text = "Use living API (SCH-DEMO-01)",
                color = KioskColors.textDim,
                fontSize = 11.sp,
                fontFamily = KioskFont,
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AssignedTodaySection(
                assignments = state.assignments,
                templates = state.templates,
                fromLive = state.assignmentsFromLive,
                busy = state.busy,
                onStart = onStartAssignment,
            )
            if (allowSelfStart) {
                Text(
                    text = "Or self-start a template",
                    color = KioskColors.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = "Pick a template, then Start. Ordered templates warn on skip-ahead (F1) — scans still count.",
                    color = KioskColors.textDim,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
                state.templates.forEach { tpl ->
                    TemplateCard(
                        template = tpl,
                        selected = state.selectedTemplateId == tpl.id,
                        onClick = { onSelect(tpl.id) },
                    )
                }
            } else {
                Text(
                    text = "Self-start disabled (requireAssignment=true). Use an assignment above.",
                    color = KioskColors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
        if (allowSelfStart) {
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                label = if (state.busy) "Working…" else "Start round",
                enabled = state.selectedTemplateId != null && !state.busy,
                onClick = onStart,
            )
        }
    }
}

@Composable
private fun AssignedTodaySection(
    assignments: List<com.satcop.smartvisitor.kiosk.guardpatrol.data.PatrolAssignment>,
    templates: List<RoundTemplate>,
    fromLive: Boolean,
    busy: Boolean,
    onStart: (String) -> Unit,
) {
    val source = if (fromLive) "live" else "fixture"
    Text(
        text = "Assigned today",
        color = KioskColors.text,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = KioskFont,
    )
    Text(
        text = if (assignments.isEmpty()) {
            "No assignments for today ($source)."
        } else {
            "${assignments.size} duty(ies) · $source · dutyDate ${assignments.first().dutyDate}"
        },
        color = KioskColors.textDim,
        fontSize = 11.sp,
        fontFamily = KioskFont,
    )
    if (assignments.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(CardShape)
                .background(KioskColors.card)
                .border(1.dp, KioskColors.border, CardShape)
                .padding(14.dp),
        ) {
            Text(
                text = "Admin has not assigned a patrol yet.",
                color = KioskColors.textMuted,
                fontSize = 12.sp,
                fontFamily = KioskFont,
            )
        }
        return
    }
    assignments.forEach { asg ->
        val tpl = templates.find { it.id == asg.templateId }
            ?: GuardPatrolFixtures.template(asg.templateId)
        AssignmentCard(
            assignment = asg,
            templateName = tpl?.name ?: asg.templateId,
            checkpointCount = tpl?.checkpointIds?.size ?: 0,
            durationMin = tpl?.expectedDurationMin ?: 0,
            ordered = tpl?.ordered == true,
            busy = busy,
            onStart = { onStart(asg.id) },
        )
    }
}

@Composable
private fun AssignmentCard(
    assignment: com.satcop.smartvisitor.kiosk.guardpatrol.data.PatrolAssignment,
    templateName: String,
    checkpointCount: Int,
    durationMin: Int,
    ordered: Boolean,
    busy: Boolean,
    onStart: () -> Unit,
) {
    val canStart = assignment.status == com.satcop.smartvisitor.kiosk.guardpatrol.data.AssignmentStatus.ASSIGNED ||
        assignment.status == com.satcop.smartvisitor.kiosk.guardpatrol.data.AssignmentStatus.STARTED
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(KioskColors.cardHover)
            .border(1.dp, KioskColors.cyan, CardShape)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = templateName,
                color = KioskColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = assignment.status.display(),
                modifier = Modifier
                    .clip(ChipShape)
                    .background(KioskColors.cyanDim)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = KioskColors.cyanBright,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
        }
        Spacer(Modifier.height(6.dp))
        val shift = listOfNotNull(assignment.shiftStart, assignment.shiftEnd).joinToString("–")
        Text(
            text = buildString {
                append("$checkpointCount CPs · ${durationMin} min")
                if (ordered) append(" · Ordered") else append(" · Any order")
                if (shift.isNotBlank()) append(" · $shift")
            },
            color = KioskColors.textDim,
            fontSize = 12.sp,
            fontFamily = KioskFont,
        )
        if (!assignment.notes.isNullOrBlank()) {
            Text(
                text = assignment.notes!!,
                color = KioskColors.textMuted,
                fontSize = 11.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .clip(ControlShape)
                .background(if (canStart && !busy) KioskColors.cyan else KioskColors.border)
                .clickable(enabled = canStart && !busy, onClick = onStart)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    busy -> "Working…"
                    canStart -> "Start from assignment"
                    else -> assignment.status.display()
                },
                color = if (canStart && !busy) KioskColors.bg else KioskColors.textDim,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ActiveRoundScreen(
    state: GuardPatrolUiState,
    onScanQr: () -> Unit,
    onScanNfc: () -> Unit,
    onEnd: () -> Unit,
    onToggleOffCampus: (Boolean) -> Unit,
    onBackTemplates: () -> Unit,
) {
    val round = state.round ?: return
    val tpl = state.templates.find { it.id == round.templateId }
        ?: GuardPatrolFixtures.template(round.templateId)
        ?: return
    val scanned = GuardPatrolEngine.uniqueScannedIds(round)
    val nextId = GuardPatrolEngine.nextExpectedId(round, tpl)
    val count = scanned.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        HeaderBlock(
            title = tpl.name,
            subtitle = "${tpl.checkpointIds.size} CPs · ${tpl.expectedDurationMin} min",
        )
        Spacer(Modifier.height(8.dp))
        RoleChip(
            guardLabel = state.guardLabel,
            extra = if (tpl.ordered) "Ordered" else "Any order",
            ordered = tpl.ordered,
        )
        if (round.assignmentId != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Linked assignment · ${round.assignmentId}",
                color = KioskColors.cyanBright,
                fontSize = 11.sp,
                fontFamily = KioskFont,
            )
        }
        if (state.showOffCampusBanner) {
            Spacer(Modifier.height(8.dp))
            OffCampusBanner()
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Checkpoints $count / ${tpl.checkpointIds.size}",
                color = KioskColors.text,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
            Text(
                text = "Started ${formatTime(round.startedAtEpochMs)}",
                color = KioskColors.textDim,
                fontSize = 12.sp,
                fontFamily = KioskFont,
            )
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            tpl.checkpointIds.forEachIndexed { idx, cpId ->
                val cp = state.checkpoints[cpId] ?: GuardPatrolFixtures.checkpoint(cpId) ?: return@forEachIndexed
                val last = round.scans.lastOrNull { it.checkpointId == cpId }
                val done = cpId in scanned
                CheckpointRow(
                    index = idx + 1,
                    name = cp.name,
                    zone = cp.gateOrZone,
                    tagType = cp.tagType.name,
                    done = done,
                    nextExpected = nextId == cpId && tpl.ordered,
                    scannedAt = last?.scannedAtEpochMs,
                    outOfOrder = last?.outOfOrder == true,
                    offCampus = last?.offCampusSuspect == true,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleOffCampus(!state.simulateOffCampus) },
        ) {
            Checkbox(
                checked = state.simulateOffCampus,
                onCheckedChange = onToggleOffCampus,
                colors = CheckboxDefaults.colors(
                    checkedColor = KioskColors.cyan,
                    uncheckedColor = KioskColors.border,
                ),
            )
            Text(
                text = "Simulate off-campus GPS on next scan",
                color = KioskColors.textDim,
                fontSize = 11.sp,
                fontFamily = KioskFont,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(
                label = "Scan QR",
                icon = { Icon(Icons.Default.QrCodeScanner, null, tint = KioskColors.cyanBright, modifier = Modifier.size(18.dp)) },
                onClick = onScanQr,
                modifier = Modifier.weight(1f),
                accent = KioskColors.cyan,
            )
            SecondaryButton(
                label = "Scan NFC",
                icon = { Icon(Icons.Default.Nfc, null, tint = KioskColors.purpleBright, modifier = Modifier.size(18.dp)) },
                onClick = onScanNfc,
                modifier = Modifier.weight(1f),
                accent = KioskColors.purple,
            )
        }
        Spacer(Modifier.height(8.dp))
        DangerButton(label = "End round", onClick = onEnd)
        Spacer(Modifier.height(6.dp))
        TextButton(onClick = onBackTemplates, modifier = Modifier.fillMaxWidth()) {
            Text("← Templates", color = KioskColors.textMuted, fontFamily = KioskFont)
        }
    }
}

@Composable
private fun EndResultScreen(
    state: GuardPatrolUiState,
    onAnother: () -> Unit,
) {
    val round = state.round ?: return
    val tpl = state.templates.find { it.id == round.templateId }
        ?: GuardPatrolFixtures.template(round.templateId)
        ?: return
    val n = GuardPatrolEngine.uniqueScannedCount(round)
    val total = tpl.checkpointIds.size
    val (bg, fg, title, sub) = when (round.status) {
        RoundStatus.COMPLETED -> Quad(
            KioskColors.greenDim,
            KioskColors.greenBright,
            "Completed",
            "All $total checkpoints scanned.",
        )
        RoundStatus.MISSED -> Quad(
            KioskColors.redDim,
            KioskColors.red,
            "Missed",
            "Zero scans — MissedRound alert path (Security Head).",
        )
        RoundStatus.PARTIAL -> Quad(
            KioskColors.orangeDim,
            KioskColors.peakAmberBright,
            "Partial",
            "$n of $total scanned. MissedRound may raise for Security Head (F3).",
        )
        else -> Quad(
            KioskColors.card,
            KioskColors.text,
            round.status.display(),
            "",
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(bg),
            contentAlignment = Alignment.Center,
        ) {
            when (round.status) {
                RoundStatus.COMPLETED -> Icon(Icons.Default.Check, null, tint = fg, modifier = Modifier.size(36.dp))
                RoundStatus.MISSED -> Icon(Icons.Default.Close, null, tint = fg, modifier = Modifier.size(36.dp))
                else -> Icon(Icons.Default.Warning, null, tint = fg, modifier = Modifier.size(36.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, color = KioskColors.text, fontSize = 24.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
        Spacer(Modifier.height(8.dp))
        Text(sub, color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont)
        Spacer(Modifier.height(28.dp))
        PrimaryButton(label = "Start another", enabled = true, onClick = onAnother)
    }
}

private data class Quad(val a: Color, val b: Color, val c: String, val d: String)

@Composable
private fun CheckpointPickerDialog(
    mode: String,
    checkpointIds: List<String>,
    checkpoints: Map<String, com.satcop.smartvisitor.kiosk.guardpatrol.data.Checkpoint>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KioskColors.card,
        title = {
            Text(
                text = "Simulate $mode scan",
                color = KioskColors.text,
                fontFamily = KioskFont,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Pick a checkpoint (demo sim — no live camera/NFC).",
                    color = KioskColors.textDim,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
                checkpointIds.forEach { id ->
                    val cp = checkpoints[id] ?: GuardPatrolFixtures.checkpoint(id) ?: return@forEach
                    TextButton(
                        onClick = { onPick(id) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "${cp.name} · ${cp.tagType.name}",
                            color = KioskColors.text,
                            fontFamily = KioskFont,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = KioskColors.textMuted, fontFamily = KioskFont)
            }
        },
    )
}

@Composable
private fun HeaderBlock(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = KioskFont,
        )
        Text(
            text = subtitle,
            color = KioskColors.textMuted,
            fontSize = 13.sp,
            fontFamily = KioskFont,
        )
    }
}

@Composable
private fun RoleChip(guardLabel: String = "Guard G1", extra: String? = null, ordered: Boolean = true) {
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(KioskColors.cyanDim)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(KioskColors.cyan),
            contentAlignment = Alignment.Center,
        ) {
            Text("G1", color = KioskColors.bg, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
        }
        Text(
            text = buildString {
                append(guardLabel)
                append(" · guard_id set")
                if (extra != null) append(" · ")
            },
            color = KioskColors.cyanBright,
            fontSize = 12.sp,
            fontFamily = KioskFont,
        )
        if (extra != null) {
            OrderBadge(ordered = ordered, label = extra)
        }
    }
}

@Composable
private fun OrderBadge(ordered: Boolean, label: String) {
    val bg = if (ordered) KioskColors.purpleDim else KioskColors.greenDim
    val fg = if (ordered) KioskColors.purpleBright else KioskColors.greenBright
    Text(
        text = label,
        modifier = Modifier
            .clip(ChipShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = fg,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = KioskFont,
    )
}

@Composable
private fun TemplateCard(
    template: RoundTemplate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (selected) KioskColors.purple else KioskColors.border
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(if (selected) KioskColors.cardHover else KioskColors.card)
            .border(1.dp, borderColor, CardShape)
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = template.name,
                color = KioskColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
                modifier = Modifier.weight(1f),
            )
            OrderBadge(
                ordered = template.ordered,
                label = if (template.ordered) "Ordered" else "Any order",
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${template.checkpointIds.size} checkpoints · ${template.expectedDurationMin} min",
            color = KioskColors.textDim,
            fontSize = 12.sp,
            fontFamily = KioskFont,
        )
    }
}

@Composable
private fun CheckpointRow(
    index: Int,
    name: String,
    zone: String,
    tagType: String,
    done: Boolean,
    nextExpected: Boolean,
    scannedAt: Long?,
    outOfOrder: Boolean,
    offCampus: Boolean,
) {
    val border = when {
        done -> KioskColors.green
        nextExpected -> KioskColors.cyan
        else -> KioskColors.border
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(KioskColors.card)
            .border(1.dp, border, CardShape)
            .padding(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (done) KioskColors.greenDim else KioskColors.borderSubtle),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$index",
                        color = if (done) KioskColors.greenBright else KioskColors.textMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = KioskFont,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name,
                    color = KioskColors.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
            }
            Text(
                text = tagType,
                modifier = Modifier
                    .clip(ChipShape)
                    .background(KioskColors.borderSubtle)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = KioskColors.textMuted,
                fontSize = 10.sp,
                fontFamily = KioskFont,
            )
        }
        Text(
            text = zone + if (nextExpected) " · next expected" else "",
            color = KioskColors.textDim,
            fontSize = 11.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (done && scannedAt != null) {
            Text(
                text = "Scanned ${formatTime(scannedAt)}",
                color = KioskColors.greenBright,
                fontSize = 11.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (outOfOrder) {
                    FlagChip("out_of_order", KioskColors.orangeDim, KioskColors.peakAmberBright)
                }
                if (offCampus) {
                    FlagChip("off_campus_suspect", KioskColors.redDim, KioskColors.red)
                }
            }
        }
    }
}

@Composable
private fun FlagChip(label: String, bg: Color, fg: Color) {
    Text(
        text = label,
        modifier = Modifier
            .clip(ChipShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = fg,
        fontSize = 10.sp,
        fontFamily = KioskFont,
    )
}

@Composable
private fun OffCampusBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(KioskColors.orangeDim)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Warning, null, tint = KioskColors.peakAmberBright, modifier = Modifier.size(16.dp))
        Text(
            text = "Soft geo: last scan flagged off-campus (still counts — no auto-fail).",
            color = KioskColors.peakAmberBright,
            fontSize = 11.sp,
            fontFamily = KioskFont,
        )
    }
}

@Composable
private fun PrimaryButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .background(if (enabled) KioskColors.purple else KioskColors.border)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) KioskColors.text else KioskColors.textDim,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun DangerButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .background(KioskColors.redDim)
            .border(1.dp, KioskColors.red, ControlShape)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = KioskColors.red,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun SecondaryButton(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(ControlShape)
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.5f), ControlShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(label, color = KioskColors.text, fontFamily = KioskFont, fontWeight = FontWeight.Medium, fontSize = 13.sp)
    }
}

private fun formatTime(epochMs: Long): String {
    return DateFormat.format("h:mm a", Date(epochMs)).toString()
}
