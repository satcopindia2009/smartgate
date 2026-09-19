package com.satcop.smartvisitor.kiosk.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.api.LoginErrors
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KioskUiState(
    val signedIn: Boolean = false,
    val loginUsername: String = "pranay.gate",
    val loginPassword: String = "PranayGate@2026",
    val loginError: String? = null,
    val loginBusy: Boolean = false,
    val step: Int = 1,
    val schoolName: String = DemoFixtures.school.name,
    val schoolId: String = "",
    val timezone: String = DemoFixtures.SCHOOL_TZ,
    val clockLabel: String = "",
    val watermark: String = DemoFixtures.WATERMARK,
    val dataSource: DataSource = DataSource.FIXTURES,
    val meDisplayName: String = "",
    val meRole: String = "",
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
    val historyBusy: Boolean = false,
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
    val lfBusy: Boolean = false,
    val faceEnrolled: Boolean = false,
    val faceConsentAgreed: Boolean = false,
    val faceConsentAt: String? = null,
    val faceConsentVersion: String? = null,
    /** From GET /schools/me — off|soft|restrict when Backend READY. */
    val geoFenceMode: String? = null,
    val faceBusy: Boolean = false,
    val faceMessage: String? = null,
    val facePhase: FaceLoginPhase = FaceLoginPhase.HUB,
) {
    val selectedGate: Gate?
        get() = gates.firstOrNull { it.id == draft.gateId } ?: gates.firstOrNull()
}

