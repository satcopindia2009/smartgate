package com.satcop.smartvisitor.kiosk.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.guardpatrol.ui.GuardPatrolApp
import com.satcop.smartvisitor.kiosk.ui.components.DemoWatermark
import com.satcop.smartvisitor.kiosk.ui.components.GatePill
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.ShieldMark
import com.satcop.smartvisitor.kiosk.ui.components.SourcePill
import com.satcop.smartvisitor.kiosk.ui.components.StepDots
import com.satcop.smartvisitor.kiosk.ui.components.ToastBanner
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
    val activity = LocalContext.current as? Activity
    BackHandler {
        val consumed = viewModel.onSystemBack()
        if (!consumed) {
            activity?.finishAffinity()
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(KioskColors.bg)
            .statusBarsPadding()
            .imePadding(),
    ) {
        val compact = maxWidth < CompactWidthBreakpoint
        val hPad = if (compact) 16.dp else 28.dp
        val vPad = if (compact) 12.dp else 20.dp
        val cardHPad = if (compact) 16.dp else 32.dp
        val cardVPad = if (compact) 16.dp else 28.dp
        CompositionLocalProvider(LocalKioskCompact provides compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (compact) Modifier else Modifier.fillMaxSize())
                    .widthIn(max = 1180.dp)
                    .align(Alignment.TopCenter)
                    .then(if (compact) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                    .padding(horizontal = hPad, vertical = vPad)
                    .padding(bottom = if (compact) 36.dp else 0.dp),
            ) {
                if (!state.signedIn) {
                    if (state.screen == KioskScreen.FACE_LOGIN) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(24.dp, CardShape, ambientColor = androidx.compose.ui.graphics.Color(0x59000000))
                                .clip(CardShape)
                                .background(KioskColors.card)
                                .border(1.dp, KioskColors.border, CardShape)
                                .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 16.dp else 28.dp),
                        ) {
                            FaceLoginScaffoldScreen(
                                enrolled = state.faceEnrolled,
                                consentAgreed = state.faceConsentAgreed,
                                busy = state.faceBusy,
                                message = state.faceMessage,
                                onToggleConsent = viewModel::toggleFaceConsent,
                                onEnroll = viewModel::enrollFaceStub,
                                onFaceLogin = viewModel::faceLoginStub,
                                onUsePassword = viewModel::closeFaceLogin,
                            )
                        }
                    } else {
                        LoginScreen(
                            username = state.loginUsername,
                            password = state.loginPassword,
                            error = state.loginError,
                            busy = state.loginBusy,
                            compact = compact,
                            onUsername = viewModel::updateLoginUsername,
                            onPassword = viewModel::updateLoginPassword,
                            onSubmit = viewModel::login,
                            onFaceLogin = viewModel::openFaceLogin,
                        )
                    }
                } else {
                    val role = state.homeRole()
                    if (role == KioskRole.GUARD) {
                        GuardPatrolApp(onExit = viewModel::logout)
                    } else {
                    KioskHeader(
                        schoolName = state.schoolName,
                        schoolId = state.schoolId,
                        gateName = state.selectedGate?.name ?: "Main Gate",
                        gates = state.gates,
                        clockLabel = state.clockLabel,
                        dataSource = state.dataSource,
                        displayName = state.meDisplayName,
                        compact = compact,
                        showGateMenu = role == KioskRole.GATE,
                        showPickup = role == KioskRole.GATE && state.screen == KioskScreen.HOME,
                        pickupOpen = state.screen == KioskScreen.PICKUP,
                        onSelectGate = viewModel::selectGate,
                        onLogout = viewModel::logout,
                        onPickup = viewModel::openPickup,
                        onClosePickup = viewModel::closePickup,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (compact) Modifier else Modifier.weight(1f))
                            .shadow(24.dp, CardShape, ambientColor = androidx.compose.ui.graphics.Color(0x59000000))
                            .clip(CardShape)
                            .background(KioskColors.card)
                            .border(1.dp, KioskColors.border, CardShape)
                            .padding(horizontal = cardHPad, vertical = cardVPad),
                    ) {
                        when {
                            role == KioskRole.GATE && state.screen == KioskScreen.PICKUP -> {
                                PickupScreen(
                                    query = state.pickupQuery,
                                    students = state.students,
                                    selectedStudent = state.selectedStudent,
                                    authorized = state.authorizedPickup,
                                    selectedCollector = state.selectedCollector,
                                    pickupReason = state.pickupReason,
                                    reasonOther = state.pickupReasonOther,
                                    pickup = state.activePickup,
                                    busy = state.pickupBusy,
                                    compact = compact,
                                    gateName = state.selectedGate?.name ?: "Main Gate",
                                    onQuery = viewModel::updatePickupQuery,
                                    onSelectStudent = viewModel::selectPickupStudent,
                                    onSelectCollector = viewModel::selectPickupCollector,
                                    onReason = viewModel::selectPickupReason,
                                    onReasonOther = viewModel::updatePickupReasonOther,
                                    onStart = viewModel::startPickup,
                                    onConsent = viewModel::consentPickup,
                                    onRelease = viewModel::releasePickup,
                                    onBack = viewModel::closePickup,
                                )
                            }
                            role == KioskRole.GATE && state.screen in setOf(KioskScreen.HISTORY, KioskScreen.HISTORY_DETAIL) -> {
                                HistoryScreen(
                                    events = state.historyEvents,
                                    filterToday = state.historyTodayOnly,
                                    filterKind = state.historyKindFilter,
                                    filterStatus = state.historyStatusFilter,
                                    busy = state.historyBusy,
                                    selected = state.historySelected,
                                    onToggleToday = viewModel::toggleHistoryToday,
                                    onKind = viewModel::setHistoryKind,
                                    onStatus = viewModel::setHistoryStatus,
                                    onSelect = viewModel::selectHistory,
                                    onClearDetail = viewModel::clearHistoryDetail,
                                    onRefresh = viewModel::refreshHistory,
                                    onBack = viewModel::closeGuardTool,
                                )
                            }
                            role == KioskRole.GATE && state.screen == KioskScreen.COURIER -> {
                                CourierLogScreen(
                                    company = state.courierCompany,
                                    recipient = state.courierRecipient,
                                    personName = state.courierPerson,
                                    mobile = state.courierMobile,
                                    note = state.courierNote,
                                    dept = state.courierDept,
                                    busy = state.courierBusy,
                                    recent = state.courierRecent,
                                    onCompany = viewModel::updateCourierCompany,
                                    onRecipient = viewModel::updateCourierRecipient,
                                    onPerson = viewModel::updateCourierPerson,
                                    onMobile = viewModel::updateCourierMobile,
                                    onNote = viewModel::updateCourierNote,
                                    onDept = viewModel::updateCourierDept,
                                    onReceive = viewModel::receiveCourier,
                                    onHandOver = viewModel::handOverCourier,
                                    onBack = viewModel::closeGuardTool,
                                )
                            }
                            role == KioskRole.GATE && state.screen == KioskScreen.CHECKOUT -> {
                                CheckoutVerifyScreen(
                                    inside = state.checkoutInside,
                                    selectedId = state.checkoutSelectedId,
                                    busy = state.checkoutBusy,
                                    onSelect = viewModel::selectCheckout,
                                    onConfirm = viewModel::confirmCheckout,
                                    onBack = viewModel::closeGuardTool,
                                    onRefresh = viewModel::refreshCheckoutInside,
                                )
                            }
                            role == KioskRole.GATE && state.screen == KioskScreen.LOST_FOUND -> {
                                LostFoundCreateScreen(
                                    description = state.lfDescription,
                                    location = state.lfLocation,
                                    finder = state.lfFinder,
                                    finderMobile = state.lfFinderMobile,
                                    foundAt = state.lfFoundAt,
                                    busy = state.lfBusy,
                                    onDescription = viewModel::updateLfDescription,
                                    onLocation = viewModel::updateLfLocation,
                                    onFinder = viewModel::updateLfFinder,
                                    onFinderMobile = viewModel::updateLfFinderMobile,
                                    onFoundAt = viewModel::updateLfFoundAt,
                                    onSubmit = viewModel::submitLostFound,
                                    onBack = viewModel::closeGuardTool,
                                )
                            }
                            role == KioskRole.GATE -> {
                                Column(
                                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier.fillMaxSize(),
                                ) {
                                    if (state.step == 1) {
                                        GuardToolRow(
                                            onHistory = viewModel::openHistory,
                                            onCourier = viewModel::openCourier,
                                            onCheckout = viewModel::openCheckout,
                                            onLostFound = viewModel::openLostFound,
                                        )
                                    }
                                    StepDots(current = state.step)
                                    AnimatedContent(
                                        targetState = state.step,
                                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                                        label = "kiosk-step",
                                        modifier = if (compact) {
                                            Modifier.fillMaxWidth()
                                        } else {
                                            Modifier
                                                .weight(1f)
                                                .verticalScroll(rememberScrollState())
                                        },
                                    ) { step ->
                                        KioskStep(
                                            step = step,
                                            state = state,
                                            viewModel = viewModel,
                                        )
                                    }
                                }
                            }
                            role == KioskRole.HOST -> HostHomeScreen(
                                displayName = state.meDisplayName,
                                schoolId = state.schoolId,
                                staffId = state.meStaffId,
                                pending = state.pendingVisits,
                                active = state.hostActiveVisits,
                                photos = state.pendingPhotos,
                                afterHours = state.afterHours,
                                busy = state.hostBusy,
                                rejectingVisitId = state.rejectingVisitId,
                                rejectReason = state.rejectReason,
                                compact = compact,
                                onRefresh = viewModel::refreshHostPending,
                                onApprove = viewModel::approvePending,
                                onStartReject = viewModel::startReject,
                                onPickRejectReason = viewModel::pickRejectReason,
                                onCancelReject = viewModel::cancelReject,
                                onConfirmReject = viewModel::confirmReject,
                                onMeetingDone = viewModel::meetingDone,
                                onShowAfterHours = viewModel::toggleAfterHoursPanel,
                                showingAfterHours = state.showingAfterHours,
                            )
                            else -> UnsupportedRoleScreen(
                                role = state.meRole,
                                compact = compact,
                                onLogout = viewModel::logout,
                            )
                        }
                    }
                    } // end non-guard
                }
            }
            DemoWatermark(
                label = state.watermark,
                modifier = Modifier.align(Alignment.BottomStart),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (compact) 16.dp else 24.dp),
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
}

