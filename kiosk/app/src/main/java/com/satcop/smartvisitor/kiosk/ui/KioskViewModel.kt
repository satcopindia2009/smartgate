package com.satcop.smartvisitor.kiosk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.fixture.FixtureDirectoryRepository
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import com.satcop.smartvisitor.kiosk.data.repository.DirectoryRepository
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
    val meDisplayName: String = "",
    val gates: List<Gate> = emptyList(),
    val hosts: List<Staff> = emptyList(),
    val recent: List<InsideVisit> = emptyList(),
    val draft: RegistrationDraft = RegistrationDraft(),
    val fieldErrors: Map<String, String> = emptyMap(),
    val toast: String? = null,
    val story: DemoStory = DemoFixtures.demoStory,
    val loaded: Boolean = false,
) {
    val selectedGate: Gate?
        get() = gates.firstOrNull { it.id == draft.gateId } ?: gates.firstOrNull()
}

class KioskViewModel(
    private val directory: DirectoryRepository = FixtureDirectoryRepository(),
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
        val me = directory.me()
        val school = directory.school()
        val staff = directory.listStaff(active = true)
        val gates = directory.listGates()
        val inside = directory.listInside()
        val story = directory.demoStory()
        val allowedGateIds = me.gateIds?.toSet()
        val visibleGates = gates.data.filter { allowedGateIds == null || it.id in allowedGateIds }
        val defaultGateId = story.gateId.takeIf { id -> visibleGates.any { it.id == id } }
            ?: visibleGates.firstOrNull()?.id
            ?: DemoFixtures.GATE_MAIN_ID
        val watermark = me.meta?.watermark
            ?: staff.meta?.watermark
            ?: DemoFixtures.WATERMARK
        _state.update {
            it.copy(
                schoolName = school.name,
                timezone = school.timezone,
                watermark = watermark,
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

    private fun patchDraft(block: RegistrationDraft.() -> RegistrationDraft) {
        _state.update { it.copy(draft = it.draft.block(), fieldErrors = emptyMap()) }
    }

    fun prefillSample() {
        val story = _state.value.story
        _state.update {
            it.copy(
                draft = RegistrationDraft.fromStory(story),
                fieldErrors = emptyMap(),
                toast = "Sample parent visit loaded (fixture)",
                step = 1,
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
                toast = "Details valid · photo & ID are Wave 2",
            )
        }
    }

    fun back() {
        _state.update {
            val prev = (it.step - 1).coerceAtLeast(1)
            it.copy(step = prev, toast = null, fieldErrors = emptyMap())
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    companion object {
        fun factory(directory: DirectoryRepository = FixtureDirectoryRepository()): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return KioskViewModel(directory) as T
                }
            }
    }
}
