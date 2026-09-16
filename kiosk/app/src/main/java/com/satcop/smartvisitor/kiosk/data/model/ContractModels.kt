package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable

/**
 * Field names locked to mvp-api-contract + FastAPI Day-1 stub.
 * Do not rename for UI convenience — map in the presentation layer instead.
 */

@Serializable
data class Meta(
    val watermark: String? = null,
)

@Serializable
data class SchoolConfig(
    val hostNotifyChannels: List<String> = emptyList(),
)

@Serializable
data class School(
    val id: String,
    val name: String,
    val timezone: String,
    val overdueHoursDefault: Int,
    val config: SchoolConfig? = null,
)

@Serializable
data class MeResponse(
    val id: String,
    val schoolId: String,
    val role: String,
    val staffId: String? = null,
    val gateIds: List<String>? = null,
    val displayName: String,
    val phone: String? = null,
    val email: String? = null,
    val meta: Meta? = null,
)

@Serializable
data class Gate(
    val id: String,
    val schoolId: String,
    val name: String,
    val active: Boolean,
)

@Serializable
data class GateListResponse(
    val data: List<Gate>,
    val meta: Meta? = null,
)

@Serializable
data class Staff(
    val id: String,
    val schoolId: String,
    val name: String,
    val roleTitle: String,
    val mobile: String? = null,
    val userId: String? = null,
    val active: Boolean,
)

@Serializable
data class StaffListResponse(
    val data: List<Staff>,
    val meta: Meta? = null,
)

/** Subset of Visit used for the kiosk strip — contract field names. */
@Serializable
data class InsideVisit(
    val id: String,
    val visitorName: String,
    val timeIn: String? = null,
    val gateId: String,
    val hostId: String,
    val visitorType: String,
    val status: String,
)

@Serializable
data class InsideListResponse(
    val data: List<InsideVisit>,
    val meta: Meta? = null,
)

@Serializable
data class DemoStory(
    val visitorName: String,
    val mobile: String,
    val visitorType: String,
    val purpose: String,
    val hostId: String,
    val gateId: String,
    val hostName: String,
    val hostRoleTitle: String,
    val gateName: String,
)

enum class VisitorType(val apiValue: String, val emoji: String, val help: String) {
    Parent("Parent", "👨‍👩‍👧", "Guardian or pickup"),
    Vendor("Vendor", "📦", "Delivery / contractor"),
    Guest("Guest", "🤝", "Invited visitor"),
    Official("Official", "🏛️", "Govt / board visit"),
    Alumni("Alumni", "🎓", "Former student"),
    ;

    companion object {
        fun fromApi(value: String): VisitorType =
            entries.firstOrNull { it.apiValue == value } ?: Parent
    }
}
