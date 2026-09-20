package com.satcop.smartvisitor.kiosk.guardpatrol.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satcop.smartvisitor.kiosk.data.geo.CaptureGeo
import com.satcop.smartvisitor.kiosk.data.geo.GeoFenceCodes
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.guardpatrol.data.CreatePatrolIncidentRequest
import com.satcop.smartvisitor.kiosk.guardpatrol.data.IncidentType
import com.satcop.smartvisitor.kiosk.guardpatrol.data.AssignmentStatus
import com.satcop.smartvisitor.kiosk.guardpatrol.data.Checkpoint
import com.satcop.smartvisitor.kiosk.guardpatrol.data.PatrolAssignment
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolEngine
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolFixtures
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolMapper
import com.satcop.smartvisitor.kiosk.guardpatrol.data.LiveGuardPatrolApi
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundInstance
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundStatus
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundTemplate
import com.satcop.smartvisitor.kiosk.guardpatrol.data.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class GuardPatrolScreen {
    START,
    ACTIVE,
    INCIDENT,
    RESULT,
}

data class ToastEvent(
    val id: Long,
    val message: String,
    val kind: ToastKind,
)

enum class ToastKind { INFO, SUCCESS, WARNING, ERROR }

data class GuardPatrolUiState(
    val screen: GuardPatrolScreen = GuardPatrolScreen.START,
    val templates: List<RoundTemplate> = emptyList(),
    val checkpoints: Map<String, Checkpoint> = emptyMap(),
    val schoolName: String = "Demo Public School — Campus A",
    val schoolId: String = LiveGuardPatrolApi.DEMO_SCHOOL_ID,
    val guardLabel: String = "Guard G1",
    val guardId: String = GuardPatrolFixtures.DEFAULT_GUARD_ID,
    val selectedTemplateId: String? = null,
    /** Assigned-today inbox (fixture-first until GET /patrol-assignments is 200). */
    val assignments: List<PatrolAssignment> = emptyList(),
    val assignmentsFromLive: Boolean = false,
    /** School flag AC-AP7 — hide/disable self-start when true (default false). */
    val requireAssignment: Boolean = GuardPatrolFixtures.REQUIRE_ASSIGNMENT_DEFAULT,
    val round: RoundInstance? = null,
    val toast: ToastEvent? = null,
    val showScanPicker: Boolean = false,
    val scanPickerMode: String = "QR",
    val simulateOffCampus: Boolean = false,
    val showOffCampusBanner: Boolean = false,
    /** Default LIVE against living /v1; toggle for offline fixture engine. */
    val useLive: Boolean = true,
    val liveReady: Boolean = false,
    val busy: Boolean = false,
    val statusLine: String = "Connecting…",
    // Wave 2 Incident (AC-PI1/PI2/PI5) — additive; never blocks Start/Scan/End.
    val incidentType: IncidentType = IncidentType.SAFETY,
    val incidentNotes: String = "",
    val incidentCheckpointId: String? = null,
    val incidentPhotoJpeg: ByteArray? = null,
    val incidentBusy: Boolean = false,
    val incidentReturnScreen: GuardPatrolScreen = GuardPatrolScreen.ACTIVE,
)

