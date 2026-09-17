package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StudentOut(
    val id: String,
    val schoolId: String? = null,
    val studentId: String? = null,
    val name: String,
    @SerialName("class") val className: String? = null,
    val section: String? = null,
    val active: Boolean = true,
    val legalHold: Boolean = false,
) {
    fun classSection(): String = listOfNotNull(
        className?.takeIf { it.isNotBlank() },
        section?.takeIf { it.isNotBlank() },
    ).joinToString("-").ifBlank { "—" }
}

@Serializable
data class StudentListResponse(
    val data: List<StudentOut> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class AuthorizedPickup(
    val id: String,
    val studentId: String? = null,
    val schoolId: String? = null,
    val name: String,
    val relation: String? = null,
    val mobile: String? = null,
    val idType: String? = null,
    val idLast4: String? = null,
    val active: Boolean = true,
)

@Serializable
data class AuthorizedPickupListResponse(
    val data: List<AuthorizedPickup> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class PickupCreate(
    val studentId: String,
    val gateId: String,
    val pickupReason: String,
    val reasonOther: String? = null,
    val collectorPickupPersonId: String? = null,
    val collectorName: String? = null,
    val collectorMobile: String? = null,
    val idType: String? = null,
    val idLast4: String? = null,
    val linkVisit: Boolean = false,
)

@Serializable
data class PickupOut(
    val id: String,
    val schoolId: String? = null,
    val gateId: String? = null,
    val studentId: String? = null,
    val collectorPickupPersonId: String? = null,
    val collectorName: String? = null,
    val collectorMobile: String? = null,
    val collectorRelation: String? = null,
    val matchMethod: String? = null,
    val pickupReason: String? = null,
    val reasonOther: String? = null,
    val collectorLivePhotoRef: String? = null,
    val status: String,
    val custodyFlagSnapshot: String? = null,
    val override: Boolean = false,
    val overrideReason: String? = null,
    val linkedVisitId: String? = null,
    val releasedAt: String? = null,
    val attemptedAt: String? = null,
    val pickupConsentAt: String? = null,
    val pickupConsentVersion: String? = null,
    val createdAt: String? = null,
    val meta: Meta? = null,
)

@Serializable
data class PickupListResponse(
    val data: List<PickupOut> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class PickupConsentBody(
    val pickupConsentVersion: String,
    val pickupConsentAt: String? = null,
)

@Serializable
data class PickupReleaseBody(
    val collectorLivePhotoRef: String,
    val linkVisit: Boolean? = null,
)

object PickupReasons {
    val chips: List<Pair<String, String>> = listOf(
        "early" to "Early",
        "sick" to "Sick",
        "appointment" to "Appointment",
        "other" to "Other",
    )
    const val CONSENT_VERSION = "pickup_notice_en_hi_v1"
}
