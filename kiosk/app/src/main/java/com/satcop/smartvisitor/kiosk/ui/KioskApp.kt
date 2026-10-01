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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
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
import kotlinx.coroutines.flow.StateFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.guardpatrol.ui.GuardPatrolApp
import com.satcop.smartvisitor.kiosk.ui.components.GatePill
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.ShieldMark
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.components.ToastBanner
import com.satcop.smartvisitor.kiosk.ui.steps.OutcomeStep
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorFormStep
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorNumberStep
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AvStage
import com.satcop.smartvisitor.kiosk.ui.steps.VisitorTypeStep
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.face.FaceLoginPhase
import com.satcop.smartvisitor.kiosk.ui.face.FaceLoginScreen
import com.satcop.smartvisitor.kiosk.ui.apple.GateTodayScreen
import com.satcop.smartvisitor.kiosk.ui.apple.HostInboxScreen
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import kotlinx.coroutines.delay

@Composable
fun KioskApp(
    viewModel: KioskViewModel = viewModel(factory = KioskViewModel.factory()),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? Activity
    HostNotifyEffects(viewModel = viewModel, state = state)
    BackHandler {
        val consumed = viewModel.onSystemBack()
        if (!consumed) {
            activity?.finishAffinity()
        }
    }
    // Login draws its own full-bleed teal header under the status bar.
    val lockUp = !(state.signedIn && FaceGateMachine.canShowData(state.gateStage)) &&
        state.gateStage == GateStage.FACE_PENDING &&
        FaceLockOrder.showLock(com.satcop.smartvisitor.kiosk.data.api.AppAuth.session.isSignedIn, com.satcop.smartvisitor.kiosk.data.api.AppAuth.session.faceVerified, state.facePhase == FaceLoginPhase.HUB)
    val onPasswordLogin = lockUp || !(state.signedIn && FaceGateMachine.canShowData(state.gateStage)) &&
        state.screen != KioskScreen.FACE_LOGIN && state.gateStage != GateStage.FACE_PENDING
    // Board 04: while the face capture step is up the whole screen (incl. status bar area) is dark teal.
    val faceCaptureUp = !(state.signedIn && FaceGateMachine.canShowData(state.gateStage)) &&
        (state.screen == KioskScreen.FACE_LOGIN || state.gateStage == GateStage.FACE_PENDING) &&
        (state.facePhase == FaceLoginPhase.CAPTURE_VERIFY || state.facePhase == FaceLoginPhase.CAPTURE_ENROLL)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (faceCaptureUp) com.satcop.smartvisitor.kiosk.ui.theme.AppleDark.bg else if (lockUp) KioskColors.primary else KioskColors.bg)
            .then(if (onPasswordLogin) Modifier else Modifier.statusBarsPadding())
            .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
            .imePadding(),
    ) {
        // AC-PH1/PH2: always phone-portrait compact — ignore tablet width branch.
        val compact = true
        val hPad = FormTokens.ScreenHPad
        val vPad = 12.dp
        val cardHPad = FormTokens.ScreenHPad
        val cardVPad = 16.dp
        CompositionLocalProvider(LocalKioskCompact provides compact) {
            // Crashfix 1045/1046: outer phone verticalScroll only for password login.
            // FACE_LOGIN has its own scroll + CameraX — nesting crashes on Face login tap.
            // Signed-in roles own their own scroll (Gate/Host/Guard).
            // Hard face gate: role screens compose ONLY when the gate machine says VERIFIED.
            val signedIn = state.signedIn && FaceGateMachine.canShowData(state.gateStage)
            val faceStage = state.gateStage == GateStage.FACE_PENDING
            val faceLogin = !signedIn && (state.screen == KioskScreen.FACE_LOGIN || faceStage)
            val outerPhoneScroll = compact && !signedIn && !faceLogin && !lockUp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (outerPhoneScroll) Modifier else Modifier.fillMaxSize())
                    .widthIn(max = 1180.dp)
                    .align(Alignment.TopCenter)
                    .then(
                        if (outerPhoneScroll) Modifier.verticalScroll(rememberScrollState())
                        else Modifier,
                    )
                    .then(
                        if (signedIn || !faceLogin || lockUp) Modifier // login owns its full-bleed teal header
                        else Modifier.padding(horizontal = hPad, vertical = vPad),
                    )
            ) {
                val faceCtx = LocalContext.current
                if (lockUp) {
                    // 1075: lock screen FIRST (drawn from the login response); the camera opens only from its button.
                    com.satcop.smartvisitor.kiosk.ui.guardhome.LockScreen(
                        displayName = state.meDisplayName, schoolName = state.schoolName, loading = false, error = null,
                        onClockIn = viewModel::startFaceVerify, onSignOut = viewModel::logout,
                        // 1079 D4: with more than one gate duty the guard picks the gate BEFORE the camera (sent as gateId on check-in).
                        gateChoices = state.dutyGates.filter { !it.id.isNullOrBlank() }.map { (it.id ?: "") to (it.name ?: "") },
                        chosenGateId = state.chosenGateId,
                        onChooseGate = viewModel::chooseGate,
                    )
                } else if (!signedIn) {
                    if (state.screen == KioskScreen.FACE_LOGIN || faceStage) {
                        // Board 04: the capture step is a full-bleed dark teal screen, not a card. HUB/CONSENT keep the card.
                        val faceCapture = state.facePhase == FaceLoginPhase.CAPTURE_VERIFY ||
                            state.facePhase == FaceLoginPhase.CAPTURE_ENROLL
                        Box(
                            modifier = if (faceCapture) {
                                Modifier
                                    .fillMaxSize()
                                    .background(com.satcop.smartvisitor.kiosk.ui.theme.AppleDark.bg)
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            } else {
                                Modifier
                                    .fillMaxWidth()
                                    .shadow(24.dp, CardShape, ambientColor = androidx.compose.ui.graphics.Color(0x59000000))
                                    .clip(CardShape)
                                    .background(KioskColors.card)
                                    .border(1.dp, KioskColors.border, CardShape)
                                    .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 16.dp else 28.dp)
                            },
                        ) {
                            FaceLoginScreen(
                                phase = state.facePhase,
                                username = state.loginUsername,
                                enrolled = state.faceEnrolled,
                                consentAgreed = state.faceConsentAgreed,
                                consentAt = state.faceConsentAt,
                                busy = state.faceBusy,
                                message = state.faceMessage,
                                messageIsError = state.faceError,
                                onUsername = viewModel::updateFaceUsername,
                                onAgreeConsent = viewModel::agreeFaceConsent,
                                onDeclineConsent = viewModel::declineFaceConsent,
                                onStartEnroll = viewModel::startFaceEnroll,
                                onStartVerify = viewModel::startFaceVerify,
                                onCaptured = viewModel::onFaceCaptured,
                                onCancelCapture = viewModel::cancelFaceCapture,
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
                            onFaceLogin = { viewModel.openFaceLogin(faceCtx) },
                        )
                    }
                } else {
                    val role = state.homeRole()
                    if (role == KioskRole.GUARD || role == KioskRole.GATE) {
                        // 1077: ONE role. EVERY duty goes through the clock-in gate (lock, business-date lock, shift-end prompt, card).
                        val guardHome: com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeViewModel =
                            viewModel(key = "guard-home-${state.sessionEpoch}")
                        val patrolVm: com.satcop.smartvisitor.kiosk.guardpatrol.ui.GuardPatrolViewModel =
                            viewModel(key = "guard-patrol-${state.sessionEpoch}")
                        com.satcop.smartvisitor.kiosk.ui.guardhome.GuardClockInGate(
                            controller = guardHome.controller,
                            displayName = state.meDisplayName,
                            schoolName = state.schoolName,
                            onLogout = viewModel::logout,
                            initialCheckInRow = state.clockInRow,
                            onInitialRowConsumed = viewModel::consumeClockInRow,
                            initialVerifiedCard = state.verifiedCard,
                            verifyOnlySession = state.verifyOnlySession,
                            attendanceRecheck = state.attendanceRecheck,
                            gateChoices = state.dutyGates.filter { !it.id.isNullOrBlank() }.map { (it.id ?: "") to (it.name ?: "") },
                            chosenGateId = state.chosenGateId,
                            onChooseGate = viewModel::chooseGate,
                            patrolCalls = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.patrolCallsAllowed(state.dutyAreas),
                        ) { requestLogout ->
                    if (state.screen == KioskScreen.COURIER) {
                        CourierLogScreen(
                            company = state.courierCompany,
                            tracking = state.courierTracking,
                            packageType = state.courierPackageType,
                            gateLabel = state.courierGateLabel,
                            collectedBy = state.courierCollectedBy,
                            note = state.courierNote,
                            busy = state.courierBusy,
                            recent = state.courierRecent,
                            packagePhotoJpeg = state.courierPackagePhotoJpeg,
                            onCompany = viewModel::updateCourierCompany,
                            onTracking = viewModel::updateCourierTracking,
                            onPackageType = viewModel::updateCourierPackageType,
                            onGateLabel = viewModel::updateCourierGateLabel,
                            onCollectedBy = viewModel::updateCourierCollectedBy,
                            onNote = viewModel::updateCourierNote,
                            onPackagePhoto = viewModel::setCourierPackagePhoto,
                            onClearPackagePhoto = viewModel::clearCourierPackagePhoto,
                            onReceive = viewModel::receiveCourier,
                            onHandOver = viewModel::handOverCourier,
                            onBack = viewModel::closeGuardTool,
                        )
                    } else if (state.screen == KioskScreen.LOST_FOUND) {
                        LostFoundCreateScreen(
                            description = state.lfDescription,
                            location = state.lfLocation,
                            finder = state.lfFinder,
                            finderMobile = state.lfFinderMobile,
                            foundAt = state.lfFoundAt,
                            itemType = state.lfItemType,
                            photo = state.lfPhoto,
                            busy = state.lfBusy,
                            onDescription = viewModel::updateLfDescription,
                            onLocation = viewModel::updateLfLocation,
                            onFinder = viewModel::updateLfFinder,
                            onFinderMobile = viewModel::updateLfFinderMobile,
                            onFoundAt = viewModel::updateLfFoundAt,
                            onItemType = viewModel::updateLfItemType,
                            onPhoto = viewModel::setLfPhoto,
                            onSubmit = viewModel::submitLostFound,
                            onBack = viewModel::closeGuardTool,
                        )
                    } else {
                        DutyHome(state, viewModel, guardHome, patrolVm, compact, cardHPad, cardVPad, requestLogout)
                    }
                        }
                    } else if (role == KioskRole.HOST) {
                        HostInboxScreen(
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
                            onRefresh = viewModel::refreshHostPending,
                            refreshing = state.hostBusy,
                            onPullRefresh = viewModel::refreshHostNow,
                            focusVisitId = state.hostFocusVisitId,
                            onApprove = viewModel::approvePending,
                            onStartReject = viewModel::startReject,
                            onPickRejectReason = viewModel::pickRejectReason,
                            onCancelReject = viewModel::cancelReject,
                            onConfirmReject = viewModel::confirmReject,
                            onMeetingDone = viewModel::meetingDone,
                            onShowAfterHours = viewModel::toggleAfterHoursPanel,
                            showingAfterHours = state.showingAfterHours,
                            onLogout = viewModel::logout,
                            notice = state.hostNotice,
                            onDismissNotice = viewModel::dismissHostNotice,
                            gateNames = state.gates.associate { it.id to it.name },
                        )
                    } else {
                        UnsupportedRoleScreen(role = state.meRole, compact = compact, onLogout = viewModel::logout)
                    } // end non-guard
                }
            }
            // Keep toast above the pinned bottom nav (64dp + raised centre + system inset)
            val aboveNav = if (signedIn) 96.dp else 0.dp
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(if (compact) 16.dp else 24.dp)
                    .padding(bottom = aboveNav),
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
            clock = viewModel.clock,
            recent = state.recent,
            gates = state.gates,
            showPrefill = !state.hideDemoStory,
            onSelectType = viewModel::selectVisitorType,
            onPrefill = viewModel::prefillSample,
            onPickup = viewModel::openPickup,
            onContinue = viewModel::continueFromStep1,
        )
        2 -> if (state.addVisitor.stage == AvStage.NUMBER) {
            AddVisitorNumberStep(
                state = state.addVisitor,
                onMobile = viewModel::avTypeMobile,
                onContinue = viewModel::avContinue,
                onCancel = viewModel::cancelAddVisitor,
                onCloseNotice = viewModel::avCloseNotice,
                onCheckout = viewModel::avCheckout,
                onCheckInOpen = viewModel::avCheckInOpen,
            )
        } else {
            AddVisitorFormStep(
                state = state.addVisitor,
                draft = state.draft,
                hosts = state.hosts,
                hostsLoading = !state.loaded,
                errors = state.fieldErrors,
                livePhoto = state.livePhoto,
                idImage = state.idImage,
                submitting = state.submitting,
                blocked = state.blocked,
                blacklistHit = state.blacklistHit,
                onIdType = viewModel::selectIdType,
                onIdNumber = viewModel::updateIdNumber,
                onIdTypeName = viewModel::updateIdTypeName,
                onLivePhoto = viewModel::setLivePhoto,
                onIdImage = viewModel::setIdImage,
                onSubmit = viewModel::submitRegistration,
                onCancel = viewModel::cancelAddVisitor,
                onKind = viewModel::avChooseKind,
                onName = viewModel::updateName,
                onCompany = viewModel::updateCompany,
                onPurpose = viewModel::updatePurpose,
                onHost = viewModel::selectHost,
                onDepartment = viewModel::selectDepartment,
                onUseSavedId = viewModel::avUseSavedId,
                onBack = viewModel::back,
                onSwitchKind = viewModel::avSwitchKind,
                onOtpId = viewModel::avSetOtpId,
            )
        }
        else -> OutcomeStep(
            draft = state.draft,
            visit = state.createdVisit,
            hosts = state.hosts,
            gates = state.gates,
            blacklistHit = state.blacklistHit,
            dataSource = state.dataSource,
            afterHoursHint = state.afterHours,
            photo = state.livePhoto,
            onNewVisitor = viewModel::registerAnother,
        )
    }
}

