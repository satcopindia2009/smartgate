package com.satcop.smartvisitor.kiosk.guardpatrol.ui

import androidx.lifecycle.ViewModel
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolEngine
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolFixtures
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundInstance
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundStatus
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundTemplate
import com.satcop.smartvisitor.kiosk.guardpatrol.data.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class GuardPatrolScreen {
    START,
    ACTIVE,
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
    val templates: List<RoundTemplate> = GuardPatrolFixtures.activeTemplates(),
    val selectedTemplateId: String? = null,
    val round: RoundInstance? = null,
    val toast: ToastEvent? = null,
    val showScanPicker: Boolean = false,
    val scanPickerMode: String = "QR",
    val simulateOffCampus: Boolean = false,
    val showOffCampusBanner: Boolean = false,
)

class GuardPatrolViewModel : ViewModel() {
    private val _state = MutableStateFlow(GuardPatrolUiState())
    val state: StateFlow<GuardPatrolUiState> = _state.asStateFlow()

    fun selectTemplate(id: String) {
        _state.update { it.copy(selectedTemplateId = id) }
    }

    fun startRound() {
        val id = _state.value.selectedTemplateId ?: return
        val tpl = GuardPatrolFixtures.template(id) ?: return
        val round = GuardPatrolEngine.startRound(tpl, guardId = GuardPatrolFixtures.DEFAULT_GUARD_ID)
        _state.update {
            it.copy(
                round = round,
                screen = GuardPatrolScreen.ACTIVE,
                showOffCampusBanner = false,
                simulateOffCampus = false,
                toast = ToastEvent(
                    id = System.currentTimeMillis(),
                    message = "Round started · ${tpl.name}",
                    kind = ToastKind.SUCCESS,
                ),
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
        val tpl = GuardPatrolFixtures.template(round.templateId) ?: return
        val offCampus = current.simulateOffCampus
        val (updated, result) = GuardPatrolEngine.simulateScan(
            round = round,
            template = tpl,
            checkpointId = checkpointId,
            offCampusSuspect = offCampus,
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
                val cpName = GuardPatrolFixtures.checkpoint(checkpointId)?.name ?: checkpointId
                val msg = if (result.outOfOrder) {
                    result.message
                } else {
                    "$mode OK · $cpName"
                }
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
        val tpl = GuardPatrolFixtures.template(round.templateId) ?: return
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
        _state.update {
            GuardPatrolUiState(
                templates = GuardPatrolFixtures.activeTemplates(),
            )
        }
    }

    fun clearToast() {
        _state.update { it.copy(toast = null) }
    }
}
