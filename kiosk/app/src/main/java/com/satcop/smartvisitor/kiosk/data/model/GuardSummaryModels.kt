package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable

/**
 * GET /v1/guards/me/today-summary (Backend addendum 2026-10-01). Every field is defaulted or nullable so a
 * missing key never fails the parse; a guard with no patrol data gets zeros and a null nextCheckpoint.
 */
@Serializable
data class GuardTodaySummary(
    val date: String? = null,
    val incidentsToday: IncidentsToday = IncidentsToday(),
    val patrol: PatrolToday = PatrolToday(),
    val meta: SummaryMeta? = null,
)

@Serializable
data class IncidentsToday(
    val count: Int = 0,
    val open: Int = 0,
    val items: List<IncidentSummaryItem> = emptyList(),
)

@Serializable
data class IncidentSummaryItem(
    val id: String? = null,
    val title: String? = null,
    val type: String? = null,
    val severity: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val photoUrl: String? = null,
)

@Serializable
data class PatrolToday(
    val assignedToday: Int = 0,
    val roundsCompleted: Int = 0,
    val roundsTotal: Int = 0,
    val checkpointsScanned: Int = 0,
    val checkpointsTotal: Int = 0,
    val missedCount: Int = 0,
    val progressPercent: Int = 0,
    val nextCheckpoint: NextCheckpoint? = null,
)

@Serializable
data class NextCheckpoint(
    val checkpointId: String? = null,
    val name: String? = null,
    val gateOrZone: String? = null,
    val assignmentId: String? = null,
    val roundId: String? = null,
    val routeName: String? = null,
)

@Serializable
data class SummaryMeta(
    val watermark: String? = null,
    val serverTime: String? = null,
)
