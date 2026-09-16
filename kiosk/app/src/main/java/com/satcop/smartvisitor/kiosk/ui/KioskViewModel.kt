package com.satcop.smartvisitor.kiosk.ui

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.fixture.HybridKioskRepository
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import com.satcop.smartvisitor.kiosk.data.repository.KioskRepository
import com.satcop.smartvisitor.kiosk.ui.components.ToastKind
import com.satcop.smartvisitor.kiosk.ui.media.PlaceholderBitmap
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class KioskMode { VISITOR, PICKUP }

data class KioskUiState(
    val mode: KioskMode = KioskMode.VISITOR,
    val step: Int = 1,
    val schoolName: String = DemoFixtures.school.name,
    val timezone: String = DemoFixtures.SCHOOL_TZ,
    val clockLabel: String = "",
    val watermark: String = DemoFixtures.WATERMARK,
    val dataSource: DataSource = DataSource.FIXTURES,
    val meDisplayName: String = "",
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
) {
    val selectedGate: Gate?
        get() = gates.firstOrNull { it.id == draft.gateId } ?: gates.firstOrNull()
}

class KioskViewModel(
    private val repository: KioskRepository = HybridKioskRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(KioskUiState())
    val state: StateFlow<KioskUiState> = _state.asStateFlow()

    private val clockFmt = DateTimeFormatter.ofPattern(
        "EEE, d MMM, hh:mm:ss a",
        Locale.ENGLISH,
    )

    init {
        viewModelScope.launch { load() }
        viewModelScope.launch { tickClock() }
    }

    private suspend fun load() {
        repository.warmup()
        val me = repository.me()
        val school = repository.school()
        val staff = repository.listStaff(active = true)
        val gates = repository.listGates()
        val inside = repository.listInside()
        val story = repository.demoStory()
        val allowedGateIds = me.gateIds?.toSet()
        val visibleGates = gates.data.filter { allowedGateIds == null || it.id in allowedGateIds }
        val defaultGateId = story.gateId.takeIf { id -> visibleGates.any { it.id == id } }
            ?: visibleGates.firstOrNull()?.id
            ?: DemoFixtures.GATE_MAIN_ID
        val watermark = me.meta?.watermark
            ?: staff.meta?.watermark
            ?: DemoFixtures.WATERMARK
        val source = repository.dataSource
        _state.update {
            it.copy(
                schoolName = school.name,
                timezone = school.timezone,
                watermark = watermark,
                dataSource = source,
                meDisplayName = me.displayName,
                gates = visibleGates,
                hosts = staff.data.filter { row -> row.roleTitle != "Guard" },
                recent = inside.data.take(4),
                story = story,
                draft = it.draft.copy(
                    hostId = story.hostId,
                    gateId = defaultGateId,
                ),
                loaded = true,
                toast = if (source == DataSource.LIVE) {
                    "Live mock connected"
                } else {
                    "FIXTURES · tunnel unreachable"
                },
                toastKind = if (source == DataSource.LIVE) ToastKind.SUCCESS else ToastKind.WARNING,
            )
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

    fun openPickupMode() {
        _state.update {
            it.copy(
                mode = KioskMode.PICKUP,
                toast = "Pickup mode · Day-1 wired UI is pickup-gate web :${com.satcop.smartvisitor.kiosk.data.fixture.PickupStory.WEB_PORT}",
                toastKind = ToastKind.INFO,
            )
        }
    }

    fun openVisitorMode() {
        _state.update { it.copy(mode = KioskMode.VISITOR, step = 1, toast = null) }
    }

    fun showPickupWebHint() {
        _state.update {
            it.copy(
                toast = com.satcop.smartvisitor.kiosk.data.fixture.PickupStory.WEB_HINT,
                toastKind = ToastKind.INFO,
            )
        }
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

    fun updateMobile(value: String) = patchDraft { copy(mobile = value) }

    fun updatePurpose(value: String) = patchDraft { copy(purpose = value) }

    fun updateVehicle(value: String) = patchDraft { copy(vehicleNumber = value) }

    fun updateAccompanying(value: String) = patchDraft { copy(accompanyingCount = value) }

    fun updateNotes(value: String) = patchDraft { copy(notes = value) }

    fun selectIdType(apiValue: String) = patchDraft { copy(idType = apiValue) }

    fun updateIdNumber(value: String) = patchDraft { copy(idNumber = value) }

    fun setLivePhoto(bitmap: Bitmap?) {
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
            )
            val visit = repository.createVisit(body)
            val source = repository.dataSource
            _state.update {
                it.copy(
                    submitting = false,
                    createdVisit = visit,
                    blacklistHit = hit,
                    blocked = false,
                    dataSource = source,
                    draft = draft.copy(livePhotoKey = photo.key, idImageKey = idKey, signatureKey = sigKey),
                    step = 4,
                    toast = when {
                        hit?.severity == "Alert" -> "Alert hit · visit pending · host notified"
                        source == DataSource.FIXTURES -> "FIXTURES · host notified · Demo approve to issue QR"
                        else -> "Host notified · waiting for approval"
                    },
                    toastKind = if (hit?.severity == "Alert") ToastKind.WARNING else ToastKind.SUCCESS,
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
        val story = _state.value.story
        _state.update {
            it.copy(
                step = 1,
                draft = RegistrationDraft(hostId = story.hostId, gateId = story.gateId),
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
