package com.satcop.smartvisitor.kiosk.guardpatrol.data

import com.satcop.smartvisitor.kiosk.data.model.Meta
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

@Serializable
data class StartRoundRequest(
    val templateId: String,
    val guardId: String,
)

@Serializable
data class ScanRequest(
    val checkpointId: String,
    val deviceId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val offCampusSuspect: Boolean = false,
)