/** The only reader of the per-second clock flow: a tick recomposes this Text, nothing else. */

/** 1077: what a clocked-in guard sees, by duty. Switcher only for both; no-duty guard gets the plain text + shared tools. */
@Composable
private fun DutyHome(
    state: KioskUiState,
    viewModel: KioskViewModel,
    guardHome: com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeViewModel,
    patrolVm: com.satcop.smartvisitor.kiosk.guardpatrol.ui.GuardPatrolViewModel,
    compact: Boolean,
    cardHPad: androidx.compose.ui.unit.Dp,
    cardVPad: androidx.compose.ui.unit.Dp,
    requestLogout: () -> Unit,
) {
    val areas = state.dutyAreas ?: setOf(com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.PATROL)
    val active = state.activeArea
    val patrolState by patrolVm.state.collectAsStateWithLifecycle()
    val incidentUp = patrolState.screen == com.satcop.smartvisitor.kiosk.guardpatrol.ui.GuardPatrolScreen.INCIDENT
    var findUp by remember { mutableStateOf(false) }
    // resume = home open: re-read duty
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshDutyOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val openIncident = { patrolVm.openIncidentReport() }
    Column(Modifier.fillMaxSize()) {
        if (state.dutyPending != null) com.satcop.smartvisitor.kiosk.ui.duty.DutyUpdatedBanner(viewModel::applyDutyUpdate)
        if (areas.size > 1) com.satcop.smartvisitor.kiosk.ui.duty.DutyAreaSwitcher(active, viewModel::switchArea)
        active?.let { a -> state.shiftNotices[a]?.let { com.satcop.smartvisitor.kiosk.ui.duty.ShiftInactiveBanner(it) } }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                incidentUp && active != com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.PATROL -> {
                    androidx.activity.compose.BackHandler { patrolVm.cancelIncidentReport() }
                    com.satcop.smartvisitor.kiosk.guardpatrol.ui.IncidentReportScreen(
                        state = patrolState,
                        onType = patrolVm::setIncidentType,
                        onNotes = patrolVm::setIncidentNotes,
                        onCheckpoint = patrolVm::setIncidentCheckpoint,
                        onPhoto = patrolVm::setIncidentPhoto,
                        onClearPhoto = patrolVm::clearIncidentPhoto,
                        onSubmit = patrolVm::submitIncident,
                        onCancel = patrolVm::cancelIncidentReport,
                    )
                }
                active == com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.GATE ->
                    GateDeskArea(state, viewModel, compact, cardHPad, cardVPad, requestLogout, openIncident)
                active == com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.PATROL ->
                    com.satcop.smartvisitor.kiosk.guardpatrol.ui.GuardPatrolApp(
                        vm = patrolVm,
                        onExit = null,
                        onCourier = viewModel::openCourier,
                        onLostFound = viewModel::openLostFound,
                        onLogout = requestLogout,
                        homeVm = guardHome,
                        onDutyRefresh = viewModel::refreshDutyNow,
                        newActionsEnabled = state.newActionsEnabled(),
                        // 1079: patrol-only duty = Guard Today + shared tools only (no Find visitor, Courier log, Desk tab).
                        gateActions = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.gateActionsVisible(state.dutyAreas),
                        headerLine = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.dutyHeader(
                            listOf(state.meDisplayName, state.schoolName).filter { it.isNotBlank() }.joinToString(" · "),
                            com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.PATROL, state.dutyAssignments,
                        ),
                    )
                findUp -> {
                    androidx.activity.compose.BackHandler { findUp = false }
                    com.satcop.smartvisitor.kiosk.ui.guardhome.GuardFindScreen(guardHome.controller)
                }
                else -> com.satcop.smartvisitor.kiosk.ui.duty.NoDutyHome(
                    displayName = state.meDisplayName,
                    onIncident = openIncident,
                    onLostFound = viewModel::openLostFound,
                    onCourier = viewModel::openCourier,
                    onFindVisitor = { findUp = true; guardHome.controller.openFind() },
                    onClockOut = { guardHome.controller.openPanel(com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode.CLOCK_OUT) },
                    onSignOut = requestLogout,
                )
            }
        }
    }
}