class GuardPatrolViewModel(
    private val api: LiveGuardPatrolApi = LiveGuardPatrolApi(),
) : ViewModel() {
    private val _state = MutableStateFlow(GuardPatrolUiState())
    val state: StateFlow<GuardPatrolUiState> = _state.asStateFlow()

    init {
        bootstrapLive()
    }

    fun selectTemplate(id: String) {
        _state.update { it.copy(selectedTemplateId = id) }
    }

    fun setUseLive(value: Boolean) {
        // AC-GP2 live-only — fixture Assigned today removed. Always reconnect Living.
        _state.update { it.copy(useLive = true, busy = true, statusLine = "Connecting…") }
        bootstrapLive()
    }

    fun refreshAssignments() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, statusLine = "Refreshing assignments…") }
            val guardId = _state.value.guardId.ifBlank {
                api.signedInUser?.staffId ?: GuardPatrolFixtures.DEFAULT_GUARD_ID
            }
            val dutyDate = GuardPatrolFixtures.todayDutyDateIst()
            val (assignments, fromLive) = loadAssignments(dutyDate, guardId)
            _state.update {
                it.copy(
                    busy = false,
                    assignments = assignments,
                    assignmentsFromLive = fromLive,
                    statusLine = "LIVE · my-schedules ${assignments.size} · $dutyDate",
                    toast = ToastEvent(
                        System.currentTimeMillis(),
                        if (assignments.isEmpty()) "No assignments for today"
                        else "Updated · ${assignments.size} assignment(s)",
                        if (assignments.isEmpty()) ToastKind.WARNING else ToastKind.SUCCESS,
                    ),
                )
            }
        }
    }

    fun startRound() {
        if (_state.value.requireAssignment) {
            _state.update {
                it.copy(
                    toast = ToastEvent(
                        System.currentTimeMillis(),
                        "Assignment required — start from Assigned today",
                        ToastKind.WARNING,
                    ),
                )
            }
            return
        }
        val id = _state.value.selectedTemplateId ?: return
        beginRound(templateId = id, assignmentId = null)
    }

    /** Start from Assigned-today row — links assignmentId in client state / POST /rounds. */
    fun startFromAssignment(assignmentId: String) {
        val asg = _state.value.assignments.find { it.id == assignmentId }
            ?: GuardPatrolFixtures.assignment(assignmentId)
            ?: return
        if (asg.status != AssignmentStatus.ASSIGNED && asg.status != AssignmentStatus.STARTED) {
            _state.update {
                it.copy(
                    toast = ToastEvent(
                        System.currentTimeMillis(),
                        "Assignment ${asg.status.display()} — cannot start",
                        ToastKind.WARNING,
                    ),
                )
            }
            return
        }
        // Resume in-progress Living round when Admin schedule already started.
        val existingRoundId = asg.roundId
        if (asg.status == AssignmentStatus.STARTED && !existingRoundId.isNullOrBlank() &&
            _state.value.useLive && _state.value.liveReady
        ) {
            viewModelScope.launch {
                _state.update { it.copy(busy = true) }
                runCatching {
                    withContext(Dispatchers.IO) { api.getRound(existingRoundId) }
                }.onSuccess { dto ->
                    val round = GuardPatrolMapper.toDomain(dto, assignmentId = asg.id)
                    _state.update {
                        it.copy(
                            busy = false,
                            round = round,
                            screen = GuardPatrolScreen.ACTIVE,
                            toast = ToastEvent(
                                System.currentTimeMillis(),
                                "Resumed assigned round",
                                ToastKind.SUCCESS,
                            ),
                        )
                    }
                }.onFailure {
                    beginRound(templateId = asg.templateId, assignmentId = asg.id)
                }
            }
            return
        }
        beginRound(templateId = asg.templateId, assignmentId = asg.id)
    }

    private fun beginRound(templateId: String, assignmentId: String?) {
        val tpl = templateOf(templateId) ?: return
        if (_state.value.useLive && _state.value.liveReady) {
            viewModelScope.launch {
                _state.update { it.copy(busy = true) }
                runCatching {
                    withContext(Dispatchers.IO) {
                        val guardId = api.signedInUser?.staffId
                            ?: GuardPatrolFixtures.DEFAULT_GUARD_ID
                        api.startRound(
                            templateId = templateId,
                            guardId = guardId,
                            assignmentId = assignmentId,
                        )
                    }
                }.onSuccess { dto ->
                    val round = GuardPatrolMapper.toDomain(dto, assignmentId = assignmentId)
                    markAssignmentStarted(assignmentId, round.id)
                    val label = if (assignmentId != null) {
                        "Started from assignment · ${tpl.name}"
                    } else {
                        "Round started · ${tpl.name}"
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            round = round,
                            screen = GuardPatrolScreen.ACTIVE,
                            showOffCampusBanner = false,
                            simulateOffCampus = false,
                            toast = ToastEvent(
                                id = System.currentTimeMillis(),
                                message = label,
                                kind = ToastKind.SUCCESS,
                            ),
                        )
                    }
                }.onFailure { err ->
                    // AC-AO1: Living rejects missing assignmentId (400 ASSIGNMENT_REQUIRED).
                    // Never retry without assignmentId.
                    val msg = when {
                        err is ApiException && (err.code == "ASSIGNMENT_REQUIRED" || "ASSIGNMENT_REQUIRED" in (err.message ?: "")) ->
                            "Assignment required — start from Assigned today"
                        assignmentId == null ->
                            "Assignment required — pick an Admin schedule row"
                        else -> err.message ?: "Start failed"
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            toast = ToastEvent(
                                System.currentTimeMillis(),
                                msg,
                                ToastKind.ERROR,
                            ),
                        )
                    }
                }
            }
            return
        }
        // Live-only perform — never start a fixture round.
        _state.update {
            it.copy(
                toast = ToastEvent(
                    System.currentTimeMillis(),
                    if (assignmentId == null) {
                        "Assignment required — pick an Admin schedule"
                    } else {
                        "Living not ready — tap Refresh"
                    },
                    ToastKind.ERROR,
                ),
            )
        }
    }

    private fun markAssignmentStarted(assignmentId: String?, roundId: String) {
        if (assignmentId == null) return
        _state.update { st ->
            st.copy(
                assignments = st.assignments.map { a ->
                    if (a.id == assignmentId) {
                        a.copy(status = AssignmentStatus.STARTED, roundId = roundId)
                    } else {
                        a
                    }
                },
            )
        }
    }

    fun openScanPicker(mode: String) {
        _state.update { it.copy(showScanPicker = true, scanPickerMode = mode) }
    }

    fun dismissScanPicker() {
        _state.update { it.copy(showScanPicker = false) }
    }

    fun setSimulateOffCampus(value: Boolean) {
        _state.update { it.copy(simulateOffCampus = value) }
    }

    fun scanCheckpoint(checkpointId: String) {
        val current = _state.value
        val round = current.round ?: return
        val tpl = templateOf(round.templateId) ?: return
        val offCampus = current.simulateOffCampus
        if (current.useLive && current.liveReady) {
            viewModelScope.launch {
                _state.update { it.copy(busy = true, showScanPicker = false) }
                // Client-side pre-check (dedupe / inactive) — warn-never-block for OOO.
                val (_, local) = GuardPatrolEngine.simulateScan(
                    round = round,
                    template = tpl,
                    checkpointId = checkpointId,
                    offCampusSuspect = offCampus,
                    checkpointLookup = { current.checkpoints[it] ?: GuardPatrolFixtures.checkpoint(it) },
                )
                if (local is ScanResult.Rejected && local.reason != "dedupe") {
                    // Still allow server to decide unknowns; only soft-block inactive locally when known.
                    if (local.reason == "inactive") {
                        _state.update {
                            it.copy(
                                busy = false,
                                toast = ToastEvent(System.currentTimeMillis(), local.message, ToastKind.ERROR),
                            )
                        }
                        return@launch
                    }
                }
                val cp = current.checkpoints[checkpointId] ?: GuardPatrolFixtures.checkpoint(checkpointId)
                val payload = cp?.tagPayload?.takeIf { it.isNotBlank() } ?: "SGCP:$checkpointId"
                runCatching {
                    withContext(Dispatchers.IO) {
                        val stamp = CaptureGeo.read()
                        api.scan(
                            roundId = round.id,
                            checkpointId = checkpointId,
                            tagPayload = payload,
                            deviceId = LiveGuardPatrolApi.DEMO_DEVICE_ID,
                            lat = stamp.lat,
                            lng = stamp.lng,
                            offCampusSuspect = offCampus,
                            gpsMissing = stamp.gpsMissing,
                        )
                    }
                }.onSuccess { dto ->
                    if (dto.deduped == true) {
                        _state.update {
                            it.copy(
                                busy = false,
                                toast = ToastEvent(
                                    System.currentTimeMillis(),
                                    dto.message ?: "Already scanned — ignored (2-min dedupe)",
                                    ToastKind.INFO,
                                ),
                            )
                        }
                        return@onSuccess
                    }
                    val updated = GuardPatrolMapper.toDomain(dto)
                    val last = dto.lastScan ?: dto.scans.lastOrNull()
                    val outOfOrder = last?.outOfOrder == true || dto.outOfOrder == true
                    val cpName = current.checkpoints[checkpointId]?.name
                        ?: GuardPatrolFixtures.checkpoint(checkpointId)?.name
                        ?: checkpointId
                    val mode = current.scanPickerMode
                    val msg = if (outOfOrder) {
                        "Out of sequence — scan recorded"
                    } else {
                        "$mode OK · $cpName"
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            round = updated,
                            simulateOffCampus = false,
                            showOffCampusBanner = it.showOffCampusBanner || offCampus ||
                                (last?.offCampusSuspect == true),
                            toast = ToastEvent(
                                System.currentTimeMillis(),
                                msg,
                                if (outOfOrder) ToastKind.WARNING else ToastKind.SUCCESS,
                            ),
                        )
                    }
                }.onFailure { err ->
                    val msg = (err as? ApiException)?.message ?: err.message ?: "Scan failed"
                    _state.update {
                        it.copy(
                            busy = false,
                            toast = ToastEvent(System.currentTimeMillis(), msg, ToastKind.ERROR),
                        )
                    }
                }
            }
            return
        }

        val (updated, result) = GuardPatrolEngine.simulateScan(
            round = round,
            template = tpl,
            checkpointId = checkpointId,
            offCampusSuspect = offCampus,
            checkpointLookup = { current.checkpoints[it] ?: GuardPatrolFixtures.checkpoint(it) },
        )
        when (result) {
            is ScanResult.Rejected -> {
                val kind = if (result.reason == "dedupe") ToastKind.INFO else ToastKind.ERROR
                _state.update {
                    it.copy(
                        showScanPicker = false,
                        toast = ToastEvent(System.currentTimeMillis(), result.message, kind),
                    )
                }
            }
            is ScanResult.Accepted -> {
                val kind = if (result.outOfOrder) ToastKind.WARNING else ToastKind.SUCCESS
                val mode = current.scanPickerMode
                val cpName = current.checkpoints[checkpointId]?.name
                    ?: GuardPatrolFixtures.checkpoint(checkpointId)?.name
                    ?: checkpointId
                val msg = if (result.outOfOrder) result.message else "$mode OK · $cpName"
                _state.update {
                    it.copy(
                        round = updated,
                        showScanPicker = false,
                        simulateOffCampus = false,
                        showOffCampusBanner = it.showOffCampusBanner || offCampus,
                        toast = ToastEvent(System.currentTimeMillis(), msg, kind),
                    )
                }
            }
        }
    }

    fun endRound() {
        val round = _state.value.round ?: return
        val tpl = templateOf(round.templateId) ?: return
        if (_state.value.useLive && _state.value.liveReady) {
            viewModelScope.launch {
                _state.update { it.copy(busy = true) }
                runCatching {
                    withContext(Dispatchers.IO) { api.endRound(round.id) }
                }.onSuccess { dto ->
                    val ended = GuardPatrolMapper.toDomain(dto)
                    val kind = when (ended.status) {
                        RoundStatus.COMPLETED -> ToastKind.SUCCESS
                        RoundStatus.MISSED -> ToastKind.ERROR
                        RoundStatus.PARTIAL -> ToastKind.WARNING
                        RoundStatus.IN_PROGRESS -> ToastKind.INFO
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            round = ended,
                            screen = GuardPatrolScreen.RESULT,
                            toast = ToastEvent(
                                System.currentTimeMillis(),
                                "Round ${ended.status.display()}",
                                kind,
                            ),
                        )
                    }
                }.onFailure { err ->
                    // Client-side End→Completed|Partial still apply if live end fails.
                    val ended = GuardPatrolEngine.endRound(round, tpl)
                    val kind = when (ended.status) {
                        RoundStatus.COMPLETED -> ToastKind.SUCCESS
                        RoundStatus.MISSED -> ToastKind.ERROR
                        RoundStatus.PARTIAL -> ToastKind.WARNING
                        RoundStatus.IN_PROGRESS -> ToastKind.INFO
                    }
                    _state.update {
                        it.copy(
                            busy = false,
                            round = ended,
                            screen = GuardPatrolScreen.RESULT,
                            toast = ToastEvent(
                                System.currentTimeMillis(),
                                "Offline end · ${ended.status.display()} (${err.message ?: "live end failed"})",
                                kind,
                            ),
                        )
                    }
                }
            }
            return
        }
        val ended = GuardPatrolEngine.endRound(round, tpl)
        val kind = when (ended.status) {
            RoundStatus.COMPLETED -> ToastKind.SUCCESS
            RoundStatus.MISSED -> ToastKind.ERROR
            RoundStatus.PARTIAL -> ToastKind.WARNING
            RoundStatus.IN_PROGRESS -> ToastKind.INFO
        }
        _state.update {
            it.copy(
                round = ended,
                screen = GuardPatrolScreen.RESULT,
                toast = ToastEvent(
                    id = System.currentTimeMillis(),
                    message = "Round ${ended.status.display()}",
                    kind = kind,
                ),
            )
        }
    }

    fun backToStart() {
        val keepLive = _state.value.useLive
        val templates = _state.value.templates
        val checkpoints = _state.value.checkpoints
        val schoolName = _state.value.schoolName
        val schoolId = _state.value.schoolId
        val guardLabel = _state.value.guardLabel
        val guardId = _state.value.guardId
        val liveReady = _state.value.liveReady
        val statusLine = _state.value.statusLine
        val assignments = _state.value.assignments
        val assignmentsFromLive = _state.value.assignmentsFromLive
        val requireAssignment = _state.value.requireAssignment
        _state.update {
            GuardPatrolUiState(
                templates = if (templates.isNotEmpty()) templates else GuardPatrolFixtures.activeTemplates(),
                checkpoints = if (checkpoints.isNotEmpty()) {
                    checkpoints
                } else {
                    GuardPatrolFixtures.checkpoints.associateBy { cp -> cp.id }
                },
                schoolName = schoolName,
                schoolId = schoolId,
                guardLabel = guardLabel,
                guardId = guardId,
                useLive = keepLive,
                liveReady = liveReady,
                statusLine = statusLine,
                assignments = assignments,

                assignmentsFromLive = assignmentsFromLive,
                requireAssignment = requireAssignment,
            )
        }
    }

    
    fun openIncidentReport() {
        val returnTo = _state.value.screen
        _state.update {
            it.copy(
                screen = GuardPatrolScreen.INCIDENT,
                incidentReturnScreen = if (returnTo == GuardPatrolScreen.INCIDENT) GuardPatrolScreen.ACTIVE else returnTo,
                incidentType = IncidentType.SAFETY,
                incidentNotes = "",
                incidentCheckpointId = null,
                incidentPhotoJpeg = null,
                incidentBusy = false,
            )
        }
    }

    fun cancelIncidentReport() {
        _state.update {
            it.copy(
                screen = it.incidentReturnScreen,
                incidentBusy = false,
                incidentPhotoJpeg = null,
            )
        }
    }

    fun setIncidentType(type: IncidentType) {
        _state.update { it.copy(incidentType = type) }
    }

    fun setIncidentNotes(value: String) {
        _state.update { it.copy(incidentNotes = value) }
    }

    fun setIncidentCheckpoint(id: String?) {
        _state.update { it.copy(incidentCheckpointId = id) }
    }

    fun setIncidentPhoto(jpeg: ByteArray) {
        _state.update { it.copy(incidentPhotoJpeg = jpeg) }
    }

    fun clearIncidentPhoto() {
        _state.update { it.copy(incidentPhotoJpeg = null) }
    }

    /** AC-PI1/PI2 — LIVE only + capture stamps (AC-CAP); toast on GEO_FENCE_RESTRICTED. */
    fun submitIncident() {
        val s = _state.value
        val jpeg = s.incidentPhotoJpeg
        if (jpeg == null || jpeg.isEmpty()) {
            _state.update {
                it.copy(
                    toast = ToastEvent(System.currentTimeMillis(), "Live photo required", ToastKind.WARNING),
                )
            }
            return
        }
        val notes = s.incidentNotes.trim()
        if (notes.isBlank()) {
            _state.update {
                it.copy(toast = ToastEvent(System.currentTimeMillis(), "Add notes", ToastKind.WARNING))
            }
            return
        }
        val round = s.round
        viewModelScope.launch {
            _state.update { it.copy(incidentBusy = true) }
            try {
                val stamp = CaptureGeo.read()
                val lat = stamp.lat ?: if (s.simulateOffCampus) 0.0 else null
                val lng = stamp.lng ?: if (s.simulateOffCampus) 0.0 else null
                val dto = withContext(Dispatchers.IO) {
                    val uploaded = api.uploadMedia(
                        bytes = jpeg,
                        filename = "incident-${System.currentTimeMillis()}.jpg",
                        kind = "patrol_incident_photo",
                    )
                    api.createIncident(
                        CreatePatrolIncidentRequest(
                            schoolId = s.schoolId,
                            guardId = s.guardId,
                            type = s.incidentType.apiValue(),
                            notes = notes,
                            photoKey = uploaded.key,
                            roundId = round?.id,
                            assignmentId = round?.assignmentId,
                            checkpointId = s.incidentCheckpointId,
                            lat = lat,
                            lng = lng,
                            capturedAt = stamp.capturedAt,
                            gpsMissing = stamp.gpsMissing,
                        ),
                    )
                }
                val warn = if (stamp.gpsMissing) " · GPS unavailable" else ""
                _state.update {
                    it.copy(
                        incidentBusy = false,
                        incidentPhotoJpeg = null,
                        screen = it.incidentReturnScreen,
                        toast = ToastEvent(
                            System.currentTimeMillis(),
                            "Incident ${dto.id} · LIVE$warn · photo ~90d",
                            if (stamp.gpsMissing) ToastKind.WARNING else ToastKind.SUCCESS,
                        ),
                    )
                }
            } catch (e: ApiException) {
                val geo = GeoFenceCodes.isRestricted(e.code, e.message)
                _state.update {
                    it.copy(
                        incidentBusy = false,
                        toast = ToastEvent(
                            System.currentTimeMillis(),
                            if (geo) GeoFenceCodes.toastMessage() else (e.message),
                            ToastKind.ERROR,
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        incidentBusy = false,
                        toast = ToastEvent(
                            System.currentTimeMillis(),
                            e.message ?: "LIVE failed",
                            ToastKind.ERROR,
                        ),
                    )
                }
            }
        }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }

    fun templateOf(id: String): RoundTemplate? =
        _state.value.templates.find { it.id == id } ?: GuardPatrolFixtures.template(id)

    fun checkpointOf(id: String): Checkpoint? =
        _state.value.checkpoints[id] ?: GuardPatrolFixtures.checkpoint(id)

    private fun bootstrapLive() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, statusLine = "Signing in…") }
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    if (!api.isSignedIn) {
                        api.login(LiveGuardPatrolApi.DEMO_USERNAME, LiveGuardPatrolApi.DEMO_PASSWORD)
                    }
                    val templates = api.listTemplates().map(GuardPatrolMapper::toDomain).filter { it.active }
                    val checkpoints = api.listCheckpoints().map(GuardPatrolMapper::toDomain)
                        .filter { it.active }
                        .associateBy { it.id }
                    Triple(templates, checkpoints, api.signedInUser)
                }
            }.getOrNull()

            if (ok == null) {
                applyFixtures(status = "Living unreachable — Assigned today empty until reconnect")
                return@launch
            }
            val (templates, checkpoints, user) = ok
            val guardId = user?.staffId ?: GuardPatrolFixtures.DEFAULT_GUARD_ID
            val dutyDate = GuardPatrolFixtures.todayDutyDateIst()
            val (assignments, fromLive) = loadAssignments(dutyDate, guardId)
            _state.update {
                it.copy(
                    busy = false,
                    useLive = true,
                    liveReady = true,
                    templates = templates.ifEmpty { GuardPatrolFixtures.activeTemplates() },
                    checkpoints = checkpoints.ifEmpty {
                        GuardPatrolFixtures.checkpoints.associateBy { cp -> cp.id }
                    },
                    schoolId = user?.schoolId ?: LiveGuardPatrolApi.DEMO_SCHOOL_ID,
                    schoolName = "Demo Public School — Campus A",
                    guardId = guardId,
                    guardLabel = user?.displayName ?: "Guard G1",
                    assignments = assignments,
                    assignmentsFromLive = fromLive,
                    requireAssignment = true,
                    statusLine = "LIVE · ${user?.displayName ?: "guard"} · ${user?.schoolId ?: LiveGuardPatrolApi.DEMO_SCHOOL_ID}" +
                        " · my-schedules ${assignments.size}",
                    toast = ToastEvent(
                        System.currentTimeMillis(),
                        if (assignments.isEmpty()) {
                            "Connected · no Admin schedules today"
                        } else {
                            "Connected · ${assignments.size} schedule(s)"
                        },
                        if (assignments.isEmpty()) ToastKind.WARNING else ToastKind.SUCCESS,
                    ),
                )
            }
        }
    }

    /**
     * Try living GET /patrol-assignments; on 404/error return G1 demo fixtures for today.
     * @return Pair(list, fromLive)
     */
    private suspend fun loadAssignments(
        dutyDate: String,
        guardId: String,
    ): Pair<List<PatrolAssignment>, Boolean> {
        // Living seed filters on staffId (G1), not "me" / U-GUARD — empty live list is still live.
        val queryGuardId = guardId.ifBlank { GuardPatrolFixtures.DEFAULT_GUARD_ID }
        val live = runCatching {
            withContext(Dispatchers.IO) {
                api.listAssignments(dutyDate = dutyDate, guardId = queryGuardId)
                    .map(GuardPatrolMapper::toDomain)
            }
        }.getOrNull()
        if (live != null) {
            if (live.isNotEmpty()) return live to true
            if (queryGuardId != GuardPatrolFixtures.DEFAULT_GUARD_ID) {
                val liveG1 = runCatching {
                    withContext(Dispatchers.IO) {
                        api.listAssignments(
                            dutyDate = dutyDate,
                            guardId = GuardPatrolFixtures.DEFAULT_GUARD_ID,
                        ).map(GuardPatrolMapper::toDomain)
                    }
                }.getOrNull()
                if (liveG1 != null && liveG1.isNotEmpty()) return liveG1 to true
            }
            return live to true
        }
        // Live-only Assigned today — empty on transport failure (no fixture PA-* rows).
        return emptyList<PatrolAssignment>() to false
    }

    private fun applyFixtures(status: String) {
        // Keep templates/checkpoints for scan labels only — never fabricate Assigned today.
        val guardId = api.signedInUser?.staffId ?: GuardPatrolFixtures.DEFAULT_GUARD_ID
        _state.update {
            it.copy(
                busy = false,
                useLive = true,
                liveReady = false,
                templates = GuardPatrolFixtures.activeTemplates(),
                checkpoints = GuardPatrolFixtures.checkpoints.associateBy { cp -> cp.id },
                schoolId = GuardPatrolFixtures.SCHOOL_ID,
                schoolName = GuardPatrolFixtures.school.name,
                guardId = guardId,
                guardLabel = api.signedInUser?.displayName ?: "Guard",
                assignments = emptyList(),
                assignmentsFromLive = false,
                requireAssignment = true,
                statusLine = status,
                toast = ToastEvent(System.currentTimeMillis(), status, ToastKind.WARNING),
            )
        }
    }
}