@Composable
private fun KioskStep(
    step: Int,
    state: KioskUiState,
    viewModel: KioskViewModel,
) {
    when (step) {
        1 -> VisitorTypeStep(
            selectedType = state.draft.visitorType,
            schoolName = state.schoolName,
            clockLabel = state.clockLabel,
            recent = state.recent,
            gates = state.gates,
            showPrefill = !state.hideDemoStory,
            onSelectType = viewModel::selectVisitorType,
            onPrefill = viewModel::prefillSample,
            onPickup = viewModel::openPickup,
            onContinue = viewModel::continueFromStep1,
        )
        2 -> VisitorDetailsStep(
            draft = state.draft,
            hosts = state.hosts,
            errors = state.fieldErrors,
            autofetchHint = state.autofetchHint,
            autofetchBusy = state.autofetchBusy,
            onName = viewModel::updateName,
            onMobile = viewModel::updateMobile,
            onCompany = viewModel::updateCompany,
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
            onAgreeConsent = viewModel::agreeGateConsent,
            onDeclineConsent = viewModel::declineGateConsent,
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
            afterHoursHint = state.afterHours,
            onRefresh = { viewModel.refreshVisit() },
            onDemoApprove = viewModel::demoApprove,
            onCheckIn = viewModel::scanCheckIn,
            onCheckOut = viewModel::scanCheckOut,
            onLoadStory = viewModel::loadStoryPass,
            onNewVisitor = viewModel::registerAnother,
            showStory = !state.hideDemoStory,
        )
    }
}

