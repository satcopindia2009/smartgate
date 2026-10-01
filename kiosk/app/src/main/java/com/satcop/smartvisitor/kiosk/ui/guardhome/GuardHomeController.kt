package com.satcop.smartvisitor.kiosk.ui.guardhome

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.GeofenceInfo
import com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Backend calls the Guard Home needs. Blocking; the controller runs them on [GuardHomeController.io]. */
interface GuardHomeApi {
    fun attendanceToday(): TodayAttendance
    fun checkIn(req: AttendanceRequest): AttendanceRow
    fun clockOut(req: AttendanceRequest): AttendanceRow
    fun visitsBetween(dateFrom: String, dateTo: String, q: String?): List<VisitOut>
    /** Guard Today patrol + incidents summary. Fails with FACE_REQUIRED for an unverified guard. */
    fun todaySummary(): GuardTodaySummary
    /** 1072: GET /guards/me/geofence (mode, radius, centre, rules). Null when the server does not provide it. */
    fun guardGeofence(): GeofenceInfo? = null
    /** 1072: fallback for the geofence mode: GET /schools/me `geoFenceMode` (readable by every signed-in role). */
    fun schoolGeoMode(): String? = null
    /** 1074: /schools/me guardSessionCutoff ("HH:mm"); null = default 00:00. */
    fun guardSessionCutoff(): String? = null
}

enum class HomeView { HOME, LIST, FIND }

/** CAMERA/LOCATION -> app permission settings. LOCATION_SERVICE -> the phone's Location switch (permission is granted, GPS is off). */
enum class SettingsHint { CAMERA, LOCATION, LOCATION_SERVICE }

data class GuardHomeState(
    val today: LocalDate = GuardHomeLogic.todayIst(),
    val loading: Boolean = false,
    val attendance: TodayAttendance? = null,
    val attendanceError: String? = null,
    val visits: List<VisitOut> = emptyList(),
    val visitsLoaded: Boolean = false,
    val visitsError: String? = null,
    val view: HomeView = HomeView.HOME,
    val listFilter: VisitFilter = VisitFilter.PENDING,
    /** Non-null while the verification panel is open. */
    val panel: AttendanceMode? = null,
    val panelBusy: Boolean = false,
    val panelMessage: String? = null,
    val panelError: Boolean = false,
    val failedFixElapsedMs: Long? = null,
    val findQuery: String = "",
    val findBusy: Boolean = false,
    val findResults: List<VisitOut>? = null,
    val findMessage: String? = null,
    val toast: String? = null,
    // --- 1064 clock-in flow ---
    /** True once GET /attendance/me/today answered (or failed) at least once. */
    val attendanceLoaded: Boolean = false,
    val step: ClockStep = ClockStep.INFO,
    /** Non-null after a successful check-in / check-out: shows "Checked In!" / "Checked Out!" until Done. */
    val result: ClockResult? = null,
    /** Bumped after every failed attempt: the UI drops the old selfie (retry ALWAYS recaptures). */
    val attemptNo: Int = 0,
    /** Non-blocking notice on the selfie screen (e.g. location off in soft mode). */
    val notice: String? = null,
    val settingsHint: SettingsHint? = null,
    // --- 1070 Guard Today summary (non-blocking; a failure leaves the rest of Today working) ---
    val todaySummary: GuardTodaySummary? = null,
    /** Plain sentence when the last summary call failed (e.g. FACE_REQUIRED -> verify face); null otherwise. */
    val summaryError: String? = null,
    val summaryLoaded: Boolean = false,
    // --- 1072 geofence / location / shift end ---
    /** GET /guards/me/geofence (or the geofence block of /attendance/me/today). Null until read. */
    val geofence: GeofenceInfo? = null,
    /** /schools/me geoFenceMode, read only when the geofence call gave no mode. */
    val schoolGeoMode: String? = null,
    /** Elapsed-realtime ms at which the selfie step opened; drives the soft-mode "no GPS yet" timeout. */
    val selfieOpenedElapsedMs: Long? = null,
    /** True while the "Your shift time is over. Clock out now?" prompt is showing. */
    val shiftEndPrompt: Boolean = false,
    /** The IST day for which the prompt was already shown: it appears once per day. */
    val shiftEndPromptedFor: LocalDate? = null,
    /** 1074: school business-day cutoff from /schools/me (null = 00:00). */
    val sessionCutoff: String? = null,
) {
    val summary: VisitSummary get() = GuardHomeLogic.summary(visits, today)
    private val fenceInfo: GeofenceInfo? get() = geofence ?: attendance?.geofence
    val geoMode: GeoMode get() = GuardGeoLogic.modeOf(fenceInfo, schoolGeoMode)
    val accuracyLimitM: Double get() = GuardGeoLogic.accuracyLimitM(fenceInfo)
    val fence: GeofenceInfo? get() = fenceInfo
}

