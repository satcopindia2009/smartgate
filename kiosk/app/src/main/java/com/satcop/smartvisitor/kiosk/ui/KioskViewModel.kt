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

data class KioskUiState(
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
    val story: DemoStory = DemoFixtures.demoStory,
    val loaded: Boolean = false,
    val submitting: Boolean = false,
    val livePhoto: Bitmap? = null,
    val idImage: Bitmap? = null,
    val signature: Bitmap? = null,
    val blacklistHit: BlacklistEntry? = null,
    val blocked: Boolean = false,
    val createdVisit: VisitOut? = null,
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
                    "Fixtures fallback · tunnel unreachable"
                },
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
            _state.update {
                it.copy(
                    submitting = false,
                    createdVisit = visit,
                    blacklistHit = hit,
                    blocked = false,
                    draft = draft.copy(livePhotoKey = photo.key, idImageKey = idKey, signatureKey = sigKey),
                    step = 4,
                    toast = if (hit?.severity == "Alert") {
                        "Alert hit · visit pending · host notified"
                    } else {
                        "Host notified · waiting for approval"
                    },
                )
            }
        } catch (e: ApiException) {
            if (e.code == "BLACKLIST_BLOCK") {
                _state.update {
                    it.copy(
                        submitting = false,
                        blocked = true,
                        toast = e.message,
                    )
                }
            } else {
                _state.update { it.copy(submitting = false, toast = e.message) }
            }
        } catch (e: Exception) {
            _state.update {
                it.copy(submitting = false, toast = e.message ?: "Submit failed")
            }
        }
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