/** 1077: the Gate desk area (moved unchanged from the old Gate-role branch). Lives inside GuardClockInGate. */
@Composable
private fun GateDeskArea(
    state: KioskUiState,
    viewModel: KioskViewModel,
    compact: Boolean,
    cardHPad: androidx.compose.ui.unit.Dp,
    cardVPad: androidx.compose.ui.unit.Dp,
    requestLogout: () -> Unit,
    onReportIncident: () -> Unit,
) {
    if (state.screen == KioskScreen.HOME) {
                        // AC-APP1: Gate shell ALWAYS on HOME (not only step==1).
                        // Registration step>1 stays under pinned NavigationBar.
                        GateTodayScreen(
                            gateName = state.dutyGateName?.takeIf { it.isNotBlank() } ?: state.selectedGate?.name ?: "Main Gate",
                            recent = state.recent,
                            onAddVisitor = viewModel::startAddVisitor,
                            onCourier = viewModel::openCourier,
                            onHistory = viewModel::openHistory,
                            onCheckout = viewModel::openCheckout,
                            onLostFound = viewModel::openLostFound,
                            onPickup = viewModel::openPickup,
                            onLogout = requestLogout,
                            displayName = state.meDisplayName,
                            insideList = state.checkoutInside,
                            historyEvents = state.historyEvents,
                            hostNames = state.hosts.associate { it.id to it.name },
                            onRefreshLists = {
                                viewModel.refreshCheckoutInside()
                                viewModel.refreshHistory()
                            },
                            onSelectHistory = viewModel::selectHistory,
                            onReportIncident = onReportIncident,
                            newActionsEnabled = state.newActionsEnabled(),
                            onCheckoutVisit = { id ->
                                viewModel.selectCheckout(id)
                                viewModel.confirmCheckout()
                            },
                            registrationStep = state.step,
                            registrationContent = if (state.step > 1) {
                                {
                                    // 1061b: ONE horizontal margin for the whole step (dots, header, labels, inputs, host).
                                    Column(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = FormTokens.ScreenHPad)
                                            .padding(bottom = FormTokens.SectionGap),
                                    ) {
                                        KioskStep(
                                            step = state.step,
                                            state = state,
                                            viewModel = viewModel,
                                        )
                                    }
                                }
                            } else null,
                        )
    } else {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    KioskHeader(
                        schoolName = state.schoolName,
                        schoolId = state.schoolId,
                        gateName = state.selectedGate?.name ?: "Main Gate",
                        gates = state.gates,
                        clock = viewModel.clock,
                        dataSource = state.dataSource,
                        displayName = state.meDisplayName,
                        compact = compact,
                        showGateMenu = true,
                        showPickup = state.screen == KioskScreen.HOME,
                        pickupOpen = state.screen == KioskScreen.PICKUP,
                        onSelectGate = viewModel::selectGate,
                        onLogout = requestLogout,
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
                            state.screen == KioskScreen.PICKUP -> {
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
                            state.screen in setOf(KioskScreen.HISTORY, KioskScreen.HISTORY_DETAIL) -> {
                                HistoryScreen(
                                    events = state.historyEvents,
                                    filterToday = state.historyTodayOnly,
                                    filterKind = state.historyKindFilter,
                                    filterStatus = state.historyStatusFilter,
                                    busy = state.historyBusy,
                                    selected = state.historySelected,
                                    rejectReason = state.historyRejectReason,
                                    onToggleToday = viewModel::toggleHistoryToday,
                                    onKind = viewModel::setHistoryKind,
                                    onStatus = viewModel::setHistoryStatus,
                                    onSelect = viewModel::selectHistory,
                                    onClearDetail = viewModel::clearHistoryDetail,
                                    onRefresh = viewModel::refreshHistory,
                                    onBack = viewModel::closeGuardTool,
                                )
                            }
                            state.screen == KioskScreen.COURIER -> {
                                CourierLogScreen(
                            company = state.courierCompany,
                            tracking = state.courierTracking,
                            packageType = state.courierPackageType,
                            gateLabel = state.courierGateLabel,
                            collectedBy = state.courierCollectedBy,
                            note = state.courierNote,
                            busy = state.courierBusy,
                            recent = state.courierRecent,
                            packagePhotoJpeg = state.courierPackagePhotoJpeg,
                            onCompany = viewModel::updateCourierCompany,
                            onTracking = viewModel::updateCourierTracking,
                            onPackageType = viewModel::updateCourierPackageType,
                            onGateLabel = viewModel::updateCourierGateLabel,
                            onCollectedBy = viewModel::updateCourierCollectedBy,
                            onNote = viewModel::updateCourierNote,
                            onPackagePhoto = viewModel::setCourierPackagePhoto,
                            onClearPackagePhoto = viewModel::clearCourierPackagePhoto,
                            onReceive = viewModel::receiveCourier,
                            onHandOver = viewModel::handOverCourier,
                            onBack = viewModel::closeGuardTool,
                        )
                            }
                            state.screen == KioskScreen.CHECKOUT -> {
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
                            state.screen == KioskScreen.LOST_FOUND -> {
                                LostFoundCreateScreen(
                                    description = state.lfDescription,
                                    location = state.lfLocation,
                                    finder = state.lfFinder,
                                    finderMobile = state.lfFinderMobile,
                                    foundAt = state.lfFoundAt,
                                    itemType = state.lfItemType,
                                    photo = state.lfPhoto,
                                    busy = state.lfBusy,
                                    onDescription = viewModel::updateLfDescription,
                                    onLocation = viewModel::updateLfLocation,
                                    onFinder = viewModel::updateLfFinder,
                                    onFinderMobile = viewModel::updateLfFinderMobile,
                                    onFoundAt = viewModel::updateLfFoundAt,
                                    onItemType = viewModel::updateLfItemType,
                                    onPhoto = viewModel::setLfPhoto,
                                    onSubmit = viewModel::submitLostFound,
                                    onBack = viewModel::closeGuardTool,
                                )
                            }
                            !state.loaded -> {
                                Text(
                                    "Loading gate…",
                                    color = KioskColors.textMuted,
                                    fontFamily = KioskFont,
                                )
                            }
                            true -> {
                                // Should not reach for HOME (shell above). Subflows only.
                                Text(
                                    "Returning to Gate home…",
                                    color = KioskColors.textMuted,
                                    fontFamily = KioskFont,
                                )
                                androidx.compose.runtime.LaunchedEffect(Unit) {
                                    viewModel.closeGuardTool()
                                }
                            }
                            else -> UnsupportedRoleScreen(
                                role = state.meRole,
                                compact = compact,
                                onLogout = viewModel::logout,
                            )
                        }
                    }
        }
    }
}

