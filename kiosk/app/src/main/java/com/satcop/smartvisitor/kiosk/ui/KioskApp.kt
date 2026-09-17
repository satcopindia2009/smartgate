package com.satcop.smartvisitor.kiosk.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.satcop.smartvisitor.kiosk.data.fixture.DemoHubLinks
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.ui.components.DemoWatermark
import com.satcop.smartvisitor.kiosk.ui.components.GatePill
import com.satcop.smartvisitor.kiosk.ui.components.ShieldMark
import com.satcop.smartvisitor.kiosk.ui.components.SourcePill
import com.satcop.smartvisitor.kiosk.ui.components.StatusPill
import com.satcop.smartvisitor.kiosk.ui.components.StepDots
import com.satcop.smartvisitor.kiosk.ui.components.ToastBanner
import com.satcop.smartvisitor.kiosk.ui.steps.DemoHubStep
import com.satcop.smartvisitor.kiosk.ui.steps.OutcomeStep
import com.satcop.smartvisitor.kiosk.ui.steps.PhotoIdStep
import com.satcop.smartvisitor.kiosk.ui.steps.VisitorDetailsStep
import com.satcop.smartvisitor.kiosk.ui.steps.VisitorTypeStep
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import kotlinx.coroutines.delay

@Composable
fun KioskApp(
    viewModel: KioskViewModel = viewModel(factory = KioskViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val openPreview: (String) -> Unit = { url ->
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        } catch (_: ActivityNotFoundException) {
            viewModel.previewOpenFailed()
        }
    }
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
                dataSource = state.dataSource,
                roleLabel = state.meDisplayName,
                onSelectGate = viewModel::selectGate,
                onOpenHub = viewModel::openDemoHub,
                onOpenGate = viewModel::openGateCheckIn,
                onOpenPreview = openPreview,
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
                    if (state.step > 0) {
                        StepDots(current = state.step)
                    }
                    AnimatedContent(
                        targetState = state.step,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "kiosk-step",
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                    ) { step ->
                        when (step) {
                            0 -> DemoHubStep(
                                onOpenGate = viewModel::openGateCheckIn,
                                onOpenPreview = openPreview,
                            )
                            1 -> VisitorTypeStep(
                                selectedType = state.draft.visitorType,
                                schoolName = state.schoolName,
                                clockLabel = state.clockLabel,
                                recent = state.recent,
                                gates = state.gates,
                                onSelectType = viewModel::selectVisitorType,
                                onPrefill = viewModel::prefillSample,
                                onDemoHub = viewModel::openDemoHub,
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
                            3 -> PhotoIdStep(
                                draft = state.draft,
                                livePhoto = state.livePhoto,
                                idImage = state.idImage,
                                errors = state.fieldErrors,
                                submitting = state.submitting,
                                blocked = state.blocked,
                                blacklistHit = state.blacklistHit,
                                onIdType = viewModel::selectIdType,
                                onIdNumber = viewModel::updateIdNumber,
                                onLivePhoto = viewModel::setLivePhoto,
                                onIdImage = viewModel::setIdImage,
                                onSignature = viewModel::setSignature,
                                onClearSignature = viewModel::clearSignature,
                                onBlockSample = viewModel::applyBlockSample,
                                onAlertSample = viewModel::applyAlertSample,
                                onBack = viewModel::back,
                                onSubmit = viewModel::submitRegistration,
                            )
                            else -> OutcomeStep(
                                draft = state.draft,
                                visit = state.createdVisit,
                                hosts = state.hosts,
                                gates = state.gates,
                                blacklistHit = state.blacklistHit,
                                dataSource = state.dataSource,
                                busy = state.outcomeBusy,
                                onRefresh = { viewModel.refreshVisit() },
                                onDemoApprove = viewModel::demoApprove,
                                onCheckIn = viewModel::scanCheckIn,
                                onCheckOut = viewModel::scanCheckOut,
                                onLoadStory = viewModel::loadStoryPass,
                                onNewVisitor = viewModel::registerAnother,
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
            ToastBanner(message = toast, kind = state.toastKind)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun KioskHeader(
    schoolName: String,
    gateName: String,
    gates: List<com.satcop.smartvisitor.kiosk.data.model.Gate>,
    clockLabel: String,
    dataSource: DataSource,
    roleLabel: String,
    onSelectGate: (String) -> Unit,
    onOpenHub: () -> Unit,
    onOpenGate: () -> Unit,
    onOpenPreview: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var demoMenuOpen by remember { mutableStateOf(false) }
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    text = if (roleLabel.isBlank()) {
                        "$schoolName · Gate check-in"
                    } else {
                        "$schoolName · $roleLabel"
                    },
                    color = KioskColors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
            }
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourcePill(live = dataSource == DataSource.LIVE)
            Box {
                StatusPill(
                    label = "Demo hub",
                    background = KioskColors.purpleDim,
                    foreground = KioskColors.purpleBright,
                    onClick = { demoMenuOpen = true },
                )
                DropdownMenu(
                    expanded = demoMenuOpen,
                    onDismissRequest = { demoMenuOpen = false },
                    modifier = Modifier
                        .background(KioskColors.card)
                        .widthIn(min = 260.dp)
                        .heightIn(max = 360.dp),
                ) {
                    DropdownMenuItem(
                        text = {
                            Text("Demo hub", color = KioskColors.text, fontFamily = KioskFont)
                        },
                        onClick = {
                            onOpenHub()
                            demoMenuOpen = false
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text("Visitor gate (this app)", color = KioskColors.text, fontFamily = KioskFont)
                        },
                        onClick = {
                            onOpenGate()
                            demoMenuOpen = false
                        },
                    )
                    HorizontalDivider(color = KioskColors.border)
                    DemoHubLinks.webPreviews.forEach { item ->
                        DropdownMenuItem(
                            text = {
                                Text(item.title, color = KioskColors.text, fontFamily = KioskFont)
                            },
                            onClick = {
                                item.url?.let(onOpenPreview)
                                demoMenuOpen = false
                            },
                        )
                    }
                }
            }
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