/**
 * Guard Home logic, kept free of Android types so it is unit-testable. Every screen value comes
 * from a fresh server read (no caching across refreshes), and attendance state is whatever
 * `GET /attendance/me/today` says — a clock-out survives app kill because it lives on the server.
 */
class GuardHomeController(
    private val scope: CoroutineScope,
    private val api: GuardHomeApi,
    val io: CoroutineDispatcher = Dispatchers.IO,
    private val nowElapsedMs: () -> Long,
    private val nowInstant: () -> Instant = { Instant.now() },
    private val newAttemptId: () -> String = { "att-" + UUID.randomUUID().toString().take(12) },
    hasLocationPermission: () -> Boolean = { true },
) {
    /** Set by the panel from the real runtime permission state. */
    @Volatile
    var hasLocationPermission: () -> Boolean = hasLocationPermission
    private val _state = MutableStateFlow(GuardHomeState())
    val state: StateFlow<GuardHomeState> = _state.asStateFlow()

    @Volatile
    private var refreshInFlight = false

    /** 1079: /guards/me/today-summary is a patrol route: called only when the guard has PATROL duty. Default true (tests, legacy). */
    @Volatile
    var patrolCalls: Boolean = true

    fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        val today = GuardHomeLogic.todayIst(nowInstant())
        _state.update { it.copy(loading = true, today = today) }
        loadGeofence()
        // Summary is its own coroutine + runCatching: it never delays or breaks attendance/visits.
        if (patrolCalls) scope.launch {
            val sum = runCatching { withContext(io) { api.todaySummary() } }
            _state.update {
                it.copy(
                    todaySummary = sum.getOrNull() ?: it.todaySummary,
                    summaryError = sum.exceptionOrNull()?.let(ErrorCopy::forThrowable),
                    summaryLoaded = true,
                )
            }
        }
        scope.launch {
            val att = runCatching { withContext(io) { api.attendanceToday() } }
            val day = today.toString()
            val vis = runCatching { withContext(io) { api.visitsBetween(day, day, null) } }
            refreshInFlight = false
            _state.update {
                it.copy(
                    loading = false,
                    attendanceLoaded = true,
                    // Keep the last good card on a failed refresh; the error line explains why.
                    attendance = att.getOrNull() ?: it.attendance,
                    attendanceError = att.exceptionOrNull()?.let(ErrorCopy::forThrowable),
                    visits = vis.getOrNull() ?: it.visits,
                    visitsLoaded = vis.isSuccess || it.visitsLoaded,
                    visitsError = vis.exceptionOrNull()?.let(ErrorCopy::forThrowable),
                )
            }
        }
    }

    /** Lock-screen refresh: attendance only (no visitor data is read before clock-in). */
    fun refreshAttendance() {
        if (refreshInFlight) return
        refreshInFlight = true
        loadGeofence()
        scope.launch {
            val att = runCatching { withContext(io) { api.attendanceToday() } }
            refreshInFlight = false
            _state.update {
                it.copy(
                    attendanceLoaded = true,
                    attendance = att.getOrNull() ?: it.attendance,
                    attendanceError = att.exceptionOrNull()?.let(ErrorCopy::forThrowable),
                )
            }
        }
    }

    /**
     * Geofence mode / radius / rules, non-blocking: a failure leaves the defaults (the server still decides).
     * Mode comes from /guards/me/geofence, else from /schools/me.
     */
    private fun loadGeofence() {
        scope.launch {
            val g = runCatching { withContext(io) { api.guardGeofence() } }.getOrNull()
            val m = if (GuardGeoLogic.modeOf(g, null) == GeoMode.UNKNOWN) {
                runCatching { withContext(io) { api.schoolGeoMode() } }.getOrNull()
            } else null
            val cut = if (_state.value.sessionCutoff == null) runCatching { withContext(io) { api.guardSessionCutoff() } }.getOrNull() else null
            _state.update { it.copy(geofence = g ?: it.geofence, schoolGeoMode = m ?: it.schoolGeoMode, sessionCutoff = cut ?: it.sessionCutoff) }
        }
    }

    /** Info screen -> selfie screen. */
    fun proceedToSelfie() {
        _state.update {
            it.copy(
                step = ClockStep.SELFIE, panelMessage = null, panelError = false, notice = null, settingsHint = null,
                selfieOpenedElapsedMs = nowElapsedMs(),
            )
        }
        loadGeofence()
    }

    // --- shift end reminder (G): once per day, non-blocking, never an automatic clock-out ---

    /** Called on a timer while the guard is on duty. Shows the prompt once the shift end has passed. */
    fun evaluateShiftEnd() {
        val s = _state.value
        if (s.shiftEndPrompt || s.panel != null || s.result != null) return
        val a = s.attendance ?: return
        val due = GuardGeoLogic.shiftEndDue(
            state = a.state,
            dutyDate = a.dutyDate,
            shiftEndTime = a.shiftEndTime ?: a.attendance?.shiftEndTime,
            now = nowInstant(),
            alreadyPromptedFor = s.shiftEndPromptedFor,
        )
        if (due) _state.update { it.copy(shiftEndPrompt = true, shiftEndPromptedFor = GuardHomeLogic.todayIst(nowInstant())) }
    }

    /** 1074: show the "Checked In!" card for a check-in done by the face step (row = the check-in response). */
    fun showVerifiedResult(r: ClockResult) {
        _state.update { it.copy(result = r) }
    }

    fun showCheckInResult(row: AttendanceRow) {
        _state.update { it.copy(result = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row, fallbackGateName = it.attendance?.gateName)) }
    }

    fun dismissShiftEndPrompt() {
        _state.update { it.copy(shiftEndPrompt = false) }
    }

    /** "Clock out" on the prompt: opens the normal Self Check Out flow (fresh selfie + location). */
    fun acceptShiftEndPrompt() {
        _state.update { it.copy(shiftEndPrompt = false) }
        openPanel(AttendanceMode.CLOCK_OUT)
    }

    /** The UI reports the phone's Location switch (permission can be granted while GPS is off). */
    @Volatile
    var locationServiceOn: () -> Boolean = { true }

    /** "Done" on the result screen. */
    fun clearResult() {
        _state.update { it.copy(result = null) }
    }

    fun reportCameraDenied() {
        _state.update { it.copy(panelMessage = ClockInLogic.CAMERA_OFF, panelError = true, settingsHint = SettingsHint.CAMERA) }
    }

    fun openList(filter: VisitFilter) {
        _state.update { it.copy(view = HomeView.LIST, listFilter = filter) }
    }

    fun openFind() {
        _state.update { it.copy(view = HomeView.FIND) }
    }

    /** @return true when a sub view was closed (back consumed). */
    fun back(): Boolean {
        val s = _state.value
        if (s.panel != null && !s.panelBusy) { closePanel(); return true }
        if (s.panel != null) return true
        if (s.view != HomeView.HOME) {
            _state.update { it.copy(view = HomeView.HOME, findQuery = "", findResults = null, findMessage = null) }
            return true
        }
        return false
    }

    fun setFindQuery(q: String) {
        _state.update { it.copy(findQuery = q, findMessage = null, findResults = null) }
    }

    fun search() {
        val q = _state.value.findQuery.trim()
        if (!GuardHomeLogic.validQuery(q)) {
            _state.update { it.copy(findMessage = "Type at least ${GuardHomeLogic.MIN_QUERY} letters or digits.", findResults = null) }
            return
        }
        if (_state.value.findBusy) return
        _state.update { it.copy(findBusy = true, findMessage = null, findResults = null) }
        scope.launch {
            val (from, to) = GuardHomeLogic.searchWindow(GuardHomeLogic.todayIst(nowInstant()))
            val res = runCatching { withContext(io) { api.visitsBetween(from, to, q) } }
            _state.update {
                // Ignore a late answer for an older query.
                if (it.findQuery.trim() != q) return@update it.copy(findBusy = false)
                res.fold(
                    onSuccess = { list ->
                        it.copy(
                            findBusy = false,
                            findResults = list,
                            findMessage = if (list.isEmpty()) GuardHomeLogic.NOT_PRESENT else null,
                        )
                    },
                    onFailure = { e -> it.copy(findBusy = false, findResults = null, findMessage = ErrorCopy.forThrowable(e)) },
                )
            }
        }
    }

    // --- verification panel ---

    fun openPanel(mode: AttendanceMode) {
        _state.update {
            it.copy(
                panel = mode, step = ClockStep.INFO, panelBusy = false, panelMessage = null, panelError = false,
                failedFixElapsedMs = null, notice = null, settingsHint = null, result = null,
            )
        }
    }

    fun closePanel() {
        _state.update { it.copy(panel = null, step = ClockStep.INFO, panelBusy = false, panelMessage = null, panelError = false, notice = null, settingsHint = null) }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    /** The photo was discarded (retake) -> clear a stale message. */
    fun onPhotoRetaken() {
        _state.update { it.copy(panelMessage = null, panelError = false) }
    }

    /** Permission denied etc. reported by the panel UI. */
    fun reportPanelProblem(message: String) {
        _state.update { it.copy(panelMessage = message, panelError = true) }
    }

    fun submit(photoBase64: String?, photoAtElapsedMs: Long?, fix: GpsFix?) {
        val s = _state.value
        val mode = s.panel ?: return
        if (s.panelBusy) return
        if (s.step != ClockStep.SELFIE) return
        val perm = hasLocationPermission()
        val serviceOn = locationServiceOn()
        val geo = s.geoMode
        // 1072 C/B: a location problem that must stop the attempt (restrict mode, or any clock-out): no request, no record.
        GuardGeoLogic.locationGate(mode, geo, perm, serviceOn)?.let { gate ->
            _state.update {
                it.copy(
                    panelMessage = gate.message, panelError = true, attemptNo = it.attemptNo + 1,
                    settingsHint = if (gate.action == LocationAction.LOCATION_SETTINGS) SettingsHint.LOCATION_SERVICE else SettingsHint.LOCATION,
                )
            }
            return
        }
        // 1074 D: mock location - restrict mode and every clock-out are blocked; soft mode goes out flagged.
        GuardGeoLogic.mockBlock(mode, geo, fix?.isMock == true)?.let { msg ->
            _state.update { it.copy(panelMessage = msg, panelError = true, attemptNo = it.attemptNo + 1) }
            return
        }
        val waitedMs = s.selfieOpenedElapsedMs?.let { nowElapsedMs() - it } ?: 0L
        val sendNoLocation = (mode == AttendanceMode.CHECK_IN && !perm) ||
            GuardGeoLogic.checkInWithoutLocation(mode, geo, perm, serviceOn, fix != null, waitedMs)
        val decision = ClockInLogic.locationDecision(mode, perm, fix != null || sendNoLocation)
        if (decision is ClockInLogic.LocationDecision.Blocked) {
            _state.update {
                it.copy(panelMessage = decision.message, panelError = true, attemptNo = it.attemptNo + 1, settingsHint = SettingsHint.LOCATION)
            }
            return
        }
        val useFix: GpsFix?
        if (sendNoLocation) {
            // Soft/off/unknown mode: allowed with a flag. Restrict mode never gets here; the server also refuses it (LOCATION_REQUIRED).
            val blocked = AttendanceRules.photoReadiness(mode, photoBase64 != null, photoAtElapsedMs, nowElapsedMs())
            if (blocked != null) {
                _state.update { it.copy(panelMessage = blocked.message, panelError = true, attemptNo = it.attemptNo + 1) }
                return
            }
            useFix = null
        } else {
            val ready = AttendanceRules.readiness(
                mode = mode,
                hasPhoto = photoBase64 != null,
                photoAtElapsedMs = photoAtElapsedMs,
                fix = fix,
                failedFixElapsedMs = s.failedFixElapsedMs,
                nowElapsedMs = nowElapsedMs(),
                locationPermission = perm,
                accuracyLimitM = s.accuracyLimitM,
            )
            if (ready is Readiness.Blocked) {
                _state.update { it.copy(panelMessage = ready.message, panelError = true, attemptNo = it.attemptNo + 1) }
                return
            }
            useFix = (ready as Readiness.Ready).fix
        }
        val req = AttendanceRules.buildRequest(useFix, photoBase64, nowInstant(), newAttemptId())
        _state.update { it.copy(panelBusy = true, panelMessage = null, panelError = false) }
        scope.launch {
            val res = runCatching {
                withContext(io) {
                    if (mode == AttendanceMode.CHECK_IN) api.checkIn(req) else api.clockOut(req)
                }
            }
            val err = res.exceptionOrNull()
            if (err == null) {
                val row = res.getOrThrow()
                _state.update {
                    it.copy(
                        panel = null, step = ClockStep.INFO, panelBusy = false, panelMessage = null, panelError = false,
                        notice = null, settingsHint = null,
                        result = ClockInLogic.resultFrom(mode, row, fallbackGateName = s.attendance?.gateName ?: s.attendance?.attendance?.gateName, sentWithoutLocation = sendNoLocation, sentMock = useFix?.isMock == true),
                        toast = if (mode == AttendanceMode.CHECK_IN) "You are checked in." else "You are clocked out.",
                    )
                }
                refresh()
                return@launch
            }
            val apiErr = (err as? ApiException)?.let(GuardGeoLogic::normalize)
            val code = apiErr?.code.orEmpty()
            val refusedNoLoc = ClockInLogic.checkInRefusedWithoutLocation(code, sendNoLocation)
            val serviceOffRefusal = refusedNoLoc && perm && !serviceOn
            val msg = when {
                serviceOffRefusal -> GuardGeoLogic.LOCATION_OFF_RESTRICT
                refusedNoLoc -> ClockInLogic.LOCATION_NEEDED
                // A: restrict-mode refusal with a distance -> "You are about X m from the campus; the limit is Y m."
                apiErr != null -> GuardGeoLogic.outsideMessageFrom(apiErr, s.geofence?.radiusM) ?: ErrorCopy.forThrowable(apiErr)
                else -> ErrorCopy.forThrowable(err)
            }
            when {
                AttendanceRules.stateAlreadyMoved(code) -> {
                    _state.update { it.copy(panel = null, panelBusy = false, toast = msg) }
                    refresh()
                }
                else -> _state.update {
                    it.copy(
                        panelBusy = false,
                        panelMessage = msg,
                        panelError = true,
                        attemptNo = it.attemptNo + 1,
                        settingsHint = when {
                            serviceOffRefusal -> SettingsHint.LOCATION_SERVICE
                            refusedNoLoc -> SettingsHint.LOCATION
                            else -> null
                        },
                        failedFixElapsedMs = if (AttendanceRules.needsFreshReading(code) && useFix != null) useFix.elapsedMs else it.failedFixElapsedMs,
                    )
                }
            }
        }
    }
}