@Composable
fun ClockText(
    clock: StateFlow<String>,
    color: androidx.compose.ui.graphics.Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    val label by clock.collectAsStateWithLifecycle()
    Text(text = label.ifBlank { "—" }, color = color, fontSize = fontSize, fontFamily = KioskFont, modifier = modifier)
}

@Composable
private fun KioskHeader(
    schoolName: String,
    schoolId: String,
    gateName: String,
    gates: List<com.satcop.smartvisitor.kiosk.data.model.Gate>,
    clock: StateFlow<String>,
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
                    ClockText(
                        clock = clock,
                        color = KioskColors.textMuted,
                        fontSize = 13.sp,
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


/**
 * Host new-visitor plumbing at the composition root:
 * - lifecycle => foreground 3 s polling + refresh on resume, slower when backgrounded
 * - POST_NOTIFICATIONS request (Android 13+) once a Host session is face-verified
 * - foreground popup, system notification when backgrounded, tap => Inbox at that visit
 */
@Composable
private fun HostNotifyEffects(viewModel: KioskViewModel, state: KioskUiState) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val isHost = state.signedIn && state.homeRole() == KioskRole.HOST &&
        FaceGateMachine.canShowData(state.gateStage)
    var foreground by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) { viewModel.bindNotifyStore(context) }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.onAppResumed()
            when (e) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> { foreground = true; viewModel.setHostForeground(true) }
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> { foreground = false; viewModel.setHostForeground(false) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(isHost) {
        if (isHost) {
            com.satcop.smartvisitor.kiosk.ui.notify.HostAlertNotifier.ensureChannel(context)
            if (com.satcop.smartvisitor.kiosk.ui.notify.HostAlertNotifier.needsPermissionRequest(context)) {
                permLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // System notification for every new visitor while the app is not in the foreground.
    LaunchedEffect(isHost) {
        if (!isHost) return@LaunchedEffect
        viewModel.newVisitorEvents.collect { item ->
            if (!foreground) com.satcop.smartvisitor.kiosk.ui.notify.HostAlertNotifier.show(context, item)
        }
    }

    // Deep link from a tapped notification (intent extra) — honoured only once face-verified as Host.
    val activity = context as? Activity
    val pendingOpen = remember { mutableStateOf(activity?.intent?.getStringExtra(
        com.satcop.smartvisitor.kiosk.ui.notify.HostAlertNotifier.EXTRA_OPEN_VISIT_ID)) }
    LaunchedEffect(isHost, pendingOpen.value) {
        val vid = pendingOpen.value
        if (isHost && !vid.isNullOrBlank()) {
            viewModel.openHostVisit(vid)
            pendingOpen.value = null
            activity?.intent?.removeExtra(com.satcop.smartvisitor.kiosk.ui.notify.HostAlertNotifier.EXTRA_OPEN_VISIT_ID)
        }
    }
    androidx.compose.runtime.DisposableEffect(activity) {
        val a = activity as? androidx.activity.ComponentActivity
        val l = androidx.core.util.Consumer<android.content.Intent> { i ->
            pendingOpen.value = i.getStringExtra(com.satcop.smartvisitor.kiosk.ui.notify.HostAlertNotifier.EXTRA_OPEN_VISIT_ID)
        }
        a?.addOnNewIntentListener(l)
        onDispose { a?.removeOnNewIntentListener(l) }
    }

    val head = state.hostAlerts.firstOrNull()
    if (isHost && head != null && foreground) {
        com.satcop.smartvisitor.kiosk.ui.notify.HostAlertDialog(
            alert = head,
            more = state.hostAlerts.size - 1,
            onApprove = { viewModel.approveFromAlert(head) },
            onReject = { viewModel.rejectFromAlert(head) },
            onLater = viewModel::dismissHostAlert,
        )
    }
}
