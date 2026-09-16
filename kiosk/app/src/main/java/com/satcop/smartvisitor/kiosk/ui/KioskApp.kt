package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.satcop.smartvisitor.kiosk.ui.components.DemoWatermark
import com.satcop.smartvisitor.kiosk.ui.components.GatePill
import com.satcop.smartvisitor.kiosk.ui.components.ShieldMark
import com.satcop.smartvisitor.kiosk.ui.components.StepDots
import com.satcop.smartvisitor.kiosk.ui.components.ToastBanner
import com.satcop.smartvisitor.kiosk.ui.steps.VisitorDetailsStep
import com.satcop.smartvisitor.kiosk.ui.steps.VisitorTypeStep
import com.satcop.smartvisitor.kiosk.ui.steps.WaveHoldStep
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import kotlinx.coroutines.delay

@Composable
fun KioskApp(
    viewModel: KioskViewModel = viewModel(factory = KioskViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(KioskColors.bg)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 1180.dp)
                .align(Alignment.TopCenter)
                .padding(horizontal = 28.dp, vertical = 20.dp),
        ) {
            KioskHeader(
                schoolName = state.schoolName,
                gateName = state.selectedGate?.name ?: "Main Gate",
                gates = state.gates,
                clockLabel = state.clockLabel,
                onSelectGate = viewModel::selectGate,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .shadow(24.dp, CardShape, ambientColor = androidx.compose.ui.graphics.Color(0x59000000))
                    .clip(CardShape)
                    .background(KioskColors.card)
                    .border(1.dp, KioskColors.border, CardShape)
                    .padding(horizontal = 32.dp, vertical = 28.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    StepDots(current = state.step)
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "kiosk-step",
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) { step ->
                        when (step) {
                            1 -> VisitorTypeStep(
                                selectedType = state.draft.visitorType,
                                schoolName = state.schoolName,
                                clockLabel = state.clockLabel,
                                recent = state.recent,
                                gates = state.gates,
                                onSelectType = viewModel::selectVisitorType,
                                onPrefill = viewModel::prefillSample,
                                onContinue = viewModel::continueFromStep1,
                            )
                            2 -> VisitorDetailsStep(
                                draft = state.draft,
                                hosts = state.hosts,
                                errors = state.fieldErrors,
                                onName = viewModel::updateName,
                                onMobile = viewModel::updateMobile,
                                onPurpose = viewModel::updatePurpose,
                                onHost = viewModel::selectHost,
                                onVehicle = viewModel::updateVehicle,
                                onAccompanying = viewModel::updateAccompanying,
                                onNotes = viewModel::updateNotes,
                                onBack = viewModel::back,
                                onContinue = viewModel::continueFromStep2,
                            )
                            else -> WaveHoldStep(
                                draft = state.draft,
                                hosts = state.hosts,
                                gates = state.gates,
                                onBack = viewModel::back,
                            )
                        }
                    }
                }
            }
        }
        DemoWatermark(
            label = state.watermark,
            modifier = Modifier.align(Alignment.BottomStart),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
        ) {
            val toast = state.toast
            if (toast != null) {
                LaunchedEffect(toast) {
                    delay(2800)
                    viewModel.dismissToast()
                }
            }
            ToastBanner(message = toast)
        }
    }
}

@Composable
private fun KioskHeader(
    schoolName: String,
    gateName: String,
    gates: List<com.satcop.smartvisitor.kiosk.data.model.Gate>,
    clockLabel: String,
    onSelectGate: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShieldMark()
            Column {
                Text(
                    text = "Satcop Smart Visitor",
                    color = KioskColors.text,
                    fontSize = 18.sp,
                    fontFamily = KioskFont,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                Text(
                    text = "$schoolName · Gate check-in",
                    color = KioskColors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                GatePill(name = gateName, onClick = { menuOpen = true })
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    modifier = Modifier
                        .background(KioskColors.card)
                        .heightIn(max = 280.dp),
                ) {
                    gates.forEach { gate ->
                        DropdownMenuItem(
                            text = {
                                Text(gate.name, color = KioskColors.text, fontFamily = KioskFont)
                            },
                            onClick = {
                                onSelectGate(gate.id)
                                menuOpen = false
                            },
                        )
                    }
                }
            }
            Text(
                text = clockLabel.ifBlank { "—" },
                color = KioskColors.textMuted,
                fontSize = 13.sp,
                fontFamily = KioskFont,
            )
        }
    }
}
