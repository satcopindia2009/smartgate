package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable

@Serializable
data class VisitListResponse(
    val data: List<VisitOut> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class RejectBody(
    val reason: String,
)

@Serializable
data class ApproveBody(
    val reason: String? = null,
)

@Serializable
data class HostNotification(
    val id: String,
    val schoolId: String? = null,
    val hostId: String? = null,
    val visitId: String? = null,
    val event: String? = null,
    val channel: String? = null,
    val title: String? = null,
    val body: String? = null,
    val status: String? = null,
    val createdAt: String? = null,
    val readAt: String? = null,
)

@Serializable
data class NotificationListResponse(
    val data: List<HostNotification> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class CampusHoursRow(
    val schoolId: String? = null,
    val timezone: String? = null,
    val weekday: String,
    val openTime: String? = null,
    val closeTime: String? = null,
    val closed: Boolean = false,
    val overnight: Boolean = false,
)

@Serializable
data class HoursListResponse(
    val data: List<CampusHoursRow> = emptyList(),
    val meta: Meta? = null,
)

object RejectReasons {
    val chips: List<String> = listOf(
        "Not expecting visitor",
        "Wrong host",
        "In a meeting",
        "Ask to reschedule",
        "Host unavailable",
    )
}
