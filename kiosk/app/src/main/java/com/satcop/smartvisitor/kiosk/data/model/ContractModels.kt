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
    val overdueHoursDefault: Int = 2,
    val config: SchoolConfig? = null,
    /** School flag — staff face login (AC-FL). */
    val faceLoginEnabled: Boolean? = null,
    /** off | soft | restrict — default restrict (Viren HARD). Backend enforces 403. */
    val geoFenceMode: String? = null,
    /** "HH:mm" school time at which the guard business day rolls over (default 00:00). */
    val guardSessionCutoff: String? = null,
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
    // OTP scope: mobileVerified / verifiedAt on /auth/me (additive; absent until Backend ships it).
    val mobileVerified: Boolean? = null,
    val verifiedAt: String? = null,
    // Clock-in addendum: on /auth/me, login top level and login user. profilePhotoUrl is signed (?t=) or null.
    val guardName: String? = null,
    val schoolName: String? = null,
    val profilePhotoUrl: String? = null,
    val otpNoticeVersion: String? = null,
    // Guard shift (Backend 2026-10-01, additive, null when no shift is assigned).
    val shiftName: String? = null,
    val shiftStartTime: String? = null,
    /** e.g. "Morning · starts 06:30 am" - shown verbatim. */
    val shiftStartDisplay: String? = null,
    val shiftEndTime: String? = null,
    val shiftEndDisplay: String? = null,
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
    val visitorName: String? = null,
    val timeIn: String? = null,
    val gateId: String? = null,
    val hostId: String? = null,
    val visitorType: String? = null,
    val status: String = "inside",
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