class KioskViewModel(
    private val repository: KioskRepository = HybridKioskRepository(),
    private val liveApi: LiveVisitorApi = LiveVisitorApi(),
) : ViewModel() {

    private var faceStore: LocalFaceTemplateStore? = null

    private val _state = MutableStateFlow(KioskUiState())
    val state: StateFlow<KioskUiState> = _state.asStateFlow()

    private val clockFmt = DateTimeFormatter.ofPattern(
        "EEE, d MMM, hh:mm:ss a",
        Locale.ENGLISH,
    )
    private var hostPollJob: Job? = null

    init {
        viewModelScope.launch { tickClock() }
    }

    fun updateLoginUsername(value: String) {
        _state.update { it.copy(loginUsername = value, loginError = null) }
    }

    fun updateLoginPassword(value: String) {
        _state.update { it.copy(loginPassword = value, loginError = null) }
    }

    fun login() {
        val username = _state.value.loginUsername.trim()
        val password = _state.value.loginPassword
        if (username.isEmpty() || password.isEmpty()) {
            _state.update {
                it.copy(loginError = "Enter username and password")
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loginBusy = true, loginError = null, toast = null) }
            try {
                val user = repository.login(username, password)
                try {
                    loadAfterLogin(user)
                } catch (e: Exception) {
                    // Never hard-crash post-auth — land on minimal Gate/home shell.
                    applyIdentityHome(user, warning = true)
                    _state.update {
                        it.copy(
                            toast = "Signed in · home load issue: ${e.message?.take(80) ?: "error"}",
                            toastKind = ToastKind.WARNING,
                            loginBusy = false,
                            loaded = true,
                            screen = KioskScreen.HOME,
                        )
                    }
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
            val clock = _state.value.clockLabel
            _state.value = KioskUiState(clockLabel = clock)
        }
    }

    private suspend fun loadAfterLogin(loginUser: MeResponse) {
        runCatching { repository.warmup() }
        val me = runCatching { repository.me() }.getOrNull() ?: loginUser
        val schoolMe = runCatching { liveApi.schoolMe() }.getOrNull()
        if (schoolMe?.geoFenceMode != null) {
            _state.update { it.copy(geoFenceMode = schoolMe.geoFenceMode) }
        }
        when (KioskRole.fromJwt(me.role)) {
            KioskRole.GATE -> loadGateHome(me)
            KioskRole.HOST -> {
                applyIdentityHome(me)
                loadHostHome()
                startHostPendingPoll()
            }
            KioskRole.GUARD -> applyIdentityHome(me)
            KioskRole.UNSUPPORTED -> applyIdentityHome(me)
        }
    }

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
            val watermark = me.meta?.watermark
                ?: staff.meta?.watermark
                ?: DemoFixtures.WATERMARK
            val source = repository.dataSource
            _state.update {
                it.copy(
                    signedIn = true,
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
                        "FIXTURES · signed in · live directory unreachable"
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
                loginBusy = false,
                loginError = null,
                loginPassword = "",
                schoolName = me.schoolId.ifBlank { it.schoolName },
                schoolId = me.schoolId,
                watermark = me.meta?.watermark ?: DemoFixtures.WATERMARK,
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
        if (_state.value.homeRole() != KioskRole.GATE) return
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
                        toast = e.message ?: "Meeting-done failed",
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
            _state.update { it.copy(hostBusy = true, toast = null) }
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
            } catch (e: ApiException) {
                if (e.code == AfterHoursCopy.CODE) {
                    _state.update {
                        it.copy(
                            hostBusy = false,
                            afterHours = true,
                            toast = AfterHoursCopy.HOST_NO_OP,
                            toastKind = ToastKind.WARNING,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            hostBusy = false,
                            toast = e.message,
                            toastKind = ToastKind.ERROR,
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        hostBusy = false,
                        toast = e.message ?: "Approve failed",
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun startReject(visitId: String) {
        _state.update { it.copy(rejectingVisitId = visitId, rejectReason = null) }
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
                        toast = e.message ?: "Reject failed",
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
                        toast = e.message ?: "Pickup failed",
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
                        toast = e.message ?: "Consent failed",
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
                        toast = e.message ?: "Release failed",
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    private fun startHostPendingPoll() {
        hostPollJob?.cancel()
        hostPollJob = viewModelScope.launch {
            while (true) {
                delay(15_000)
                val snap = _state.value
                if (!snap.signedIn || snap.homeRole() != KioskRole.HOST) return@launch
                loadHostHome(showToast = false)
            }
        }
    }

    private suspend fun loadHostHome(showToast: Boolean = false) {
        val hostId = _state.value.meStaffId.ifBlank { null }
        _state.update { it.copy(hostBusy = true) }
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
            val notes = repository.listNotifications()
            val pendingNote = notes.firstOrNull { it.event == "visit.pending" }
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
                        pendingNote != null -> pendingNote.body ?: "New visit pending"
                        showToast && pending.isEmpty() -> "No pending visits"
                        showToast -> "Pending · ${pending.size}"
                        else -> it.toast
                    },
                    toastKind = when {
                        pendingNote != null -> ToastKind.INFO
                        showToast -> ToastKind.INFO
                        else -> it.toastKind
                    },
                )
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    hostBusy = false,
                    pendingVisits = emptyList(),
                    toast = e.message ?: "Could not load pending",
                    toastKind = ToastKind.ERROR,
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
                    toast = e.message ?: "Student search failed",
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
            val label = ZonedDateTime.now(zone).format(clockFmt) + " IST"
            _state.update { it.copy(clockLabel = label) }
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
                draft = it.draft.copy(hostId = hostId),
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
                _state.update { it.copy(autofetchBusy = false, autofetchHint = e.message ?: "Lookup failed") }
            }
        }
    }

    fun updatePurpose(value: String) = patchDraft { copy(purpose = value) }

    fun updateVehicle(value: String) = patchDraft { copy(vehicleNumber = value) }

    fun updateAccompanying(value: String) = patchDraft { copy(accompanyingCount = value) }

    fun updateNotes(value: String) = patchDraft { copy(notes = value) }

    fun selectIdType(apiValue: String) = patchDraft { copy(idType = apiValue) }

    fun updateIdNumber(value: String) = patchDraft { copy(idNumber = value) }

    fun setLivePhoto(bitmap: Bitmap?) {
        if (!_state.value.draft.consentAgreed) {
            _state.update {
                it.copy(toast = "Agree to visitor notice before photo", toastKind = ToastKind.WARNING)
            }
            return
        }
        val bmp = bitmap ?: PlaceholderBitmap.livePhoto(_state.value.draft.visitorName)
        _state.update {
            it.copy(
                livePhoto = bmp,
                draft = it.draft.copy(livePhotoCaptured = true),
                fieldErrors = it.fieldErrors - "livePhotoKey",
                toast = if (bitmap == null) "Live photo captured (demo placeholder)" else "Live photo captured",
                toastKind = ToastKind.SUCCESS,
            )
        }
    }

    fun setIdImage(bitmap: Bitmap?) {
        if (!_state.value.draft.consentAgreed) {
            _state.update {
                it.copy(toast = "Agree to visitor notice before ID capture", toastKind = ToastKind.WARNING)
            }
            return
        }
        val bmp = bitmap ?: PlaceholderBitmap.idCard(_state.value.draft)
        _state.update {
            it.copy(
                idImage = bmp,
                draft = it.draft.copy(idImageCaptured = true),
                fieldErrors = it.fieldErrors - "idNumber",
                toast = if (bitmap == null) "ID image attached (demo)" else "ID image attached",
                toastKind = ToastKind.INFO,
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

    fun prefillSample() {
        if (_state.value.hideDemoStory) {
            _state.update {
                it.copy(toast = "Sample chips are off for this school", toastKind = ToastKind.INFO)
            }
            return
        }
        applyDraft(RegistrationDraft.fromStory(_state.value.story), "Sample parent visit loaded (fixture)")
    }

    fun applyBlockSample() {
        applyDraft(RegistrationDraft.blockSample(), "Block sample loaded · Vikram More")
    }

    fun applyAlertSample() {
        applyDraft(RegistrationDraft.alertSample(), "Alert sample loaded · Neha Salunkhe")
    }

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
        val errors = RegistrationValidator.validateStep3(current.draft)
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
                    toast = "Visitor notice consent required",
                    toastKind = ToastKind.WARNING,
                )
            }
            return
        }
        try {
            val liveBmp = snap.livePhoto ?: PlaceholderBitmap.livePhoto(draft.visitorName)
            val photo = repository.uploadMedia(
                PlaceholderBitmap.toJpeg(liveBmp),
                "live-photo.jpg",
                "image/jpeg",
                "live_photo",
            )
            val idKey = snap.idImage?.let {
                repository.uploadMedia(
                    PlaceholderBitmap.toJpeg(it),
                    "id-image.jpg",
                    "image/jpeg",
                    "id_image",
                ).key
            }
            val sigKey = snap.signature?.let {
                repository.uploadMedia(
                    PlaceholderBitmap.toJpeg(it),
                    "signature.jpg",
                    "image/jpeg",
                    "signature",
                ).key
            }
            val mobileTen = MobileIndia.tenDigit(draft.mobile) ?: draft.mobile.filter { it.isDigit() }
            val hit = repository.matchBlacklist(
                mobile = mobileTen,
                idType = draft.idType,
                idNumber = draft.idNumber.trim().ifEmpty { null },
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
            val body = VisitCreate(
                visitorName = draft.visitorName.trim(),
                mobile = mobileTen,
                visitorType = draft.visitorType,
                purpose = draft.purpose.trim(),
                hostId = draft.hostId.orEmpty(),
                livePhotoKey = photo.key,
                idType = draft.idType,
                idNumber = draft.idNumber.trim().ifEmpty { null },
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
                        after -> "After hours · ${AfterHoursCopy.HOST_NO_OP}"
                        source == DataSource.FIXTURES -> "FIXTURES · host notified · Demo approve to issue QR"
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
            if (e.code == "BLACKLIST_BLOCK") {
                _state.update {
                    it.copy(
                        submitting = false,
                        blocked = true,
                        toast = e.message,
                        toastKind = ToastKind.ERROR,
                        dataSource = repository.dataSource,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        submitting = false,
                        toast = e.message,
                        toastKind = ToastKind.ERROR,
                        dataSource = repository.dataSource,
                    )
                }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(
                    submitting = false,
                    toast = e.message ?: "Submit failed",
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
                        toast = if (silent) it.toast else (e.message ?: "Refresh failed"),
                        toastKind = if (silent) it.toastKind else ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun demoApprove() {
        val id = _state.value.createdVisit?.id ?: return
        viewModelScope.launch {
            _state.update { it.copy(outcomeBusy = true) }
            try {
                val visit = repository.demoApprove(id)
                applyVisit(visit, "Demo host approved · pass ${visit.passId}", ToastKind.SUCCESS)
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        outcomeBusy = false,
                        toast = e.message ?: "Demo approve failed",
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

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
                        toast = e.message ?: "Scan failed",
                        toastKind = ToastKind.ERROR,
                    )
                }
            }
        }
    }

    fun loadStoryPass() {
        if (_state.value.hideDemoStory) {
            _state.update {
                it.copy(toast = "Demo story is off for this school", toastKind = ToastKind.INFO)
            }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(outcomeBusy = true) }
            try {
                val visit = repository.storyPass()
                applyVisit(
                    visit,
                    "Story pass ${visit.passId} · ${visit.visitorName} · ${visit.status}",
                    ToastKind.INFO,
                )
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        outcomeBusy = false,
                        toast = e.message ?: "Could not load P-4F21",
                        toastKind = ToastKind.ERROR,
                        dataSource = repository.dataSource,
                    )
                }
            }
        }
    }

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
    fun selectHistory(event: GuardHistoryEvent) { _state.update { it.copy(historySelected = event, screen = KioskScreen.HISTORY_DETAIL) } }
    fun clearHistoryDetail() { _state.update { it.copy(historySelected = null, screen = KioskScreen.HISTORY) } }
    fun refreshHistory() { viewModelScope.launch { refreshHistoryInternal() } }
    private suspend fun refreshHistoryInternal() {
        val s = _state.value
        try {
            val rows = repository.listGuardHistory(todayOnly = s.historyTodayOnly, kind = s.historyKindFilter, status = s.historyStatusFilter, gateId = s.selectedGate?.id)
            _state.update { it.copy(historyEvents = rows, historyBusy = false, dataSource = repository.dataSource) }
        } catch (e: Exception) {
            _state.update { it.copy(historyBusy = false, toast = e.message ?: "History failed", toastKind = ToastKind.ERROR) }
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
                _state.update { it.copy(courierBusy = false, toast = e.message ?: "Courier save failed", toastKind = ToastKind.ERROR) }
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
                _state.update { it.copy(courierBusy = false, toast = e.message ?: "Handover failed", toastKind = ToastKind.ERROR) }
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
                _state.update { it.copy(checkoutBusy = false, toast = e.message ?: "Inside list failed", toastKind = ToastKind.ERROR) }
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
                _state.update { it.copy(checkoutBusy = false, toast = e.message ?: "Checkout rejected", toastKind = ToastKind.ERROR) }
            }
        }
    }
    fun openLostFound() {
        _state.update { it.copy(screen = KioskScreen.LOST_FOUND, lfFoundAt = LocalVisitStore.nowIst(), lfFinder = it.meDisplayName.ifBlank { "Gate guard" }) }
    }
    fun updateLfDescription(v: String) = _state.update { it.copy(lfDescription = v) }
    fun updateLfLocation(v: String) = _state.update { it.copy(lfLocation = v) }
    fun updateLfFinder(v: String) = _state.update { it.copy(lfFinder = v) }
    fun updateLfFinderMobile(v: String) = _state.update { it.copy(lfFinderMobile = v) }
    fun updateLfFoundAt(v: String) = _state.update { it.copy(lfFoundAt = v) }
    fun submitLostFound() {
        val s = _state.value
        if (s.lfDescription.isBlank() || s.lfLocation.isBlank()) {
            _state.update { it.copy(toast = "Description and location required", toastKind = ToastKind.ERROR) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(lfBusy = true) }
            try {
                val created = repository.createLostFound(
                    LostFoundCreate(
                        description = s.lfDescription,
                        locationFound = s.lfLocation,
                        foundAt = s.lfFoundAt.ifBlank { LocalVisitStore.nowIst() },
                        finderName = s.lfFinder.ifBlank { s.meDisplayName.ifBlank { "Gate guard" } },
                        finderMobile = s.lfFinderMobile.ifBlank { null },
                        gateId = s.selectedGate?.id ?: s.draft.gateId,
                        photoKey = "media/lost_found/placeholder",
                    ),
                )
                _state.update {
                    it.copy(
                        lfBusy = false,
                        lfDescription = "",
                        lfLocation = "",
                        lfFinderMobile = "",
                        toast = "LF Open · ${created.id}",
                        toastKind = ToastKind.SUCCESS,
                        screen = KioskScreen.HOME,
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(lfBusy = false, toast = e.message ?: "LF create failed", toastKind = ToastKind.ERROR) }
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
        val enrolled = store?.isEnrolled(user) == true || store?.hasAny() == true
        val enrolledUser = if (user.isNotBlank() && store?.isEnrolled(user) == true) user else store?.enrolledUsername().orEmpty()
        _state.update {
            it.copy(
                screen = KioskScreen.FACE_LOGIN,
                facePhase = FaceLoginPhase.HUB,
                faceMessage = null,
                faceEnrolled = enrolled,
                faceConsentAgreed = enrolled && !store?.consentAt(enrolledUser).isNullOrBlank(),
                faceConsentAt = store?.consentAt(enrolledUser),
                faceConsentVersion = store?.consentVersion(enrolledUser),
                loginUsername = it.loginUsername.ifBlank { enrolledUser },
            )
        }
    }

    fun closeFaceLogin() {
        _state.update {
            it.copy(
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
            it.copy(facePhase = FaceLoginPhase.CAPTURE_VERIFY, faceMessage = null)
        }
    }

    fun cancelFaceCapture() {
        _state.update {
            it.copy(facePhase = FaceLoginPhase.HUB, faceBusy = false, faceMessage = null)
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
                    liveApi.login(user, pw)
                }
                liveApi.faceEnroll(
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
                )
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
                            faceMessage = e.message,
                            toast = GeoFenceCodes.toastMessage(),
                            toastKind = ToastKind.ERROR,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            faceBusy = false,
                            faceEnrolled = true,
                            facePhase = FaceLoginPhase.HUB,
                            faceMessage = "Local demo enroll OK · live pending (${e.message.take(100)})",
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
                        faceMessage = "Local demo enroll OK · live pending (${e.message?.take(100) ?: "error"})",
                        toast = "Face enrolled · staff only",
                        toastKind = ToastKind.SUCCESS,
                    )
                }
            }
        }
    }

    private fun verifyFace(jpegBytes: ByteArray) {
        val user = _state.value.loginUsername.trim().ifBlank { faceStore?.enrolledUsername().orEmpty() }
        val store = faceStore
        viewModelScope.launch {
            _state.update { it.copy(faceBusy = true, faceMessage = "Verifying…") }
            val b64 = LocalFaceTemplateStore.jpegToBase64(jpegBytes)
            val stamp = CaptureGeo.read()
            val liveResult = runCatching {
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
            val liveErr = liveResult.exceptionOrNull()
            if (liveErr is ApiException) {
                val geo = GeoFenceCodes.isRestricted(liveErr.code, liveErr.message)
                if (geo) {
                    _state.update {
                        it.copy(
                            faceBusy = false,
                            facePhase = FaceLoginPhase.HUB,
                            faceMessage = liveErr.message,
                            toast = GeoFenceCodes.toastMessage(),
                            toastKind = ToastKind.ERROR,
                        )
                    }
                    return@launch
                }
            }
            val live = liveResult.getOrNull()
            if (live != null && !live.accessToken.isNullOrBlank() && live.user != null) {
                val warn = if (stamp.gpsMissing) " · GPS unavailable (allowed)" else ""
                _state.update {
                    it.copy(
                        faceBusy = false,
                        facePhase = FaceLoginPhase.HUB,
                        faceMessage = "Live face verify OK$warn",
                        toast = if (stamp.gpsMissing) "Face OK · location missing (gpsMissing)" else null,
                        toastKind = if (stamp.gpsMissing) ToastKind.WARNING else ToastKind.SUCCESS,
                    )
                }
                loadAfterLogin(live.user)
                return@launch
            }
            if (live != null && live.matched && live.user != null && !live.accessToken.isNullOrBlank()) {
                _state.update { it.copy(faceBusy = false) }
                loadAfterLogin(live.user)
                return@launch
            }
            val local = store?.localVerify(user, jpegBytes)
            if (local?.matched == true) {
                _state.update {
                    it.copy(
                        faceBusy = false,
                        facePhase = FaceLoginPhase.HUB,
                        screen = KioskScreen.HOME,
                        loginUsername = local.username ?: it.loginUsername,
                        faceMessage = local.message,
                        toast = "Face OK (local demo) · enter password (AC-FL1)",
                        toastKind = ToastKind.INFO,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        faceBusy = false,
                        facePhase = FaceLoginPhase.HUB,
                        faceMessage = local?.message
                            ?: live?.message
                            ?: "Face match failed — use password (AC-FL3)",
                        toast = "Face failed · use password",
                        toastKind = ToastKind.WARNING,
                    )
                }
            }
        }
    }

    /** true = nested pop consumed; false = finish app (AC-BP1/BP2). */
    fun onSystemBack(): Boolean {
        val s = _state.value
        if (!s.signedIn) {
            if (s.screen == KioskScreen.FACE_LOGIN) {
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
