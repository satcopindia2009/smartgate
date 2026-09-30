package com.satcop.smartvisitor.kiosk.ui.guardhome

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
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
}

enum class HomeView { HOME, LIST, FIND }

enum class SettingsHint { CAMERA, LOCATION }

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
) {
    val summary: VisitSummary get() = GuardHomeLogic.summary(visits, today)
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

    fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        val today = GuardHomeLogic.todayIst(nowInstant())
        _state.update { it.copy(loading = true, today = today) }
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

    /** Info screen -> selfie screen. */
    fun proceedToSelfie() {
        _state.update { it.copy(step = ClockStep.SELFIE, panelMessage = null, panelError = false, notice = null, settingsHint = null) }
    }

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
        val sendNoLocation = mode == AttendanceMode.CHECK_IN && !perm
        val decision = ClockInLogic.locationDecision(mode, perm, fix != null)
        if (decision is ClockInLogic.LocationDecision.Blocked) {
            _state.update {
                it.copy(panelMessage = decision.message, panelError = true, attemptNo = it.attemptNo + 1, settingsHint = SettingsHint.LOCATION)
            }
            return
        }
        val useFix: GpsFix?
        if (sendNoLocation) {
            // Soft mode: allowed with a flag. Restrict mode: the server refuses and we show LOCATION_NEEDED below.
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
                        result = ClockInLogic.resultFrom(mode, row),
                        toast = if (mode == AttendanceMode.CHECK_IN) "You are checked in." else "You are clocked out.",
                    )
                }
                refresh()
                return@launch
            }
            val code = (err as? ApiException)?.code.orEmpty()
            val refusedNoLoc = ClockInLogic.checkInRefusedWithoutLocation(code, sendNoLocation)
            val msg = if (refusedNoLoc) ClockInLogic.LOCATION_NEEDED else ErrorCopy.forThrowable(err)
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
                        settingsHint = if (refusedNoLoc) SettingsHint.LOCATION else null,
                        failedFixElapsedMs = if (AttendanceRules.needsFreshReading(code) && useFix != null) useFix.elapsedMs else it.failedFixElapsedMs,
                    )
                }
            }
        }
    }
}
