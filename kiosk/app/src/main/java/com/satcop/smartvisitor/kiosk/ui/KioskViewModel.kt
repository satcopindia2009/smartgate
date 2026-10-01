package com.satcop.smartvisitor.kiosk.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.satcop.smartvisitor.kiosk.data.api.AppAuth
import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.api.LoginErrors
import com.satcop.smartvisitor.kiosk.data.api.LoginInput
import com.satcop.smartvisitor.kiosk.data.face.FaceErrors
import com.satcop.smartvisitor.kiosk.data.face.FaceImage
import com.satcop.smartvisitor.kiosk.data.face.LocalFaceTemplateStore
import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.fixture.HybridKioskRepository
import com.satcop.smartvisitor.kiosk.data.model.AfterHoursCopy
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AuthorizedPickup
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.CampusHours
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.HostFeedItem
import com.satcop.smartvisitor.kiosk.data.store.MemoryStringStore
import com.satcop.smartvisitor.kiosk.data.store.SharedPrefsStringStore
import com.satcop.smartvisitor.kiosk.data.store.StringStore
import com.satcop.smartvisitor.kiosk.ui.notify.HostFeedTracker
import com.satcop.smartvisitor.kiosk.ui.notify.PollBackoff
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.satcop.smartvisitor.kiosk.data.fixture.LocalVisitStore
import com.satcop.smartvisitor.kiosk.data.model.LostFoundCreate
import com.satcop.smartvisitor.kiosk.data.model.GuardHistoryEvent
import com.satcop.smartvisitor.kiosk.data.model.CourierEvent
import com.satcop.smartvisitor.kiosk.data.model.CourierCreate
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.PickupCreate
import com.satcop.smartvisitor.kiosk.data.model.PickupOut
import com.satcop.smartvisitor.kiosk.data.model.PickupReasons
import com.satcop.smartvisitor.kiosk.data.geo.CaptureGeo
import com.satcop.smartvisitor.kiosk.data.geo.GeoFenceCodes
import com.satcop.smartvisitor.kiosk.data.model.FaceEnrollRequest
import com.satcop.smartvisitor.kiosk.data.model.FaceVerifyRequest
import com.satcop.smartvisitor.kiosk.data.model.GateConsent
import com.satcop.smartvisitor.kiosk.data.model.StaffFaceConsent
import com.satcop.smartvisitor.kiosk.data.model.SchoolIds
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.model.StudentOut
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import com.satcop.smartvisitor.kiosk.data.repository.KioskRepository
import com.satcop.smartvisitor.kiosk.ui.face.FaceLoginPhase
import com.satcop.smartvisitor.kiosk.ui.components.ToastKind
import com.satcop.smartvisitor.kiosk.ui.media.PlaceholderBitmap
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import com.satcop.smartvisitor.kiosk.data.api.SessionExpiry
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KioskUiState(
    /** Face gate stage (1059). Role screens/data only when [GateStage.VERIFIED]. */
    val gateStage: GateStage = GateStage.SIGNED_OUT,
    /** Bumped on every logout so per-session ViewModels (Guard patrol) are never reused across users. */
    val sessionEpoch: Int = 0,
    val signedIn: Boolean = false,
    // 1059b: NO prefilled credentials. In 1059 these were prefilled but invisible (black on black), so anything
    // the tester typed was APPENDED to "pranay.gate"/"PranayGate@2026" -> wrong credentials -> "cannot log in".
    val loginUsername: String = "",
    val loginPassword: String = "",
    val loginError: String? = null,
    val loginBusy: Boolean = false,
    val step: Int = 1,
    val schoolName: String = "",
    val schoolId: String = "",
    val timezone: String = DemoFixtures.SCHOOL_TZ,
    val watermark: String = "",
    val dataSource: DataSource = DataSource.OFFLINE,
    val meDisplayName: String = "",
    val meRole: String = "",
    /** 1076: card of a role whose clock-in was refused by the server (face-verify only). */
    val verifyOnlySession: Boolean = false,
    /** 1077 duty: null = not read yet; empty = no duty. */
    val dutyAreas: Set<com.satcop.smartvisitor.kiosk.ui.duty.DutyArea>? = null,
    val activeArea: com.satcop.smartvisitor.kiosk.ui.duty.DutyArea? = null,
    val dutyRevision: String? = null,
    val dutyPending: com.satcop.smartvisitor.kiosk.data.model.DutyMe? = null,
    val dutyGates: List<com.satcop.smartvisitor.kiosk.data.model.DutyGate> = emptyList(),
    val chosenGateId: String? = null,
    val dutyGateName: String? = null,
    /** Bumped when the server says NOT_CLOCKED_IN: the guard gate re-reads attendance and returns to the lock. */
    val attendanceRecheck: Int = 0,
    val verifiedCard: com.satcop.smartvisitor.kiosk.ui.guardhome.ClockResult? = null,
    val meStaffId: String = "",
    val gates: List<Gate> = emptyList(),
    val hosts: List<Staff> = emptyList(),
    val recent: List<InsideVisit> = emptyList(),
    val draft: RegistrationDraft = RegistrationDraft(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val toast: String? = null,
    val toastKind: ToastKind = ToastKind.INFO,
    val story: DemoStory = DemoFixtures.demoStory,
    val loaded: Boolean = false,
    val submitting: Boolean = false,
    val livePhoto: Bitmap? = null,
    val idImage: Bitmap? = null,
    val signature: Bitmap? = null,
    val blacklistHit: BlacklistEntry? = null,
    val blocked: Boolean = false,
    val createdVisit: VisitOut? = null,
    val outcomeBusy: Boolean = false,
    val screen: KioskScreen = KioskScreen.HOME,
    val hideDemoStory: Boolean = false,
    val pendingVisits: List<VisitOut> = emptyList(),
    val hostActiveVisits: List<VisitOut> = emptyList(),
    val pendingPhotos: Map<String, Bitmap> = emptyMap(),
    val hostBusy: Boolean = false,
    /** Persistent (non-toast) message for a failed host Approve/Reject; cleared on the next action or Back. */
    val hostNotice: String? = null,
    val rejectingVisitId: String? = null,
    val rejectReason: String? = null,
    val afterHours: Boolean = false,
    val showingAfterHours: Boolean = false,
    val pickupQuery: String = "",
    val students: List<StudentOut> = emptyList(),
    val authorizedPickup: List<AuthorizedPickup> = emptyList(),
    val selectedStudent: StudentOut? = null,
    val selectedCollector: AuthorizedPickup? = null,
    val pickupReason: String = "early",
    val pickupReasonOther: String = "",
    val activePickup: PickupOut? = null,
    val pickupBusy: Boolean = false,
    val historyEvents: List<GuardHistoryEvent> = emptyList(),
    val historyTodayOnly: Boolean = true,
    val historyKindFilter: String = "all",
    val historyStatusFilter: String = "all",
    val historySelected: GuardHistoryEvent? = null,
    /** Host's reason for the selected rejected visit (GET /visits/{id}); null until loaded. */
    val historyRejectReason: String? = null,
    val historyBusy: Boolean = false,
    val addVisitor: com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState(),
    val autofetchBusy: Boolean = false,
    val autofetchHint: String? = null,
    val courierCompany: String = "",
    val courierTracking: String = "",
    val courierPackageType: String = "Envelope",
    val courierGateLabel: String = "",
    val courierCollectedBy: String = "",
    val courierNote: String = "",
    val courierPackagePhotoJpeg: ByteArray? = null,
    val courierRecent: List<CourierEvent> = emptyList(),
    val courierBusy: Boolean = false,
    val checkoutInside: List<InsideVisit> = emptyList(),
    val checkoutSelectedId: String? = null,
    val checkoutBusy: Boolean = false,
    val lfDescription: String = "",
    val lfLocation: String = "",
    val lfFinder: String = "",
    val lfFinderMobile: String = "",
    val lfFoundAt: String = "",
    val lfItemType: String = "Found",
    val lfPhoto: Bitmap? = null,
    val lfBusy: Boolean = false,
    val faceEnrolled: Boolean = false,
    /** 1074: check-in response of the face step; the guard gate shows the "Checked In!" card from it, then clears it. */
    val clockInRow: com.satcop.smartvisitor.kiosk.data.model.AttendanceRow? = null,
    val faceConsentAgreed: Boolean = false,
    val faceConsentAt: String? = null,
    val faceConsentVersion: String? = null,
    /** From GET /schools/me — off|soft|restrict when Backend READY. */
    val geoFenceMode: String? = null,
    val faceBusy: Boolean = false,
    val faceMessage: String? = null,
    /** 1059b: faceMessage is an error (shown in error colour, capture stays open for retry). */
    val faceError: Boolean = false,
    val facePhase: FaceLoginPhase = FaceLoginPhase.HUB,
    /** Host: queue of new pending-visitor alerts (head = shown in the foreground popup). */
    val hostAlerts: List<HostFeedItem> = emptyList(),
    /** Host: visit id requested by tapping a system notification (opens Inbox at that visit). */
    val hostFocusVisitId: String? = null,
) {
    val selectedGate: Gate?
        get() = gates.firstOrNull { it.id == draft.gateId } ?: gates.firstOrNull()
}

class KioskViewModel(
    private val repository: KioskRepository = HybridKioskRepository(),
    private val liveApi: LiveVisitorApi = LiveVisitorApi(),
    private val addVisitorLookup: com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLookup =
        com.satcop.smartvisitor.kiosk.data.addvisitor.ContractLookup(),
) : ViewModel() {

    private var faceStore: LocalFaceTemplateStore? = null

    private val _state = MutableStateFlow(KioskUiState())
    val state: StateFlow<KioskUiState> = _state.asStateFlow()

    private val clockFmt = DateTimeFormatter.ofPattern(
        "EEE, d MMM, hh:mm:ss a",
        Locale.ENGLISH,
    )
    private var hostPollJob: Job? = null

    // --- Host new-visitor feed (Backend contract 2026-09-30) ---
    private var feedStore: StringStore = MemoryStringStore()
    private var feedTracker = HostFeedTracker(feedStore)
    private val pollBackoff = PollBackoff()
    @Volatile private var hostForeground = true
    @Volatile private var pollInFlight = false
    private var pollNow: CompletableDeferred<Unit>? = null
    private var feedUnsupported = false
    private var fallbackSeenPending: MutableSet<String>? = null
    private var lastFullRefreshMs = 0L
    /** The first poll after host login only finds what the Inbox already lists; no pop-up for that backlog. */
    private var hostFirstPollDone = false
    private val _newVisitorEvents = MutableSharedFlow<HostFeedItem>(extraBufferCapacity = 16)

    /** Emits every de-duplicated new pending visitor; the UI layer turns it into a system notification. */
    val newVisitorEvents: SharedFlow<HostFeedItem> get() = _newVisitorEvents

    /**
     * The per-second clock lives in its OWN flow, not in [KioskUiState]: a state copy every second recomposed the
     * whole app (1065-1068 emulator ANR suspicion). Only the tiny ClockText composables collect this.
     * MUST be declared before the init block: viewModelScope runs on Dispatchers.Main.immediate, so a coroutine
     * launched from init starts right away and would see this field as null (1070 launch crash).
     */
    private val _clock = MutableStateFlow("")
    val clock: StateFlow<String> = _clock.asStateFlow()

    init {
        // Cold start / new Activity = new session: never inherit a verified token from a warm process.
        AppAuth.session.clear()
        viewModelScope.launch { tickClock() }
        viewModelScope.launch {
            AppAuth.session.faceRequiredEvents.collect { onServerFaceRequired() }
        }
        viewModelScope.launch {
            AppAuth.session.dutyEvents.collect { code ->
                if (code == "NOT_CLOCKED_IN") _state.update { it.copy(attendanceRecheck = it.attendanceRecheck + 1) }
                else refreshDutyNow()
            }
        }
        viewModelScope.launch {
            AppAuth.session.sessionExpiredEvents.collect { endSession(ErrorCopy.SESSION_EXPIRED) }
        }
        // 1072 E: while the app stays open past the token cut-off (guard: 00:00 IST) sign out at that moment, not on the next tap.
        viewModelScope.launch { watchSessionExpiry() }
    }

    private suspend fun watchSessionExpiry() {
        while (true) {
            val s = AppAuth.session
            if (s.isExpired()) s.markExpired()
            delay(SessionExpiry.nextCheckDelayMs(s.expiresAtMs, System.currentTimeMillis()))
        }
    }

    /** App came back to the foreground: if the token's cut-off (guard: 00:00 IST) has passed, sign out now. */
    fun onAppResumed() {
        if (AppAuth.session.isExpired()) AppAuth.session.markExpired()
    }

    /**
     * The token is over (client clock passed sessionExpiresAt, or the server said 401 TOKEN_EXPIRED):
     * drop everything and show Login with a plain sentence. The next login needs password + face again.
     */
    private fun endSession(message: String) {
        if (!_state.value.signedIn && _state.value.gateStage == GateStage.SIGNED_OUT) return
        hostPollJob?.cancel()
        hostPollJob = null
        AppAuth.session.clear()
        val epoch = _state.value.sessionEpoch + 1
        _state.value = KioskUiState(
            sessionEpoch = epoch,
            gateStage = GateStage.SIGNED_OUT,
            loginError = message,
        )
        viewModelScope.launch { runCatching { repository.logout() } }
    }

    /** Server rejected the token with 403 FACE_REQUIRED -> force the face screen, hide all data. */
    private fun onServerFaceRequired() {
        val s = _state.value
        val next = FaceGateMachine.onServerFaceRequired(s.gateStage)
        if (next == s.gateStage) return
        hostPollJob?.cancel()
        hostPollJob = null
        _state.update {
            it.copy(
                gateStage = next,
                signedIn = false,
                screen = KioskScreen.FACE_LOGIN,
                facePhase = FaceLoginPhase.HUB,
                faceMessage = "Face verification required",
                faceBusy = false,
            )
        }
    }

    fun updateLoginUsername(value: String) {
        _state.update { it.copy(loginUsername = value, loginError = null) }
    }

    fun updateLoginPassword(value: String) {
        _state.update { it.copy(loginPassword = value, loginError = null) }
    }

    fun login() {
        // Phone keyboards capitalise ("Gate") and append a trailing space: normalise before sending.
        val username = LoginInput.normalizeUsername(_state.value.loginUsername)
        val password = _state.value.loginPassword
        if (username.isEmpty() || password.isEmpty()) {
            _state.update {
                it.copy(loginError = "Enter username and password")
            }
            return
        }
        if (_state.value.loginBusy) return
        viewModelScope.launch {
            _state.update { it.copy(loginBusy = true, loginError = null, toast = null) }
            try {
                val me = repository.login(username, password)
                if (KioskRole.isWebOnly(me.role)) {
                    // D1: Admin and security_head are web only. Never keep an admin token on the phone.
                    runCatching { repository.logout() }
                    AppAuth.session.clear()
                    _state.update {
                        it.copy(
                            gateStage = GateStage.SIGNED_OUT, signedIn = false, loginBusy = false,
                            loginPassword = "", loginError = ErrorCopy.ADMIN_USE_WEB, loginUsername = username,
                        )
                    }
                    return@launch
                }
                val session = AppAuth.session
                // AuthSession.faceVerified was already set from the server flags in LiveVisitorApi.login.
                val needsFace = !session.faceVerified
                if (!needsFace) {
                    // Server says this role is exempt/not enforced (gate/host/admin) or already verified: NO face step.
                    _state.update {
                        it.copy(
                            gateStage = GateStage.VERIFIED,
                            loginBusy = false,
                            loginError = null,
                            loginPassword = "",
                            loginUsername = username,
                        )
                    }
                    loadAfterLogin(me)
                    return@launch
                }
                // Face gate: password alone NEVER opens the app for a face-required role (guard).
                val store = faceStore
                val enrolled = withContext(Dispatchers.IO) { store?.isEnrolled(username) == true }
                _state.update {
                    it.copy(
                        gateStage = FaceGateMachine.onPasswordLogin(it.gateStage),
                        signedIn = false,
                        loginBusy = false,
                        loginError = null,
                        loginPassword = "",
                        loginUsername = username,
                        screen = KioskScreen.FACE_LOGIN,
                        // 1075: the LOCK SCREEN comes first (HUB phase of a half-open guard session = lock screen);
                        // the camera opens only from its CLOCK IN TO CONTINUE button. No data call before face-verify.
                        facePhase = FaceLoginPhase.HUB,
                        faceEnrolled = enrolled || it.faceEnrolled,
                        dutyGates = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.gatesFromAssignments(me.dutyAssignments),
                        chosenGateId = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.gatesFromAssignments(me.dutyAssignments)
                            .takeIf { g -> com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.needsGateChooser(g) }
                            ?.let { g -> com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.defaultGate(g)?.id },
                        dutyAreas = null, activeArea = null, dutyPending = null,
                        meDisplayName = me.displayName,
                        schoolName = me.schoolName?.takeIf { n -> n.isNotBlank() } ?: it.schoolName,
                        faceMessage = null,
                        faceError = false,
                        toast = null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        signedIn = false,
                        loginBusy = false,
                        loginError = LoginErrors.message(e),
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            hostPollJob?.cancel()
            hostPollJob = null
            runCatching { repository.logout() }
            AppAuth.session.clear()
                val epoch = _state.value.sessionEpoch + 1
            _state.value = KioskUiState(
                    sessionEpoch = epoch,
                gateStage = FaceGateMachine.onLogout(_state.value.gateStage),
            )
        }
    }

    private suspend fun loadAfterLogin(loginUser: MeResponse) {
        // Hard gate: refuse to load ANY role data unless the session token is face-verified.
        if (!AppAuth.session.dataAccessAllowed) {
            _state.update {
                it.copy(
                    gateStage = if (AppAuth.session.isSignedIn) GateStage.FACE_PENDING else GateStage.SIGNED_OUT,
                    signedIn = false,
                    screen = KioskScreen.FACE_LOGIN,
                    faceMessage = "Face verification required",
                    faceBusy = false,
                )
            }
            return
        }
        runCatching { repository.warmup() }
        val me = runCatching { repository.me() }.getOrNull() ?: loginUser
        val schoolMe = runCatching { io { liveApi.schoolMe() } }.getOrNull()
        if (schoolMe?.geoFenceMode != null) {
            _state.update { it.copy(geoFenceMode = schoolMe.geoFenceMode) }
        }
        when (KioskRole.fromJwt(me.role)) {
            KioskRole.GATE, KioskRole.GUARD -> loadDutyHome(me)
            KioskRole.HOST -> {
                applyIdentityHome(me)
                loadHostHome()
                startHostPendingPoll()
            }
            KioskRole.UNSUPPORTED -> applyIdentityHome(me)
        }
    }

    // ---------------- 1077: one role (guard), home by DUTY ----------------
    private var dutyJob: Job? = null

    private suspend fun readDuty(me: MeResponse): Triple<com.satcop.smartvisitor.kiosk.data.model.DutyMe?, com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo?, com.satcop.smartvisitor.kiosk.ui.duty.DutyResult> {
        val dm = runCatching { io { liveApi.dutyMe() } }.getOrNull()
        val info = com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo.from(dm)?.takeIf { it.present }
            ?: com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo.fromUser(me)?.takeIf { it.present }
        val patrolCount = if (com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.needsPatrolSummary(me.role, info)) {
            runCatching { io { liveApi.guardTodaySummary() } }.getOrNull()?.patrol?.assignedToday
        } else null
        val att = if (info == null) runCatching { io { liveApi.attendanceToday() } }.getOrNull() else null
        // the attendance row of today may already carry the duty (dutyTypes) when /duty/me is not there yet
        val info2 = info ?: com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo(att?.dutyTypes, att?.primaryHome, att?.hasDuty, att?.dutyRevision)
            .takeIf { it.present }
        val r = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.areas(
            me.role, info2, me.gateIds,
            att?.gateId ?: att?.attendance?.gateId, att?.attendance?.gateSource, patrolCount,
        )
        return Triple(dm, info2, r)
    }

    private fun applyDutyState(
        r: com.satcop.smartvisitor.kiosk.ui.duty.DutyResult,
        revision: String?,
        gates: List<com.satcop.smartvisitor.kiosk.data.model.DutyGate>,
    ) {
        _state.update {
            val active = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.selectArea(it.activeArea, r)
            val g = gates.ifEmpty { it.dutyGates }
            it.copy(
                dutyAreas = r.areas, activeArea = active, dutyRevision = revision ?: it.dutyRevision, dutyPending = null,
                dutyGates = g,
                chosenGateId = it.chosenGateId?.takeIf { id -> g.any { x -> x.id == id } },
                dutyGateName = g.firstOrNull { x -> x.id == (it.chosenGateId ?: g.firstOrNull()?.id) }?.name ?: it.dutyGateName,
            )
        }
    }

    private suspend fun loadDutyHome(me: MeResponse) {
        val (dm, info, r) = readDuty(me)
        applyIdentityHome(me)
        applyDutyState(r, info?.revision, dm?.gates.orEmpty())
        if (com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.GATE in r.areas) {
            loadGateHome(me)
            // the gate chosen at clock-in (or the first duty gate) is the default gate of the desk
            val gid = _state.value.chosenGateId ?: _state.value.dutyGates.firstOrNull()?.id
            if (gid != null) _state.update { s -> if (s.gates.any { it.id == gid }) s.copy(draft = s.draft.copy(gateId = gid)) else s }
        }
        _state.update {
            it.copy(screen = if (it.activeArea == com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.PATROL) KioskScreen.GUARD_PATROL else KioskScreen.HOME)
        }
        startDutyPoll()
    }

    private fun startDutyPoll() {
        dutyJob?.cancel()
        dutyJob = viewModelScope.launch {
            while (true) {
                delay(60_000L)
                val s = _state.value
                if (!s.signedIn || !FaceGateMachine.canShowData(s.gateStage)) return@launch
                if (hostForeground) checkDuty()
            }
        }
    }

    /** Home open / pull-to-refresh / resume / duty error: re-read duty; a change shows the banner (never switches silently). */
    fun refreshDutyNow() {
        val s = _state.value
        if (!s.signedIn || !FaceGateMachine.canShowData(s.gateStage) || s.dutyAreas == null) return
        viewModelScope.launch { checkDuty() }
    }

    private suspend fun checkDuty() {
        val me = AppAuth.session.user ?: return
        val dm = runCatching { io { liveApi.dutyMe() } }.getOrNull() ?: return
        val s = _state.value
        val info = com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo.from(dm)?.takeIf { it.present } ?: return
        val fresh = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.areas(me.role, info, me.gateIds)
        val changed = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.revisionChanged(s.dutyRevision, info.revision) ||
            (s.dutyRevision == null && fresh.areas != s.dutyAreas)
        if (changed && s.dutyPending?.dutyRevision != dm.dutyRevision) _state.update { it.copy(dutyPending = dm) }
        else if (s.dutyRevision == null && !changed) _state.update { it.copy(dutyRevision = info.revision) }
    }

    /** The guard tapped "Your duty was updated. Tap to refresh.": re-read and re-render. */
    fun applyDutyUpdate() {
        viewModelScope.launch {
            val me = AppAuth.session.user ?: return@launch
            val dm = runCatching { io { liveApi.dutyMe() } }.getOrNull() ?: _state.value.dutyPending
            val info = com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo.from(dm)?.takeIf { it.present }
            if (info == null) { _state.update { it.copy(dutyPending = null) }; return@launch }
            val r = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.areas(me.role, info, me.gateIds)
            val hadGate = com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.GATE in (_state.value.dutyAreas ?: emptySet())
            applyDutyState(r, info.revision, dm?.gates.orEmpty())
            if (!hadGate && com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.GATE in r.areas) loadGateHome(me)
        }
    }

    fun switchArea(area: com.satcop.smartvisitor.kiosk.ui.duty.DutyArea) {
        _state.update { s ->
            if (s.dutyAreas?.contains(area) == true) s.copy(activeArea = area, screen = KioskScreen.HOME, toast = null) else s
        }
    }

    fun chooseGate(id: String) { _state.update { it.copy(chosenGateId = id, dutyGateName = it.dutyGates.firstOrNull { g -> g.id == id }?.name ?: it.dutyGateName) } }

    private suspend fun loadGateHome(me: MeResponse) {
        try {
            val school = repository.school()
            val staff = repository.listStaff(active = true)
            val gates = repository.listGates()
            val inside = repository.listInside()
            val story = repository.demoStory()
            val allowedGateIds = me.gateIds?.toSet()
            val matchedGates = gates.data.filter { allowedGateIds == null || it.id in allowedGateIds }
            val visibleGates = matchedGates.ifEmpty { gates.data }
            val hosts = staff.data.filter { row -> row.roleTitle != "Guard" }
            val hideStory = SchoolIds.hidesDemoStory(me.schoolId)
            val defaultGateId = story.gateId
                .takeIf { id -> !hideStory && visibleGates.any { it.id == id } }
                ?: visibleGates.firstOrNull()?.id
                ?: DemoFixtures.GATE_MAIN_ID
            val defaultHostId = story.hostId
                .takeIf { id -> !hideStory && hosts.any { it.id == id } }
                ?: hosts.firstOrNull()?.id
            val watermark = ""
            val source = repository.dataSource
            _state.update {
                it.copy(
                    signedIn = true,
                    gateStage = GateStage.VERIFIED,
                    loginBusy = false,
                    loginError = null,
                    loginPassword = "",
                    schoolName = school.name,
                    schoolId = me.schoolId,
                    timezone = school.timezone,
                    watermark = watermark,
                    dataSource = source,
                    meDisplayName = me.displayName,
                    meRole = me.role,
                    meStaffId = me.staffId.orEmpty(),
                    gates = visibleGates,
                    hosts = hosts,
                    recent = if (hideStory) {
                        inside.data.filter { !isPriyaDemoRow(it.visitorName, it.id) }.take(4)
                    } else {
                        inside.data.take(4)
                    },
                    story = story,
                    hideDemoStory = hideStory,
                    screen = KioskScreen.HOME,
                    draft = it.draft.copy(
                        hostId = defaultHostId,
                        gateId = defaultGateId,
                    ),
                    loaded = true,
                    toast = if (source == DataSource.LIVE) {
                        "Signed in · ${me.displayName}"
                    } else {
                        "Signed in · some lists could not be loaded"
                    },
                    toastKind = if (source == DataSource.LIVE) ToastKind.SUCCESS else ToastKind.WARNING,
                )
            }
        } catch (_: Exception) {
            applyIdentityHome(me, warning = true)
        }
    }

    private fun applyIdentityHome(
        me: MeResponse,
        warning: Boolean = false,
    ) {
        val source = repository.dataSource
        _state.update {
            it.copy(
                signedIn = true,
                gateStage = GateStage.VERIFIED,
                loginBusy = false,
                loginError = null,
                loginPassword = "",
                schoolName = me.schoolName?.takeIf { n -> n.isNotBlank() } ?: me.schoolId.ifBlank { it.schoolName },
                schoolId = me.schoolId,
                watermark = "",
                dataSource = source,
                meDisplayName = me.displayName,
                meRole = me.role,
                meStaffId = me.staffId.orEmpty(),
                gates = emptyList(),
                hosts = emptyList(),
                recent = emptyList(),
                loaded = true,
                hideDemoStory = SchoolIds.hidesDemoStory(me.schoolId),
                screen = KioskScreen.HOME,
                toast = if (warning) {
                    "Signed in · ${me.displayName} · directory unavailable"
                } else {
                    "Signed in · ${me.displayName}"
                },
                toastKind = if (warning) ToastKind.WARNING else ToastKind.SUCCESS,
            )
        }
    }

    fun openPickup() {
        if (!_state.value.isGateDesk()) return
        viewModelScope.launch {
            _state.update { it.copy(screen = KioskScreen.PICKUP, toast = null) }
            refreshStudents()
        }
    }

    fun closePickup() {
        _state.update { it.copy(screen = KioskScreen.HOME, toast = null) }
    }

    fun refreshHostPending() {
        viewModelScope.launch { loadHostHome(showToast = true) }
    }

    fun meetingDone(visitId: String) {
        viewModelScope.launch {
            _state.update { it.copy(hostBusy = true, toast = null) }
            try {
                repository.meetingDone(visitId)
                loadHostHome(showToast = false)
                _state.update {
                    it.copy(
                        toast = "Meeting done · $visitId",
                        toastKind = ToastKind.SUCCESS,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        hostBusy = false,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun toggleAfterHoursPanel() {
        _state.update { it.copy(showingAfterHours = !it.showingAfterHours) }
    }

    fun approvePending(visitId: String) {
        viewModelScope.launch {
            _state.update { it.copy(hostBusy = true, toast = null, hostNotice = null) }
            try {
                repository.approveVisit(visitId)
                loadHostHome(showToast = false)
                _state.update {
                    it.copy(
                        toast = "Approved · card removed",
                        toastKind = ToastKind.SUCCESS,
                        hostBusy = false,
                    )
                }
            } catch (e: Exception) {
                val msg = com.satcop.smartvisitor.kiosk.data.model.HostApproveCopy.forError(e)
                val after = (e as? ApiException)?.code == AfterHoursCopy.CODE
                _state.update {
                    it.copy(
                        hostBusy = false,
                        afterHours = it.afterHours || after,
                        hostNotice = msg,
                        toast = msg,
                        toastKind = if (after) ToastKind.WARNING else ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun dismissHostNotice() { _state.update { it.copy(hostNotice = null) } }

    fun startReject(visitId: String) {
        _state.update { it.copy(rejectingVisitId = visitId, rejectReason = null, hostNotice = null) }
    }

    fun pickRejectReason(reason: String) {
        _state.update { it.copy(rejectReason = reason) }
    }

    fun cancelReject() {
        _state.update { it.copy(rejectingVisitId = null, rejectReason = null) }
    }

    fun confirmReject() {
        val id = _state.value.rejectingVisitId ?: return
        val reason = _state.value.rejectReason?.trim().orEmpty()
        if (reason.isEmpty()) {
            _state.update { it.copy(toast = "Pick a reject reason", toastKind = ToastKind.WARNING) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(hostBusy = true, toast = null) }
            try {
                repository.rejectVisit(id, reason)
                loadHostHome(showToast = false)
                _state.update {
                    it.copy(
                        toast = "Rejected · $reason",
                        toastKind = ToastKind.WARNING,
                        hostBusy = false,
                        rejectingVisitId = null,
                        rejectReason = null,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        hostBusy = false,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun updatePickupQuery(value: String) {
        _state.update { it.copy(pickupQuery = value) }
        viewModelScope.launch { refreshStudents() }
    }

    fun selectPickupStudent(student: StudentOut) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    selectedStudent = student,
                    selectedCollector = null,
                    authorizedPickup = emptyList(),
                    activePickup = null,
                )
            }
            val people = repository.listAuthorizedPickup(student.id)
            _state.update { it.copy(authorizedPickup = people.filter { row -> row.active }) }
        }
    }

    fun selectPickupCollector(person: AuthorizedPickup) {
        _state.update { it.copy(selectedCollector = person) }
    }

    fun selectPickupReason(reason: String) {
        _state.update { it.copy(pickupReason = reason) }
    }

    fun updatePickupReasonOther(value: String) {
        _state.update { it.copy(pickupReasonOther = value) }
    }

    fun startPickup() {
        val snap = _state.value
        val student = snap.selectedStudent ?: return
        val collector = snap.selectedCollector ?: return
        val gateId = snap.selectedGate?.id ?: snap.draft.gateId
        if (gateId.isBlank()) {
            _state.update { it.copy(toast = "Select a gate", toastKind = ToastKind.WARNING) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(pickupBusy = true, toast = null) }
            try {
                val pickup = repository.startPickup(
                    PickupCreate(
                        studentId = student.id,
                        gateId = gateId,
                        pickupReason = snap.pickupReason,
                        reasonOther = snap.pickupReasonOther.trim().ifEmpty { null },
                        collectorPickupPersonId = collector.id,
                        collectorName = collector.name,
                        collectorMobile = collector.mobile,
                    ),
                )
                _state.update {
                    it.copy(
                        pickupBusy = false,
                        activePickup = pickup,
                        dataSource = repository.dataSource,
                        toast = "Pickup ${pickup.status} · ${collector.name}",
                        toastKind = ToastKind.SUCCESS,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        pickupBusy = false,
                        dataSource = repository.dataSource,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun consentPickup() {
        val id = _state.value.activePickup?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(pickupBusy = true) }
            try {
                val pickup = repository.consentPickup(id, PickupReasons.CONSENT_VERSION)
                _state.update {
                    it.copy(
                        pickupBusy = false,
                        activePickup = pickup,
                        toast = "Consent recorded",
                        toastKind = ToastKind.SUCCESS,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        pickupBusy = false,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun releasePickup() {
        val current = _state.value.activePickup ?: return
        val name = current.collectorName ?: _state.value.selectedCollector?.name ?: "Collector"
        viewModelScope.launch {
            _state.update { it.copy(pickupBusy = true) }
            try {
                val photo = repository.uploadMedia(
                    PlaceholderBitmap.toJpeg(PlaceholderBitmap.livePhoto(name)),
                    "collector-live.jpg",
                    "image/jpeg",
                    "collector_live_photo",
                )
                val pickup = repository.releasePickup(current.id, photo.key)
                _state.update {
                    it.copy(
                        pickupBusy = false,
                        activePickup = pickup,
                        toast = "Released · ${pickup.collectorName ?: name}",
                        toastKind = ToastKind.SUCCESS,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        pickupBusy = false,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    /** Persist the `since` cursor per app install (SharedPreferences); memory-only until bound. */
    fun bindNotifyStore(context: Context) {
        if (feedStore is MemoryStringStore) {
            feedStore = SharedPrefsStringStore(context, "satcop_host_feed")
            feedTracker = HostFeedTracker(feedStore)
        }
    }

    /** Lifecycle hook: foreground => 3 s polling + immediate refresh on resume. */
    fun setHostForeground(foreground: Boolean) {
        val was = hostForeground
        hostForeground = foreground
        if (foreground && !was) pollNow?.complete(Unit)
    }

    /** Called by the pull-to-refresh / resume paths: never starts a second in-flight request. */
    fun refreshHostNow() {
        pollNow?.complete(Unit)
        if (hostPollJob == null) return
        viewModelScope.launch { loadHostHome(showToast = false) }
    }

    private fun startHostPendingPoll() {
        hostPollJob?.cancel()
        feedUnsupported = false
        fallbackSeenPending = null
        hostFirstPollDone = false
        hostPollJob = viewModelScope.launch {
            while (true) {
                val snap = _state.value
                if (!snap.signedIn || snap.homeRole() != KioskRole.HOST ||
                    !FaceGateMachine.canShowData(snap.gateStage)
                ) return@launch
                if (!pollInFlight) {
                    pollInFlight = true
                    try {
                        pollHostOnce()
                        pollBackoff.onSuccess()
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        pollBackoff.onFailure()
                    } finally {
                        pollInFlight = false
                    }
                }
                val wake = CompletableDeferred<Unit>().also { pollNow = it }
                kotlinx.coroutines.withTimeoutOrNull(pollBackoff.nextDelayMs(hostForeground)) { wake.await() }
            }
        }
    }

    private fun hostKey(): String = _state.value.meStaffId.ifBlank { "host" }

    private suspend fun pollHostOnce() {
        val key = hostKey()
        val alerts: List<HostFeedItem>
        if (!feedUnsupported) {
            try {
                val resp = repository.hostFeed(io { feedTracker.lastId(key) })
                // SharedPreferences commit() inside the tracker = disk write: keep it off the main thread.
                alerts = io {
                    feedTracker.ingest(
                        hostKey = key,
                        items = resp.data,
                        metaLastId = resp.meta?.lastId,
                        parseTime = { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() },
                    )
                }
            } catch (e: ApiException) {
                if (e.httpStatus == 404 || e.httpStatus == 405) {
                    feedUnsupported = true // /notifications feed not live -> fall back to pending list diff
                    return
                }
                throw e
            }
        } else {
            val pending = repository.listPendingVisits(_state.value.meStaffId.ifBlank { null })
                .filter { it.status == "pending" }
            val seen = fallbackSeenPending
            if (seen == null) {
                fallbackSeenPending = pending.map { it.id }.toMutableSet()
                alerts = emptyList()
            } else {
                alerts = pending.filter { seen.add(it.id) }.map {
                    HostFeedItem(
                        id = "V-" + it.id, type = "visit.pending", visitId = it.id,
                        visitorName = it.visitorName, purpose = it.purpose, gateLabel = it.gateId,
                        hostId = it.hostId, createdAt = it.createdAt, actions = listOf("approve", "reject", "view"),
                    )
                }
            }
        }
        val now = System.currentTimeMillis()
        val due = alerts.isNotEmpty() || now - lastFullRefreshMs > 15_000
        if (due) {
            lastFullRefreshMs = now
            loadHostHome(showToast = false)
        }
        val firstPoll = !hostFirstPollDone
        hostFirstPollDone = true
        if (alerts.isNotEmpty() && !firstPoll) {
            _state.update { it.copy(hostAlerts = (it.hostAlerts + alerts).distinctBy { a -> a.id }) }
            alerts.forEach { _newVisitorEvents.tryEmit(it) }
        }
    }

    /** Popup "Later"/dismiss: drop head alert and mark it read (best effort). */
    fun dismissHostAlert() {
        val head = _state.value.hostAlerts.firstOrNull() ?: return
        _state.update { it.copy(hostAlerts = it.hostAlerts.drop(1)) }
        markAlertRead(head)
    }

    fun approveFromAlert(alert: HostFeedItem) {
        val vid = alert.visitId ?: return dismissHostAlert()
        _state.update { it.copy(hostAlerts = it.hostAlerts.filterNot { a -> a.id == alert.id }) }
        markAlertRead(alert)
        approvePending(vid)
    }

    fun rejectFromAlert(alert: HostFeedItem) {
        // Reject needs a reason: hand over to the Inbox decline flow for that visit.
        val vid = alert.visitId
        _state.update { it.copy(hostAlerts = it.hostAlerts.filterNot { a -> a.id == alert.id }) }
        markAlertRead(alert)
        if (vid != null) startReject(vid)
    }

    private fun markAlertRead(alert: HostFeedItem) {
        if (alert.id.startsWith("V-")) return // synthetic (fallback mode) — nothing to mark
        viewModelScope.launch { runCatching { repository.markNotificationsRead(listOf(alert.id)) } }
    }

    /** Tapped system notification (only honoured once face-verified as Host). */
    fun openHostVisit(visitId: String?) {
        if (visitId.isNullOrBlank()) return
        _state.update { it.copy(hostFocusVisitId = visitId) }
        refreshHostNow()
    }

    fun clearHostFocus() { _state.update { it.copy(hostFocusVisitId = null) } }

    /** Device-token hook for future FCM: no-op until a token exists (Backend /devices is ready). */
    fun registerPushToken(token: String?) {
        if (token.isNullOrBlank()) return
        viewModelScope.launch { runCatching { repository.registerDeviceToken(token) } }
    }

    private var hostLoadInFlight = false

    private suspend fun loadHostHome(showToast: Boolean = false) {
        if (hostLoadInFlight) return // avoid duplicate in-flight requests (poll + resume + pull-to-refresh)
        hostLoadInFlight = true
        try { loadHostHomeInner(showToast) } finally { hostLoadInFlight = false }
    }

    private suspend fun loadHostHomeInner(showToast: Boolean) {
        val hostId = _state.value.meStaffId.ifBlank { null }
        // Silent background refreshes must not flip hostBusy (it disables Approve/Decline buttons).
        if (showToast) _state.update { it.copy(hostBusy = true) }
        try {
            val pending = repository.listPendingVisits(hostId)
                .filter { it.status == "pending" }
                .filter { !SchoolIds.hidesDemoStory(_state.value.schoolId) || !isPriyaDemoRow(it.visitorName, it.id) }
            val hours = repository.listHours()
            val zone = runCatching { ZoneId.of(_state.value.timezone) }
                .getOrDefault(ZoneId.of("Asia/Kolkata"))
            val after = CampusHours.isAfterHours(hours, ZonedDateTime.now(zone)) ||
                pending.any { it.afterHours }
            val photos = pending.associate { visit ->
                val key = visit.livePhotoKey
                val allowed = !visit.consentAt.isNullOrBlank()
                val bytes = if (allowed && !key.isNullOrBlank()) repository.loadMediaBytes(key) else null
                val bmp = bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                visit.id to bmp
            }.filterValues { it != null }.mapValues { it.value as Bitmap }
            val active = repository.listHostActiveVisits(hostId)
            _state.update {
                it.copy(
                    hostBusy = false,
                    pendingVisits = pending,
                    hostActiveVisits = active,
                    pendingPhotos = photos,
                    afterHours = after,
                    rejectingVisitId = it.rejectingVisitId?.takeIf { id -> pending.any { row -> row.id == id } },
                    dataSource = repository.dataSource,
                    toast = when {
                        showToast && pending.isEmpty() -> "No pending visits"
                        showToast -> "Pending · ${pending.size}"
                        else -> it.toast
                    },
                    toastKind = if (showToast) ToastKind.INFO else it.toastKind,
                )
            }
        } catch (e: Exception) {
            // Keep the last good list on transient errors (no empty-inbox flash while polling).
            _state.update {
                it.copy(
                    hostBusy = false,
                    toast = if (showToast) ErrorCopy.forThrowable(e) else it.toast,
                    toastKind = if (showToast) ToastKind.ERROR else it.toastKind,
                    dataSource = repository.dataSource,
                )
            }
        }
    }

    private suspend fun refreshStudents() {
        val q = _state.value.pickupQuery
        try {
            val rows = repository.listStudents(q.ifBlank { null })
                .filter { it.active }
                .filter {
                    !SchoolIds.hidesDemoStory(_state.value.schoolId) ||
                        !isPriyaDemoRow(it.name, it.id)
                }
            _state.update { it.copy(students = rows, dataSource = repository.dataSource) }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    students = emptyList(),
                    toast = ErrorCopy.forThrowable(e),
                    toastKind = ToastKind.ERROR,
                    dataSource = repository.dataSource,
                )
            }
        }
    }

    private suspend fun tickClock() {
        while (true) {
            val zone = runCatching { ZoneId.of(_state.value.timezone) }
                .getOrDefault(ZoneId.of(DemoFixtures.SCHOOL_TZ))
            _clock.value = ZonedDateTime.now(zone).format(clockFmt) + " IST"
            delay(1_000)
        }
    }

    fun selectVisitorType(apiValue: String) {
        _state.update { it.copy(draft = it.draft.copy(visitorType = apiValue)) }
    }

    fun selectGate(gateId: String) {
        _state.update { it.copy(draft = it.draft.copy(gateId = gateId)) }
    }

    fun selectHost(hostId: String) {
        _state.update {
            it.copy(
                draft = it.draft.copy(hostId = hostId, department = null),
                fieldErrors = it.fieldErrors - "hostId",
            )
        }
    }

    /** N1: a vendor picks a department instead of a host; the server routes it to a real host. */
    fun selectDepartment(department: String) {
        _state.update {
            it.copy(
                draft = it.draft.copy(hostId = null, department = department.trim().ifEmpty { null }),
                fieldErrors = it.fieldErrors - "hostId",
            )
        }
    }

    fun updateName(value: String) = patchDraft { copy(visitorName = value) }

    fun updateMobile(value: String) {
        patchDraft { copy(mobile = value) }
        maybeAutofetch(value)
    }

    fun updateCompany(value: String) = patchDraft { copy(company = value) }

    private var autofetchJob: Job? = null

    private fun maybeAutofetch(raw: String) {
        val ten = MobileIndia.tenDigit(raw)
        if (ten == null) {
            autofetchJob?.cancel()
            _state.update { it.copy(autofetchBusy = false, autofetchHint = null) }
            return
        }
        autofetchJob?.cancel()
        autofetchJob = viewModelScope.launch {
            delay(280)
            if (MobileIndia.tenDigit(_state.value.draft.mobile) != ten) return@launch
            _state.update { it.copy(autofetchBusy = true, autofetchHint = "Looking up…") }
            try {
                val bl = repository.matchBlacklist(mobile = ten, idType = null, idNumber = null)
                if (bl?.severity == "Block") {
                    _state.update {
                        it.copy(
                            autofetchBusy = false, blocked = true, blacklistHit = bl,
                            autofetchHint = "BLACKLIST BLOCK · ${bl.name ?: ten}",
                            toast = "Blacklist Block — entry denied", toastKind = ToastKind.ERROR,
                            draft = it.draft.copy(visitorName = bl.name ?: it.draft.visitorName, mobile = MobileIndia.formatDisplay(ten)),
                        )
                    }
                    return@launch
                }
                val prefill = repository.lookupVisitorByMobile(ten)
                if (prefill == null) {
                    _state.update { it.copy(autofetchBusy = false, autofetchHint = "No prior match", blocked = false) }
                    return@launch
                }
                if (prefill.blacklisted && prefill.blacklistSeverity == "Block") {
                    _state.update {
                        it.copy(
                            autofetchBusy = false, blocked = true,
                            blacklistHit = BlacklistEntry(id = "BL-PREFILL", name = prefill.visitorName, mobile = ten, reason = prefill.blacklistReason, severity = "Block"),
                            autofetchHint = "BLACKLIST BLOCK", toast = "Blacklist Block — entry denied", toastKind = ToastKind.ERROR,
                            draft = it.draft.copy(visitorName = prefill.visitorName.orEmpty(), mobile = MobileIndia.formatDisplay(ten)),
                        )
                    }
                    return@launch
                }
                _state.update {
                    val d = it.draft
                    it.copy(
                        autofetchBusy = false,
                        autofetchHint = "Prefill · ${prefill.visitorName ?: ten}",
                        toast = "Details prefilled from prior visit", toastKind = ToastKind.SUCCESS,
                        blacklistHit = if (prefill.blacklisted) BlacklistEntry(id = "BL-PREFILL", name = prefill.visitorName, mobile = ten, reason = prefill.blacklistReason, severity = prefill.blacklistSeverity ?: "Alert") else it.blacklistHit,
                        blocked = false,
                        draft = d.copy(
                            visitorName = d.visitorName.ifBlank { prefill.visitorName.orEmpty() },
                            mobile = MobileIndia.formatDisplay(ten),
                            company = d.company.ifBlank { prefill.company.orEmpty() },
                            purpose = d.purpose.ifBlank { prefill.lastPurpose.orEmpty() },
                            visitorType = prefill.lastVisitorType ?: d.visitorType,
                            hostId = prefill.lastHostId ?: d.hostId,
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(autofetchBusy = false, autofetchHint = ErrorCopy.forThrowable(e)) }
            }
        }
    }

    fun updatePurpose(value: String) = patchDraft { copy(purpose = value) }

    fun updateVehicle(value: String) = patchDraft { copy(vehicleNumber = value) }

    fun updateAccompanying(value: String) = patchDraft { copy(accompanyingCount = value) }

    fun updateNotes(value: String) = patchDraft { copy(notes = value) }

    fun selectIdType(apiValue: String) = patchDraft { copy(idType = apiValue) }

    fun updateIdNumber(value: String) = patchDraft { copy(idNumber = value) }

    fun updateIdTypeName(value: String) = patchDraft { copy(idTypeName = value.take(30)) }

    fun setLivePhoto(bitmap: Bitmap?) {
        if (bitmap == null) {
            // No camera picture = no photo. Nothing is invented; the guard must try again.
            _state.update { it.copy(toast = "The photo could not be taken. Please try again.", toastKind = ToastKind.WARNING) }
            return
        }
        _state.update {
            it.copy(
                livePhoto = bitmap,
                draft = it.draft.copy(livePhotoCaptured = true),
                fieldErrors = it.fieldErrors - "livePhotoKey",
                toast = null,
            )
        }
    }

    fun setIdImage(bitmap: Bitmap?) {
        if (bitmap == null) {
            _state.update { it.copy(toast = "The ID photo could not be taken. Please try again.", toastKind = ToastKind.WARNING) }
            return
        }
        // ID number stays compulsory: capturing the ID photo does not clear idNumber errors.
        _state.update {
            it.copy(
                idImage = bitmap,
                draft = it.draft.copy(idImageCaptured = true),
                fieldErrors = it.fieldErrors - "idImageKey",
                toast = null,
            )
        }
    }

    fun setSignature(bitmap: Bitmap?) {
        _state.update {
            it.copy(
                signature = bitmap,
                draft = it.draft.copy(signatureCaptured = bitmap != null),
            )
        }
    }

    fun clearSignature() {
        _state.update {
            it.copy(signature = null, draft = it.draft.copy(signatureCaptured = false, signatureKey = null))
        }
    }

    private fun patchDraft(block: RegistrationDraft.() -> RegistrationDraft) {
        _state.update { it.copy(draft = it.draft.block(), fieldErrors = emptyMap()) }
    }

    /** Removed: no sample data in the app. Kept as an inert hook for the shell wiring. */
    fun prefillSample() = Unit

    fun applyBlockSample() = Unit

    fun applyAlertSample() = Unit

    private fun applyDraft(draft: RegistrationDraft, toast: String) {
        _state.update {
            it.copy(
                draft = draft,
                fieldErrors = emptyMap(),
                toast = toast,
                toastKind = ToastKind.INFO,
                livePhoto = null,
                idImage = null,
                signature = null,
                blocked = false,
                blacklistHit = null,
                createdVisit = null,
            )
        }
    }

    // ---------------- 1064 Add Visitor (all lookup/prefill/dedupe is server-side) ----------------

    private var avLookupJob: Job? = null

    /** Gate "Add Visitor": number-only screen first (registration step 2 hosts the flow). */
    /** One id per Add Visitor save; reused for retries so a lost response cannot create a second visit. */
    private var avAttemptId: String = java.util.UUID.randomUUID().toString()

    fun startAddVisitor() {
        avLookupJob?.cancel()
        avAttemptId = java.util.UUID.randomUUID().toString()
        val snap = _state.value
        _state.update {
            it.copy(
                step = 2,
                addVisitor = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState(),
                draft = RegistrationDraft(hostId = null, gateId = snap.draft.gateId),
                livePhoto = null, idImage = null, signature = null,
                fieldErrors = emptyMap(), toast = null, blocked = false, blacklistHit = null, createdVisit = null,
                submitting = false, outcomeBusy = false,
            )
        }
    }

    fun cancelAddVisitor() {
        avLookupJob?.cancel()
        _state.update {
            it.copy(
                step = 1,
                addVisitor = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState(),
                draft = RegistrationDraft(hostId = null, gateId = it.draft.gateId),
                fieldErrors = emptyMap(), toast = null,
            )
        }
    }

    fun avTypeMobile(raw: String) {
        _state.update { it.copy(addVisitor = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer.typeMobile(it.addVisitor, raw)) }
    }

    fun avCloseNotice() {
        _state.update { it.copy(addVisitor = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer.closeNotice(it.addVisitor)) }
    }

    fun avContinue() {
        val reducer = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer
        val (next, ten) = reducer.tenDigitsOrError(_state.value.addVisitor)
        _state.update { it.copy(addVisitor = next) }
        if (ten == null || _state.value.addVisitor.busy.not()) return
        avLookupJob?.cancel()
        avLookupJob = viewModelScope.launch {
            val outcome = try {
                addVisitorLookup.lookup(ten)
            } catch (e: ApiException) {
                if (ErrorCopy.isSessionEnd(e)) return@launch
                com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome.Failed(ErrorCopy.LOOKUP_FAILED)
            } catch (e: Exception) {
                com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome.Failed(ErrorCopy.LOOKUP_FAILED)
            }
            _state.update { s ->
                val av = reducer.applyOutcome(s.addVisitor, outcome)
                val draft = if (av.stage == com.satcop.smartvisitor.kiosk.ui.addvisitor.AvStage.FORM) {
                    reducer.draftFor(s.draft, av, ten, s.hosts.map { h -> h.id }.toSet())
                } else s.draft
                s.copy(addVisitor = av, draft = draft, fieldErrors = emptyMap())
            }
            // Reference photo: the URL exactly as the lookup returned it (signed ?t= intact, Bearer only for API media).
            // An expired token just means no thumbnail: the lookup is re-run (Continue) to get a fresh URL.
            val url = (outcome as? com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome.Found)?.profile?.photoUrl
                ?: (outcome as? com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome.OpenVisit)?.open?.photoUrl
            if (!url.isNullOrBlank()) {
                val bmp = runCatching { repository.loadMediaUrl(url) }.getOrNull()
                    ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                if (bmp != null) _state.update { s -> s.copy(addVisitor = s.addVisitor.copy(referencePhoto = bmp)) }
            }
        }
    }

    fun avChooseKind(kind: com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind) {
        _state.update { s ->
            val av = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer.chooseKind(s.addVisitor, kind)
            s.copy(
                addVisitor = av,
                draft = s.draft.copy(
                    profileKind = av.kind,
                    visitorType = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.apiVisitorType(av.kind, null),
                    scheduledAtMs = if (av.kind == com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind.VENDOR) null else s.draft.scheduledAtMs,
                ),
                fieldErrors = emptyMap(),
            )
        }
    }

    fun avUseSavedId(use: Boolean) = patchDraft { copy(useSavedId = use && savedId != null, idNumber = if (use) "" else idNumber) }

    fun updateScheduled(ms: Long?) = patchDraft { copy(scheduledAtMs = ms) }

    /** "Already inside" -> Check out. Server does the work; the banner closes on success. */
    /**
     * 409 on save (contract sections 3-4): PROFILE_TYPE_CONFLICT -> open the form of the type the server has;
     * ALREADY_INSIDE -> back to the number step and re-run the lookup (shows the banner). Returns true when handled.
     */
    private fun handleSaveConflict(e: ApiException): Boolean {
        val reducer = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer
        when (e.code.uppercase()) {
            "PROFILE_TYPE_CONFLICT" -> {
                val serverKind = if (e.details["profileType"].equals("vendor", true))
                    com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind.VENDOR
                else com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind.VISITOR
                _state.update { s ->
                    val av = s.addVisitor.copy(kind = serverKind)
                    s.copy(
                        submitting = false,
                        step = 2,
                        addVisitor = av,
                        draft = s.draft.copy(
                            profileKind = serverKind, profileId = e.details["profileId"] ?: s.draft.profileId,
                            confirmKindSwitch = false,
                            visitorType = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.apiVisitorType(serverKind, null),
                            scheduledAtMs = if (serverKind == com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind.VENDOR) null else s.draft.scheduledAtMs,
                        ),
                        toast = ErrorCopy.PROFILE_TYPE_CONFLICT, toastKind = ToastKind.WARNING,
                    )
                }
                return true
            }
            "OTP_REQUIRED" -> {
                val otpMsg = ErrorCopy.forApi(e).takeIf { m -> m.isNotBlank() && m != ErrorCopy.GENERIC } ?: "Verify the visitor's mobile number to continue."
                _state.update { it.copy(submitting = false, step = 2, toast = otpMsg, toastKind = ToastKind.WARNING) }
                return true
            }
            "INVALID_ID_FORMAT" -> {
                _state.update { it.copy(submitting = false, step = 2, fieldErrors = it.fieldErrors + (com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.ID to ErrorCopy.INVALID_ID_FORMAT)) }
                return true
            }
            "VENDOR_NO_SCHEDULE" -> {
                // Vendor goes straight to checked-in: drop the schedule and ask the guard to save again.
                _state.update { it.copy(submitting = false, step = 2, draft = it.draft.copy(scheduledAtMs = null), toast = ErrorCopy.VENDOR_NO_SCHEDULE, toastKind = ToastKind.WARNING) }
                return true
            }
            "VALIDATION" -> {
                val f = e.details["field"]?.takeIf { it in setOf(com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.HOST_ID, com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.ID, com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.ID_IMAGE, com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.PURPOSE, com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.COMPANY, com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.VISITOR_NAME) } ?: return false
                val step = 2
                val msg = if (f == com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.HOST_ID) ErrorCopy.HOST_INACTIVE else e.message?.takeIf { it.isNotBlank() && !it.contains('_') } ?: ErrorCopy.GENERIC
                _state.update { it.copy(submitting = false, step = step, fieldErrors = it.fieldErrors + (f to msg)) }
                return true
            }
            "OPEN_VISIT_EXISTS" -> {
                // D15 / AC-AV7: nothing was created. Show the card for the EXISTING visit; never a second one.
                val mobile = _state.value.draft.mobile
                val open = (com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.openVisitFromDetails(e.details) ?: return false).copy(
                    visitorName = _state.value.draft.visitorName.takeIf { it.isNotBlank() },
                    mobileMasked = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.maskedMobileForForm(mobile).takeIf { it.isNotBlank() },
                    purpose = _state.value.draft.purpose.takeIf { it.isNotBlank() },
                )
                _state.update { s ->
                    s.copy(
                        submitting = false, step = 2,
                        addVisitor = reducer.backToNumber(s.addVisitor.copy(mobileInput = com.satcop.smartvisitor.kiosk.data.registration.MobileIndia.tenDigit(mobile) ?: mobile.filter { it.isDigit() }))
                            .copy(notice = com.satcop.smartvisitor.kiosk.ui.addvisitor.AvNotice.Open(open)),
                        toast = null,
                    )
                }
                return true
            }
            "ALREADY_INSIDE" -> {
                val mobile = _state.value.draft.mobile
                _state.update { s ->
                    s.copy(submitting = false, step = 2, addVisitor = reducer.backToNumber(s.addVisitor.copy(mobileInput = com.satcop.smartvisitor.kiosk.data.registration.MobileIndia.tenDigit(mobile) ?: mobile.filter { it.isDigit() })),
                        toast = if (e.details["activeVisit.status"].equals("pending", true)) ErrorCopy.WAITING_HOST else ErrorCopy.ALREADY_INSIDE,
                        toastKind = ToastKind.WARNING)
                }
                avContinue()
                return true
            }
        }
        return false
    }

    /** OTP card result: the id to send on the visit (null when unverified/skipped or the number changed). */
    fun avSetOtpId(id: String?) { _state.update { it.copy(draft = it.draft.copy(otpId = id)) } }

    /** Guard confirmed "Register as different type" (contract: confirmKindSwitch:true, kind converted, audited). */
    fun avSwitchKind() {
        val reducer = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer
        _state.update { s ->
            val (av, d) = reducer.switchKind(s.addVisitor, s.draft)
            s.copy(addVisitor = av, draft = d, fieldErrors = emptyMap())
        }
    }

    /** D15: "Check in now" checks in THAT approved visit (POST /visits/{id}/check-in). No new visit. */
    fun avCheckInOpen(open: com.satcop.smartvisitor.kiosk.data.addvisitor.OpenVisitInfo) {
        if (_state.value.addVisitor.checkoutBusy) return
        _state.update { it.copy(addVisitor = it.addVisitor.copy(checkoutBusy = true)) }
        viewModelScope.launch {
            try {
                val gateId = _state.value.selectedGate?.id ?: _state.value.draft.gateId
                repository.checkInVisit(open.visitId, gateId)
                _state.update {
                    it.copy(
                        addVisitor = it.addVisitor.copy(
                            checkoutBusy = false,
                            notice = com.satcop.smartvisitor.kiosk.ui.addvisitor.AvNotice.CheckedIn(open.hostName),
                        ),
                        toast = "Checked in", toastKind = ToastKind.SUCCESS,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        addVisitor = it.addVisitor.copy(checkoutBusy = false),
                        toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun avCheckout(active: com.satcop.smartvisitor.kiosk.data.addvisitor.ActiveVisit) {
        if (_state.value.addVisitor.checkoutBusy) return
        _state.update { it.copy(addVisitor = it.addVisitor.copy(checkoutBusy = true)) }
        viewModelScope.launch {
            try {
                val gateId = _state.value.selectedGate?.id ?: _state.value.draft.gateId
                repository.checkoutInsideVisit(active.visitId, gateId)
                _state.update {
                    it.copy(
                        addVisitor = it.addVisitor.copy(checkoutBusy = false, notice = null),
                        toast = "Checked out", toastKind = ToastKind.SUCCESS,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        addVisitor = it.addVisitor.copy(checkoutBusy = false),
                        toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun continueFromStep1() {
        _state.update { it.copy(step = 2, toast = null) }
    }

    fun continueFromStep2() {
        val draft = _state.value.draft
        val errors = RegistrationValidator.validateStep2(draft)
        if (errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    fieldErrors = errors,
                    toast = RegistrationValidator.toastMessage(errors),
                    toastKind = ToastKind.WARNING,
                )
            }
            return
        }
        val normalized = draft.copy(mobile = MobileIndia.formatDisplay(draft.mobile))
        _state.update {
            it.copy(
                draft = normalized,
                fieldErrors = emptyMap(),
                step = 3,
                toast = null,
            )
        }
    }


    fun agreeGateConsent() {
        val at = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        _state.update {
            it.copy(
                draft = it.draft.copy(
                    consentAgreed = true,
                    consentVersion = GateConsent.VERSION,
                    consentAt = at,
                ),
                toast = "Consent recorded · ${GateConsent.VERSION}",
                toastKind = ToastKind.SUCCESS,
            )
        }
    }

    fun declineGateConsent() {
        _state.update {
            it.copy(
                step = 2,
                draft = it.draft.copy(consentAgreed = false, consentVersion = null, consentAt = null),
                livePhoto = null,
                idImage = null,
                toast = "Consent declined · capture locked",
                toastKind = ToastKind.WARNING,
            )
        }
    }

    fun submitRegistration() {
        val current = _state.value
        if (current.submitting) return
        // Single-form Add Visitor: the notice line above Submit is the consent; submitting = notice given.
        val now = java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
            .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        _state.update {
            it.copy(draft = it.draft.copy(consentAgreed = true, consentVersion = GateConsent.VERSION, consentAt = now))
        }
        val errors = RegistrationValidator.validateForm(_state.value.draft)
        if (errors.isNotEmpty()) {
            _state.update {
                it.copy(
                    fieldErrors = errors,
                    toast = RegistrationValidator.toastMessage(errors),
                    toastKind = ToastKind.WARNING,
                )
            }
            return
        }
        viewModelScope.launch { submitNow() }
    }

    private suspend fun submitNow() {
        _state.update { it.copy(submitting = true, toast = null, blocked = false) }
        val snap = _state.value
        val draft = snap.draft
        if (!draft.consentAgreed || draft.consentVersion.isNullOrBlank() || draft.consentAt.isNullOrBlank()) {
            _state.update {
                it.copy(
                    submitting = false,
                    toast = "Please try again.",
                    toastKind = ToastKind.WARNING,
                )
            }
            return
        }
        try {
            val liveBmp = snap.livePhoto ?: run {
                _state.update { it.copy(submitting = false, step = 2, fieldErrors = it.fieldErrors + (com.satcop.smartvisitor.kiosk.data.registration.FieldKeys.LIVE_PHOTO to com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.LIVE_PHOTO_REQUIRED)) }
                return
            }
            val consentAt = draft.consentAt
            val consentVer = GateConsent.VERSION
            val photo = repository.uploadMedia(
                PlaceholderBitmap.toJpeg(liveBmp),
                "live-photo.jpg",
                "image/jpeg",
                "live_photo",
                consentAt = consentAt,
                consentVersion = consentVer,
            )
            val idKey = snap.idImage?.let {
                repository.uploadMedia(
                    PlaceholderBitmap.toJpeg(it),
                    "id-image.jpg",
                    "image/jpeg",
                    "id_image",
                    consentAt = consentAt,
                    consentVersion = consentVer,
                ).key
            }
            val sigKey = snap.signature?.let {
                repository.uploadMedia(
                    PlaceholderBitmap.toJpeg(it),
                    "signature.jpg",
                    "image/jpeg",
                    "signature",
                    consentAt = consentAt,
                    consentVersion = consentVer,
                ).key
            }
            val mobileTen = MobileIndia.tenDigit(draft.mobile) ?: draft.mobile.filter { it.isDigit() }
            val hit = repository.matchBlacklist(
                mobile = mobileTen,
                idType = draft.idType,
                idNumber = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.idNumberToSend(draft.useSavedId && draft.savedId != null, draft.idNumber),
            )
            if (hit?.severity == "Block") {
                _state.update {
                    it.copy(
                        submitting = false,
                        blocked = true,
                        blacklistHit = hit,
                        draft = draft.copy(livePhotoKey = photo.key, idImageKey = idKey, signatureKey = sigKey),
                        toast = "Blacklist Block · pass not issued",
                        toastKind = ToastKind.ERROR,
                    )
                }
                return
            }
            val kindNow = draft.profileKind
            val body = VisitCreate(
                visitorName = draft.visitorName.trim(),
                mobile = mobileTen,
                visitorType = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.visitorTypeToSend(kindNow, draft.profileId, draft.confirmKindSwitch),
                purpose = draft.purpose.trim(),
                hostId = draft.hostId?.takeIf { it.isNotBlank() },
                department = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.departmentToSend(kindNow, draft.hostId, draft.department),
                livePhotoKey = photo.key,
                idType = draft.idType,
                idTypeName = if (draft.idType == "Other" && !(draft.useSavedId && draft.savedId != null)) draft.idTypeName.trim().ifEmpty { null } else null,
                // Only a newly typed full number is sent; the saved ID (masked) is never echoed back.
                idNumber = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.idNumberToSend(draft.useSavedId && draft.savedId != null, draft.idNumber),
                idImageKey = idKey,
                vehicleNumber = draft.vehicleNumber.trim().ifEmpty { null },
                accompanyingCount = draft.accompanyingCount.trim().toIntOrNull(),
                notes = draft.notes.trim().ifEmpty { null },
                signatureKey = sigKey,
                gateId = draft.gateId,
                blacklistOverride = false,
                // E4-G3: always send SoT version + consentAt (client-enforce until Backend hardens)
                consentVersion = GateConsent.VERSION,
                consentAt = draft.consentAt!!.ifBlank {
                    java.time.ZonedDateTime.now(java.time.ZoneId.of("Asia/Kolkata"))
                        .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                },
                profileId = draft.profileId,
                confirmKindSwitch = if (draft.confirmKindSwitch) true else null,
                company = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.companyToSend(kindNow, draft.company),
                scheduledAt = com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic.scheduledAtToSend(kindNow, draft.scheduledAtMs),
                attemptId = avAttemptId,
                otpId = draft.otpId,
            )
            val visit = repository.createVisit(body)
            val source = repository.dataSource
            val hours = runCatching { repository.listHours() }.getOrDefault(emptyList())
            val zone = runCatching { ZoneId.of(_state.value.timezone) }
                .getOrDefault(ZoneId.of("Asia/Kolkata"))
            val localAfter = CampusHours.isAfterHours(hours, ZonedDateTime.now(zone))
            val after = visit.afterHours || localAfter
            _state.update {
                it.copy(
                    submitting = false,
                    createdVisit = visit,
                    afterHours = after,
                    blacklistHit = hit,
                    blocked = false,
                    dataSource = source,
                    draft = draft.copy(livePhotoKey = photo.key, idImageKey = idKey, signatureKey = sigKey),
                    step = 4,
                    toast = when {
                        hit?.severity == "Alert" -> "Alert hit · visit pending · host notified"
                        visit.isVendor && visit.status == "inside" -> "Vendor checked in · host informed"
                        after -> "After hours · ${AfterHoursCopy.HOST_NO_OP}"
                        source == DataSource.OFFLINE -> "Host notified · waiting for approval"
                        else -> "Host notified · waiting for approval"
                    },
                    toastKind = when {
                        hit?.severity == "Alert" -> ToastKind.WARNING
                        after -> ToastKind.WARNING
                        else -> ToastKind.SUCCESS
                    },
                )
            }
            if (visit.status == "pending") {
                viewModelScope.launch { pollWhilePending() }
            }
        } catch (e: ApiException) {
            if (handleSaveConflict(e)) return
            if (e.code == "BLACKLIST_BLOCK" || e.code == "BLACKLISTED") {
                _state.update {
                    it.copy(
                        submitting = false,
                        blocked = true,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                        dataSource = repository.dataSource,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        submitting = false,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                        dataSource = repository.dataSource,
                    )
                }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    submitting = false,
                    toast = ErrorCopy.forThrowable(e),
                    toastKind = ToastKind.ERROR,
                    dataSource = repository.dataSource,
                )
            }
        }
    }

    private suspend fun pollWhilePending() {
        repeat(20) {
            delay(6_000)
            val current = _state.value
            if (current.step != 4 || current.createdVisit?.status != "pending") return
            refreshVisit(silent = true)
        }
    }

    fun refreshVisit(silent: Boolean = false) {
        val id = _state.value.createdVisit?.id ?: return
        viewModelScope.launch {
            if (!silent) _state.update { it.copy(outcomeBusy = true) }
            try {
                val visit = repository.getVisit(id)
                applyVisit(
                    visit,
                    toast = if (silent) _state.value.toast else statusToast(visit),
                    kind = if (silent) _state.value.toastKind else ToastKind.INFO,
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        outcomeBusy = false,
                        dataSource = repository.dataSource,
                        toast = if (silent) it.toast else ErrorCopy.forThrowable(e),
                        toastKind = if (silent) it.toastKind else ToastKind.ERROR,
                    )
                }
            }
        }
    }

    /** Removed: a visit is approved only by its host. */
    fun demoApprove() = Unit

    fun scanCheckIn() {
        scan("check_in", "Checked in · inside campus")
    }

    fun scanCheckOut() {
        scan("check_out", "Checked out · visit completed")
    }

    private fun scan(action: String, okToast: String) {
        val visit = _state.value.createdVisit ?: return
        val gateId = _state.value.draft.gateId
        viewModelScope.launch {
            _state.update { it.copy(outcomeBusy = true) }
            try {
                val updated = repository.scanPass(
                    passId = visit.passId,
                    token = visit.qrToken,
                    action = action,
                    gateId = gateId,
                )
                val inside = runCatching { repository.listInside() }.getOrNull()
                applyVisit(updated, okToast, ToastKind.SUCCESS, inside?.data?.take(4))
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        outcomeBusy = false,
                        dataSource = repository.dataSource,
                        toast = ErrorCopy.forThrowable(e),
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun loadStoryPass() = Unit

    private fun applyVisit(
        visit: VisitOut,
        toast: String?,
        kind: ToastKind,
        recent: List<InsideVisit>? = null,
    ) {
        _state.update {
            it.copy(
                createdVisit = visit,
                outcomeBusy = false,
                dataSource = repository.dataSource,
                recent = recent ?: it.recent,
                toast = toast,
                toastKind = kind,
            )
        }
    }

    private fun statusToast(visit: VisitOut): String = when (visit.status) {
        "pending" -> "Still waiting for host"
        "approved" -> "Approved · pass ${visit.passId ?: "ready"}"
        "inside" -> "Inside campus · ${visit.passId ?: ""}"
        "completed" -> "Checked out"
        "rejected" -> "Rejected · ${visit.rejectReason ?: "see host"}"
        else -> "Status · ${visit.status}"
    }

    fun back() {
        _state.update {
            // Add Visitor form -> back to the number-only screen (no lookup cache; the guard re-checks).
            if (it.step == 2 && it.addVisitor.stage == com.satcop.smartvisitor.kiosk.ui.addvisitor.AvStage.FORM) {
                return@update it.copy(
                    addVisitor = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer.backToNumber(it.addVisitor),
                    toast = null, fieldErrors = emptyMap(),
                )
            }
            val prev = (it.step - 1).coerceAtLeast(1)
            it.copy(step = prev, toast = null, fieldErrors = emptyMap(), blocked = false)
        }
    }

    fun registerAnother() {
        val snap = _state.value
        val hostId = snap.story.hostId
            .takeIf { id -> !snap.hideDemoStory && snap.hosts.any { it.id == id } }
            ?: snap.hosts.firstOrNull()?.id
        val gateId = snap.story.gateId
            .takeIf { id -> !snap.hideDemoStory && snap.gates.any { it.id == id } }
            ?: snap.draft.gateId.takeIf { id -> snap.gates.any { it.id == id } }
            ?: snap.gates.firstOrNull()?.id
            ?: DemoFixtures.GATE_MAIN_ID
        _state.update {
            it.copy(
                step = 1,
                addVisitor = com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState(),
                draft = RegistrationDraft(hostId = hostId, gateId = gateId),
                livePhoto = null,
                idImage = null,
                signature = null,
                fieldErrors = emptyMap(),
                toast = null,
                blocked = false,
                blacklistHit = null,
                createdVisit = null,
                submitting = false,
                outcomeBusy = false,
            )
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun isPriyaDemoRow(name: String?, id: String?): Boolean {
        val n = name.orEmpty()
        val ident = id.orEmpty()
        return n.contains("Priya Sharma", ignoreCase = true) ||
            ident.contains("P-4F21", ignoreCase = true) ||
            ident.contains("V-20260916-014", ignoreCase = true)
    }


    fun openHistory() {
        viewModelScope.launch {
            _state.update { it.copy(screen = KioskScreen.HISTORY, historyBusy = true, historySelected = null) }
            refreshHistoryInternal()
        }
    }
    fun closeGuardTool() { _state.update { it.copy(screen = KioskScreen.HOME, historySelected = null, checkoutSelectedId = null, toast = null) } }
    fun toggleHistoryToday() { _state.update { it.copy(historyTodayOnly = !it.historyTodayOnly) }; viewModelScope.launch { refreshHistoryInternal() } }
    fun setHistoryKind(kind: String) { _state.update { it.copy(historyKindFilter = kind) }; viewModelScope.launch { refreshHistoryInternal() } }
    fun setHistoryStatus(status: String) { _state.update { it.copy(historyStatusFilter = status) }; viewModelScope.launch { refreshHistoryInternal() } }
    fun selectHistory(event: GuardHistoryEvent) {
        _state.update { it.copy(historySelected = event, historyRejectReason = null, screen = KioskScreen.HISTORY_DETAIL) }
        // /gate/history has no rejectReason: the Gate must still see why a visitor was rejected.
        if (event.kind == "visit" && event.status.equals("rejected", ignoreCase = true)) {
            viewModelScope.launch {
                val reason = runCatching { withContext(Dispatchers.IO) { liveApi.getVisit(event.id) } }
                    .getOrNull()?.rejectReason?.takeIf { it.isNotBlank() }
                _state.update { if (it.historySelected?.id == event.id) it.copy(historyRejectReason = reason) else it }
            }
        }
    }
    fun clearHistoryDetail() { _state.update { it.copy(historySelected = null, historyRejectReason = null, screen = KioskScreen.HISTORY) } }
    fun refreshHistory() { viewModelScope.launch { refreshHistoryInternal() } }
    private suspend fun refreshHistoryInternal() {
        val s = _state.value
        try {
            val rows = repository.listGuardHistory(todayOnly = s.historyTodayOnly, kind = s.historyKindFilter, status = s.historyStatusFilter, gateId = s.selectedGate?.id)
            _state.update { it.copy(historyEvents = rows, historyBusy = false, dataSource = repository.dataSource) }
        } catch (e: Exception) {
            _state.update { it.copy(historyBusy = false, toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR) }
        }
    }
    fun openCourier() {
        viewModelScope.launch {
            val list = runCatching { repository.listCouriers() }.getOrDefault(emptyList())
            _state.update {
                val gate = it.selectedGate
                it.copy(
                    screen = KioskScreen.COURIER,
                    courierRecent = list,
                    courierBusy = false,
                    courierGateLabel = if (it.courierGateLabel.isNotBlank()) it.courierGateLabel
                    else (gate?.name ?: gate?.id ?: it.draft.gateId),
                    courierCollectedBy = if (it.courierCollectedBy.isNotBlank()) it.courierCollectedBy
                    else it.meDisplayName.ifBlank { "Gate guard" },
                )
            }
        }
    }
    fun updateCourierCompany(v: String) = _state.update { it.copy(courierCompany = v) }
    fun updateCourierTracking(v: String) = _state.update { it.copy(courierTracking = v) }
    fun updateCourierPackageType(v: String) = _state.update { it.copy(courierPackageType = v) }
    fun updateCourierGateLabel(v: String) = _state.update { it.copy(courierGateLabel = v) }
    fun updateCourierCollectedBy(v: String) = _state.update { it.copy(courierCollectedBy = v) }
    fun updateCourierNote(v: String) = _state.update { it.copy(courierNote = v) }
    fun setCourierPackagePhoto(jpeg: ByteArray) = _state.update { it.copy(courierPackagePhotoJpeg = jpeg) }
    fun clearCourierPackagePhoto() = _state.update { it.copy(courierPackagePhotoJpeg = null) }
    fun receiveCourier() {
        val s = _state.value
        val gateId = s.selectedGate?.id ?: s.draft.gateId
        viewModelScope.launch {
            _state.update { it.copy(courierBusy = true) }
            try {
                var photoKey: String? = null
                val jpeg = s.courierPackagePhotoJpeg
                if (jpeg != null && jpeg.isNotEmpty()) {
                    val uploaded = repository.uploadMedia(
                        jpeg,
                        "courier-package.jpg",
                        "image/jpeg",
                        "live_photo",
                    )
                    photoKey = uploaded.key
                }
                val collected = s.courierCollectedBy.trim()
                repository.receiveCourier(
                    CourierCreate(
                        gateId = gateId,
                        courierCompany = s.courierCompany.trim(),
                        recipientName = collected.ifBlank { "Lobby" },
                        packageNote = s.courierNote.ifBlank { null },
                        photoKey = photoKey,
                        trackingNumber = s.courierTracking.trim().ifBlank { null },
                        packageType = s.courierPackageType.trim().ifBlank { null },
                        collectedBy = collected.ifBlank { null },
                    ),
                )
                val list = repository.listCouriers()
                _state.update {
                    it.copy(
                        courierBusy = false,
                        courierRecent = list,
                        courierCompany = "",
                        courierTracking = "",
                        courierPackageType = "Envelope",
                        courierCollectedBy = it.meDisplayName.ifBlank { "Gate guard" },
                        courierNote = "",
                        courierPackagePhotoJpeg = null,
                        toast = "Courier Received · on History",
                        toastKind = ToastKind.SUCCESS,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(courierBusy = false, toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR) }
            }
        }
    }
    fun handOverCourier(id: String) {
        viewModelScope.launch {
            _state.update { it.copy(courierBusy = true) }
            try {
                repository.handOverCourier(id)
                _state.update { it.copy(courierBusy = false, courierRecent = repository.listCouriers(), toast = "Handed over", toastKind = ToastKind.SUCCESS) }
            } catch (e: Exception) {
                _state.update { it.copy(courierBusy = false, toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR) }
            }
        }
    }
    fun openCheckout() {
        viewModelScope.launch {
            _state.update { it.copy(screen = KioskScreen.CHECKOUT, checkoutBusy = true, checkoutSelectedId = null) }
            refreshCheckoutInside()
        }
    }
    fun refreshCheckoutInside() {
        viewModelScope.launch {
            try {
                val inside = repository.listInside().data
                _state.update { it.copy(checkoutInside = inside, checkoutBusy = false, recent = inside.take(4), dataSource = repository.dataSource) }
            } catch (e: Exception) {
                _state.update { it.copy(checkoutBusy = false, toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR) }
            }
        }
    }
    fun selectCheckout(id: String) { _state.update { it.copy(checkoutSelectedId = id) } }
    fun confirmCheckout() {
        val id = _state.value.checkoutSelectedId ?: return
        val gateId = _state.value.selectedGate?.id ?: _state.value.draft.gateId
        viewModelScope.launch {
            _state.update { it.copy(checkoutBusy = true) }
            try {
                val updated = repository.checkoutInsideVisit(id, gateId)
                val inside = runCatching { repository.listInside().data }.getOrDefault(emptyList())
                _state.update { it.copy(checkoutBusy = false, checkoutInside = inside, checkoutSelectedId = null, recent = inside.take(4), toast = "Checked out · ${updated.visitorName ?: id}", toastKind = ToastKind.SUCCESS) }
            } catch (e: Exception) {
                _state.update { it.copy(checkoutBusy = false, toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR) }
            }
        }
    }
    fun openGuardPatrol() {
        _state.update { it.copy(screen = KioskScreen.GUARD_PATROL, toast = null) }
    }
    fun openLostFound() {
        _state.update {
            it.copy(
                screen = KioskScreen.LOST_FOUND,
                lfFoundAt = LocalVisitStore.nowIst(),
                lfFinder = it.meDisplayName.ifBlank { "Gate guard" },
                lfItemType = "Found",
                lfPhoto = null,
            )
        }
    }
    fun updateLfDescription(v: String) = _state.update { it.copy(lfDescription = v) }
    fun updateLfLocation(v: String) = _state.update { it.copy(lfLocation = v) }
    fun updateLfFinder(v: String) = _state.update { it.copy(lfFinder = v) }
    fun updateLfFinderMobile(v: String) = _state.update { it.copy(lfFinderMobile = v) }
    fun updateLfFoundAt(v: String) = _state.update { it.copy(lfFoundAt = v) }
    fun updateLfItemType(v: String) = _state.update { it.copy(lfItemType = v) }
    fun setLfPhoto(bitmap: Bitmap?) = _state.update { it.copy(lfPhoto = bitmap) }
    fun submitLostFound() {
        val s = _state.value
        if (s.lfDescription.isBlank() || s.lfLocation.isBlank()) {
            _state.update { it.copy(toast = "Description and location required", toastKind = ToastKind.ERROR) }
            return
        }
        if (s.lfItemType !in setOf("Lost", "Found")) {
            _state.update { it.copy(toast = "Pick Lost or Found", toastKind = ToastKind.ERROR) }
            return
        }
        val photoBmp = s.lfPhoto
        if (photoBmp == null) {
            _state.update { it.copy(toast = "Capture item photo (required)", toastKind = ToastKind.ERROR) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(lfBusy = true) }
            try {
                val stream = java.io.ByteArrayOutputStream()
                photoBmp.compress(Bitmap.CompressFormat.JPEG, 90, stream)
                val jpeg = stream.toByteArray()
                val uploaded = repository.uploadMedia(
                    jpeg,
                    "lost-found-item.jpg",
                    "image/jpeg",
                    "lost_found_photo",
                )
                val created = repository.createLostFound(
                    LostFoundCreate(
                        description = s.lfDescription,
                        locationFound = s.lfLocation,
                        foundAt = s.lfFoundAt.ifBlank { LocalVisitStore.nowIst() },
                        finderName = s.lfFinder.ifBlank { s.meDisplayName.ifBlank { "Gate guard" } },
                        finderMobile = s.lfFinderMobile.ifBlank { null },
                        gateId = s.selectedGate?.id ?: s.draft.gateId,
                        photoKey = uploaded.key,
                        itemType = s.lfItemType,
                        itemName = s.lfDescription.trim(),
                        category = "Other",
                    ),
                )
                _state.update {
                    it.copy(
                        lfBusy = false,
                        lfDescription = "",
                        lfLocation = "",
                        lfFinderMobile = "",
                        lfItemType = "Found",
                        lfPhoto = null,
                        toast = "${s.lfItemType} · ${created.id}",
                        toastKind = ToastKind.SUCCESS,
                        screen = KioskScreen.HOME,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(lfBusy = false, toast = ErrorCopy.forThrowable(e), toastKind = ToastKind.ERROR) }
            }
        }
    }
    fun bindFaceStore(context: Context) {
        if (faceStore == null) faceStore = LocalFaceTemplateStore(context.applicationContext)
    }

    fun openFaceLogin(context: Context? = null) {
        context?.let { bindFaceStore(it) }
        val store = faceStore
        val user = _state.value.loginUsername.trim()
        // Show the face screen at once; the template-store reads (disk) run on IO and then fill in the hub fields.
        _state.update { it.copy(screen = KioskScreen.FACE_LOGIN, facePhase = FaceLoginPhase.HUB, faceMessage = null) }
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) {
                val enrolled = store?.isEnrolled(user) == true || store?.hasAny() == true
                val enrolledUser = if (user.isNotBlank() && store?.isEnrolled(user) == true) user else store?.enrolledUsername().orEmpty()
                Triple(enrolled, enrolledUser, Pair(store?.consentAt(enrolledUser), store?.consentVersion(enrolledUser)))
            }
            val (enrolled, enrolledUser, consent) = info
            _state.update {
                it.copy(
                    faceEnrolled = enrolled,
                    faceConsentAgreed = enrolled && !consent.first.isNullOrBlank(),
                    faceConsentAt = consent.first,
                    faceConsentVersion = consent.second,
                    loginUsername = it.loginUsername.ifBlank { enrolledUser },
                )
            }
        }
    }

    fun closeFaceLogin() {
        // Leaving the face step ("use password" / back) drops any half-open password session:
        // nothing behind the gate becomes reachable.
        if (AppAuth.session.isSignedIn && !AppAuth.session.faceVerified) {
            viewModelScope.launch { runCatching { repository.logout() }; AppAuth.session.clear() }
        }
        _state.update {
            it.copy(
                gateStage = FaceGateMachine.onBack(it.gateStage),
                screen = KioskScreen.HOME,
                facePhase = FaceLoginPhase.HUB,
                faceMessage = null,
                faceBusy = false,
            )
        }
    }

    fun updateFaceUsername(value: String) {
        val store = faceStore
        val enrolled = store?.isEnrolled(value) == true
        _state.update {
            it.copy(
                loginUsername = value,
                faceEnrolled = enrolled || (store?.hasAny() == true && value.isBlank()),
                faceMessage = null,
            )
        }
    }

    fun startFaceEnroll() {
        val user = _state.value.loginUsername.trim()
        if (user.isBlank()) {
            _state.update { it.copy(faceMessage = "Enter staff username before enroll") }
            return
        }
        // Consent required before CameraX (Compliance); no pre-tick.
        _state.update {
            it.copy(
                facePhase = FaceLoginPhase.CONSENT_ENROLL,
                faceConsentAgreed = false,
                faceConsentAt = null,
                faceConsentVersion = null,
                faceMessage = null,
            )
        }
    }

    fun agreeFaceConsent() {
        val at = java.time.Instant.now().toString()
        _state.update {
            it.copy(
                faceConsentAgreed = true,
                faceConsentAt = at,
                faceConsentVersion = StaffFaceConsent.VERSION,
                facePhase = FaceLoginPhase.CAPTURE_ENROLL,
                faceMessage = "Consent recorded · capture face",
            )
        }
    }

    fun declineFaceConsent() {
        // Decline = no capture, no template store
        _state.update {
            it.copy(
                facePhase = FaceLoginPhase.HUB,
                faceConsentAgreed = false,
                faceConsentAt = null,
                faceConsentVersion = null,
                faceMessage = "Declined — no face capture (use password)",
                toast = "Face enroll cancelled",
                toastKind = ToastKind.INFO,
            )
        }
    }

    fun startFaceVerify() {
        _state.update {
            it.copy(facePhase = FaceLoginPhase.CAPTURE_VERIFY, faceMessage = null, faceError = false)
        }
    }

    fun consumeClockInRow() {
        _state.update { it.copy(clockInRow = null, verifiedCard = null) }
    }

    fun cancelFaceCapture() {
        _state.update {
            it.copy(facePhase = FaceLoginPhase.HUB, faceBusy = false, faceMessage = null, faceError = false)
        }
    }

    fun onFaceCaptured(jpegBytes: ByteArray) {
        when (_state.value.facePhase) {
            FaceLoginPhase.CAPTURE_ENROLL -> enrollFace(jpegBytes)
            FaceLoginPhase.CAPTURE_VERIFY -> verifyFace(jpegBytes)
            else -> _state.update { it.copy(faceMessage = "Unexpected capture phase") }
        }
    }


    private fun enrollFace(jpegBytes: ByteArray) {
        val s = _state.value
        val user = s.loginUsername.trim()
        if (!s.faceConsentAgreed || s.faceConsentAt.isNullOrBlank() || s.faceConsentVersion != StaffFaceConsent.VERSION) {
            _state.update {
                it.copy(
                    facePhase = FaceLoginPhase.CONSENT_ENROLL,
                    faceMessage = "CONSENT_REQUIRED · agree ${StaffFaceConsent.VERSION} first",
                    toast = "Consent required",
                    toastKind = ToastKind.WARNING,
                )
            }
            return
        }
        if (user.isBlank()) {
            _state.update { it.copy(faceMessage = "Username required", facePhase = FaceLoginPhase.HUB) }
            return
        }
        val store = faceStore
        if (store == null) {
            _state.update { it.copy(faceMessage = "Face store not ready", facePhase = FaceLoginPhase.HUB) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(faceBusy = true, faceMessage = "Enrolling…") }
            store.enroll(user, jpegBytes, s.faceConsentVersion!!, s.faceConsentAt!!)
            val b64 = LocalFaceTemplateStore.jpegToBase64(jpegBytes)
            val stamp = CaptureGeo.read()
            try {
                if (liveApi.accessToken.isNullOrBlank()) {
                    val pw = s.loginPassword
                    if (pw.isBlank()) {
                        error("Enter password on Sign-in first — enroll needs Bearer session")
                    }
                    io { liveApi.login(user, pw) }
                }
                io { liveApi.faceEnroll(
                    FaceEnrollRequest(
                        username = user,
                        imageBase64 = b64,
                        faceConsentVersion = StaffFaceConsent.VERSION,
                        faceConsentAt = s.faceConsentAt!!,
                        consentVersion = StaffFaceConsent.VERSION,
                        consentAt = s.faceConsentAt,
                        capturedAt = stamp.capturedAt,
                        lat = stamp.lat,
                        lng = stamp.lng,
                        accuracyM = stamp.accuracyM,
                        gpsMissing = stamp.gpsMissing,
                    ),
                ) }
                val msg = if (stamp.gpsMissing) {
                    "Live enroll OK · GPS unavailable (gpsMissing)"
                } else {
                    "Live enroll OK (Bearer)"
                }
                _state.update {
                    it.copy(
                        faceBusy = false,
                        faceEnrolled = true,
                        facePhase = FaceLoginPhase.HUB,
                        faceMessage = msg,
                        toast = if (stamp.gpsMissing) "Face enrolled · location missing (allowed)" else "Face enrolled · staff only",
                        toastKind = if (stamp.gpsMissing) ToastKind.WARNING else ToastKind.SUCCESS,
                    )
                }
            } catch (e: ApiException) {
                val geo = GeoFenceCodes.isRestricted(e.code, e.message)
                if (geo) {
                    _state.update {
                        it.copy(
                            faceBusy = false,
                            facePhase = FaceLoginPhase.HUB,
                            faceMessage = GeoFenceCodes.toastMessage(e.message),
                            toast = GeoFenceCodes.toastMessage(e.message),
                            toastKind = ToastKind.ERROR,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            faceBusy = false,
                            faceEnrolled = true,
                            facePhase = FaceLoginPhase.HUB,
                            faceMessage = "Saved on this phone. Server enrolment is pending.",
                            toast = "Face enrolled · staff only",
                            toastKind = ToastKind.SUCCESS,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        faceBusy = false,
                        faceEnrolled = true,
                        facePhase = FaceLoginPhase.HUB,
                        faceMessage = "Saved on this phone. Server enrolment is pending.",
                        toast = "Face enrolled · staff only",
                        toastKind = ToastKind.SUCCESS,
                    )
                }
            }
        }
    }

    /** Blocking OkHttp calls must never run on the main thread (viewModelScope is Dispatchers.Main). */
    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun verifyFace(jpegBytes: ByteArray) {
        val typedUser = LoginInput.normalizeUsername(_state.value.loginUsername)
            .ifBlank { faceStore?.enrolledUsername().orEmpty() }
        // With a password session the server takes the username from the token (mismatch => 403).
        val hasSession = AppAuth.session.isSignedIn
        val user = if (hasSession) "" else typedUser
        if (_state.value.faceBusy) return
        viewModelScope.launch {
            _state.update { it.copy(faceBusy = true, faceMessage = "Verifying…", faceError = false) }
            val b64 = LocalFaceTemplateStore.jpegToBase64(jpegBytes)
            // GPS is OPTIONAL: lat/lng/accuracy are only sent when a real fix exists; gpsMissing=true otherwise.
            val stamp = CaptureGeo.read()
            val liveResult = runCatching {
                io {
                    liveApi.faceVerify(
                        FaceVerifyRequest(
                            imageBase64 = b64,
                            username = user.ifBlank { null },
                            capturedAt = stamp.capturedAt,
                            lat = stamp.lat,
                            lng = stamp.lng,
                            accuracyM = stamp.accuracyM,
                            gpsMissing = stamp.gpsMissing,
                        ),
                    )
                }
            }
            val live = liveResult.getOrNull()
            // The SERVER decides (face_verified token). No client-side template match gates success.
            if (live != null && live.faceVerified && !live.accessToken.isNullOrBlank() && live.user != null &&
                com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.requiresClockIn(live.user.role)
            ) {
                // 1073: the face step IS the clock-in. Same photo + GPS + time; any failure keeps the guard OUT with a reason + Retry.
                val req = com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.buildCheckIn(
                    b64, stamp, java.time.Instant.now(), "att-" + java.util.UUID.randomUUID().toString().take(12),
                    gateId = _state.value.chosenGateId,
                )
                _state.update { it.copy(faceMessage = "Face matched — clocking you in…") }
                // 1074: mock location - restrict mode is blocked, soft mode goes out flagged (isMock sent).
                val mode = runCatching { io { liveApi.guardGeofence() } }.getOrNull()?.mode
                val mockMsg = com.satcop.smartvisitor.kiosk.ui.guardhome.GuardGeoLogic.mockBlock(
                    com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode.CHECK_IN,
                    com.satcop.smartvisitor.kiosk.ui.guardhome.GeoMode.parse(mode), stamp.isMock,
                )
                if (mockMsg != null) {
                    _state.update {
                        it.copy(
                            faceBusy = false, facePhase = FaceLoginPhase.CAPTURE_VERIFY, screen = KioskScreen.FACE_LOGIN,
                            faceMessage = mockMsg, faceError = true, toast = null,
                        )
                    }
                    return@launch
                }
                val ci = runCatching { io { liveApi.attendanceCheckIn(req) } }
                val ciErr = ci.exceptionOrNull()
                if (ciErr != null && com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.verifyOnlyFallback(live.user.role, ciErr)) {
                    // 1076: the server refuses attendance for this role: face-verify only, card says "Verified!".
                    _state.update {
                        it.copy(
                            faceBusy = true, faceMessage = "Verified", faceError = false,
                            verifyOnlySession = true,
                            verifiedCard = com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.verifiedResult(live.loginAt, null),
                            toast = null,
                        )
                    }
                    loadAfterLogin(live.user)
                    _state.update { it.copy(faceBusy = false, facePhase = FaceLoginPhase.HUB, faceMessage = null) }
                    return@launch
                }
                if (ciErr != null && !com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.alreadyIn(ciErr)) {
                    val text = com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.checkInFailureMessage(ciErr, req.gpsMissing)
                    _state.update {
                        it.copy(
                            faceBusy = false, facePhase = FaceLoginPhase.CAPTURE_VERIFY, screen = KioskScreen.FACE_LOGIN,
                            faceMessage = text, faceError = true, toast = null,
                        )
                    }
                    return@launch
                }
                // 1075: stay on the camera (no white Face login card, no lock flash) until the app data is in.
                _state.update {
                    it.copy(
                        faceBusy = true, faceMessage = "Checked in", faceError = false,
                        clockInRow = ci.getOrNull(),
                        dutyGateName = ci.getOrNull()?.let { r -> r.dutyGateName?.takeIf { n -> n.isNotBlank() } ?: r.gateName?.takeIf { n -> n.isNotBlank() && n != "—" } } ?: it.dutyGateName,
                        toast = if (ciErr == null) "Face matched. You are checked in." else null, toastKind = ToastKind.SUCCESS,
                    )
                }
                loadAfterLogin(live.user)
                _state.update { it.copy(faceBusy = false, facePhase = FaceLoginPhase.HUB, faceMessage = null) }
                return@launch
            }
            if (live != null && live.faceVerified && !live.accessToken.isNullOrBlank() && live.user != null) {
                _state.update {
                    it.copy(
                        faceBusy = false,
                        facePhase = FaceLoginPhase.HUB,
                        faceMessage = null,
                        faceError = false,
                        toast = live.warn?.takeIf { w -> w.isNotBlank() }
                            ?: if (stamp.gpsMissing) "Signed in · location unavailable" else null,
                        toastKind = if (live.warn.isNullOrBlank() && !stamp.gpsMissing) ToastKind.SUCCESS else ToastKind.WARNING,
                    )
                }
                loadAfterLogin(live.user)
                return@launch
            }
            // Failure: show the server's own message/code text (GEO_FENCE_RESTRICTED, FACE_MISMATCH, ...),
            // stay on the camera so Retry is one tap. 403 FACE_REQUIRED is NOT an error here (it is what we are fixing).
            val err = liveResult.exceptionOrNull()
            val text = if (err != null) {
                FaceErrors.message(err)
            } else {
                live?.message?.takeIf { it.isNotBlank() } ?: FaceErrors.MISMATCH_DEFAULT
            }
            _state.update {
                it.copy(
                    faceBusy = false,
                    facePhase = FaceLoginPhase.CAPTURE_VERIFY,
                    screen = KioskScreen.FACE_LOGIN,
                    faceMessage = text,
                    faceError = true,
                    toast = null,
                )
            }
        }
    }

    /** true = nested pop consumed; false = finish app (AC-BP1/BP2). */
    fun onSystemBack(): Boolean {
        val s = _state.value
        // 1074: with a half-open password session (guard face clock-in) Back does nothing; only Sign Out / Cancel leave.
        if (AppAuth.session.isSignedIn && !AppAuth.session.faceVerified && s.gateStage == GateStage.FACE_PENDING) {
            // 1075: Back on the camera returns to the lock screen (not while verifying); Back on the lock screen does nothing.
            if (!s.faceBusy && s.facePhase == FaceLoginPhase.CAPTURE_VERIFY) cancelFaceCapture()
            return true
        }
        if (!s.signedIn || !FaceGateMachine.canShowData(s.gateStage)) {
            if (s.screen == KioskScreen.FACE_LOGIN || s.gateStage == GateStage.FACE_PENDING) {
                when (s.facePhase) {
                    FaceLoginPhase.HUB -> { closeFaceLogin(); return true }
                    FaceLoginPhase.CONSENT_ENROLL -> { declineFaceConsent(); return true }
                    FaceLoginPhase.CAPTURE_ENROLL, FaceLoginPhase.CAPTURE_VERIFY -> {
                        cancelFaceCapture(); return true
                    }
                }
            }
            return false
        }
        if (s.isGateDesk() && s.homeRole() != KioskRole.HOST) return when {
            s.screen == KioskScreen.HISTORY_DETAIL -> { clearHistoryDetail(); true }
            s.screen != KioskScreen.HOME -> { closeGuardTool(); true }
            s.step > 1 -> { back(); true }
            else -> false
        }
        return when (s.homeRole()) {
            KioskRole.GATE -> when {
                s.screen == KioskScreen.HISTORY_DETAIL -> { clearHistoryDetail(); true }
                s.screen != KioskScreen.HOME -> { closeGuardTool(); true }
                s.step > 1 -> { back(); true }
                else -> false
            }
            KioskRole.GUARD, KioskRole.HOST, KioskRole.UNSUPPORTED -> false
        }
    }

    companion object {
        fun factory(repository: KioskRepository = HybridKioskRepository()): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KioskViewModel(repository) as T
                }
            }
    }
}
