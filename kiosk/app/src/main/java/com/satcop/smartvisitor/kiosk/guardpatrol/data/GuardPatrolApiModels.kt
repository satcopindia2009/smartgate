package com.satcop.smartvisitor.kiosk.guardpatrol.data

import com.satcop.smartvisitor.kiosk.data.model.Meta
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class PatrolCheckpointDto(
    val id: String,
    val schoolId: String,
    val name: String,
    val gateOrZone: String,
    val tagType: String,
    val active: Boolean = true,
    val lat: Double? = null,
    val lng: Double? = null,
    val meta: Meta? = null,
)

@Serializable
data class PatrolTemplateDto(
    val id: String,
    val schoolId: String,
    val name: String,
    val checkpointIds: List<String> = emptyList(),
    val ordered: Boolean = false,
    val expectedDurationMin: Int = 0,
    val active: Boolean = true,
    val meta: Meta? = null,
)

@Serializable
data class PatrolScanDto(
    val checkpointId: String,
    val scannedAt: String? = null,
    val deviceId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val offCampusSuspect: Boolean = false,
    val outOfOrder: Boolean = false,
)

@Serializable
data class PatrolRoundDto(
    val id: String,
    val schoolId: String,
    val templateId: String,
    val guardId: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val status: String = "in_progress",
    val scans: List<PatrolScanDto> = emptyList(),
    val meta: Meta? = null,
    val lastScan: PatrolScanDto? = null,
    val deduped: Boolean? = null,
    val message: String? = null,
    val outOfOrder: Boolean? = null,
)

@Serializable
data class PatrolListResponse(
    val data: List<PatrolCheckpointDto> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class PatrolTemplateListResponse(
    val data: List<PatrolTemplateDto> = emptyList(),
    val meta: Meta? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class StartRoundRequest(
    val templateId: String,
    val guardId: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val assignmentId: String? = null,
)

@Serializable
data class ScanRequest(
    val checkpointId: String,
    val deviceId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val offCampusSuspect: Boolean = false,
)

@Serializable
data class PatrolAssignmentDto(
    val id: String,
    val schoolId: String,
    val templateId: String,
    val guardId: String,
    val dutyDate: String,
    val shiftStart: String? = null,
    val shiftEnd: String? = null,
    val status: String = "assigned",
    val roundId: String? = null,
    val assignedBy: String? = null,
    val assignedAt: String? = null,
    val notes: String? = null,
    val meta: Meta? = null,
)

@Serializable
data class PatrolAssignmentListResponse(
    val data: List<PatrolAssignmentDto> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class CreatePatrolAssignmentRequest(
    val templateId: String,
    val guardId: String,
    val dutyDate: String,
    val shiftStart: String? = null,
    val shiftEnd: String? = null,
    val notes: String? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreatePatrolIncidentRequest(
    val schoolId: String,
    val guardId: String,
    val type: String,
    val notes: String,
    val photoKey: String,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val roundId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val assignmentId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val checkpointId: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lat: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lng: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val capturedAt: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val gpsMissing: Boolean? = null,
)

@Serializable
data class PatrolIncidentDto(
    val id: String,
    val schoolId: String,
    val guardId: String,
    val type: String,
    val notes: String = "",
    val photoKey: String? = null,
    val roundId: String? = null,
    val assignmentId: String? = null,
    val checkpointId: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val createdAt: String? = null,
    val meta: Meta? = null,
)