@Composable
private fun KioskHeader(
    schoolName: String,
    schoolId: String,
    gateName: String,
    gates: List<com.satcop.smartvisitor.kiosk.data.model.Gate>,
    clockLabel: String,
    dataSource: DataSource,
    displayName: String,
    compact: Boolean,
    showGateMenu: Boolean,
    showPickup: Boolean,
    pickupOpen: Boolean,
    onSelectGate: (String) -> Unit,
    onLogout: () -> Unit,
    onPickup: () -> Unit,
    onClosePickup: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val identity = listOf(displayName, schoolId).filter { it.isNotBlank() }.joinToString(" · ")
    val subtitle = identity.ifBlank { "$schoolName · Gate check-in" }
    if (compact) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShieldMark()
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Satcop Smart Visitor",
                        color = KioskColors.text,
                        fontSize = 16.sp,
                        fontFamily = KioskFont,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = subtitle,
                        color = KioskColors.textMuted,
                        fontSize = 11.sp,
                        fontFamily = KioskFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SourcePill(live = dataSource == DataSource.LIVE)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showGateMenu) {
                    Box {
                        GatePill(name = gateName, onClick = { menuOpen = true })
                        GateMenu(
                            expanded = menuOpen,
                            gates = gates,
                            onDismiss = { menuOpen = false },
                            onSelectGate = onSelectGate,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                KioskGhostButton(
                    text = "Log out",
                    onClick = onLogout,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (showPickup) {
                    KioskGhostButton(
                        text = "Pickup",
                        onClick = onPickup,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                if (pickupOpen) {
                    KioskGhostButton(
                        text = "Register",
                        onClick = onClosePickup,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    } else {
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
                        text = subtitle,
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
                SourcePill(live = dataSource == DataSource.LIVE)
                if (showGateMenu) {
                    Box {
                        GatePill(name = gateName, onClick = { menuOpen = true })
                        GateMenu(
                            expanded = menuOpen,
                            gates = gates,
                            onDismiss = { menuOpen = false },
                            onSelectGate = onSelectGate,
                        )
                    }
                    Text(
                        text = clockLabel.ifBlank { "—" },
                        color = KioskColors.textMuted,
                        fontSize = 13.sp,
                        fontFamily = KioskFont,
                    )
                }
                KioskGhostButton(
                    text = "Log out",
                    onClick = onLogout,
                    modifier = Modifier.heightIn(min = 48.dp),
                )
                if (showPickup) {
                    KioskGhostButton(
                        text = "Pickup",
                        onClick = onPickup,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
                if (pickupOpen) {
                    KioskGhostButton(
                        text = "Register",
                        onClick = onClosePickup,
                        modifier = Modifier.heightIn(min = 48.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GateMenu(
    expanded: Boolean,
    gates: List<com.satcop.smartvisitor.kiosk.data.model.Gate>,
    onDismiss: () -> Unit,
    onSelectGate: (String) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
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
                    onDismiss()
                },
            )
        }
    }
}

