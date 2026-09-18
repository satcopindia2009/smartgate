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

object PickupConsent {
    const val VERSION = PickupReasons.CONSENT_VERSION
    const val TITLE_EN = "Collector notice"
    const val TITLE_HI = "संग्राहक सूचना"
    const val BODY_EN =
        "This school will record your name, mobile number, and a live photo for this student pickup. Data is used for student safety and pickup audit. Live photos are retained up to 90 days (then deleted); pickup event metadata follows school audit retention. You may ask the school office about access, correction, or deletion.\n\nBy tapping I agree & continue, you consent to this processing for today’s pickup."
    const val BODY_HI =
        "यह स्कूल इस छात्र पिकअप के लिए आपका नाम, मोबाइल नंबर और लाइव फोटो दर्ज करेगा। डेटा छात्र सुरक्षा और पिकअप ऑडिट के लिए उपयोग होगा। लाइव फोटो अधिकतम 90 दिन रखी जाएगी (फिर हटा दी जाएगी); पिकअप इवेंट मेटाडेटा स्कूल की ऑडिट नीति के अनुसार रहेगा। पहुँच, सुधार या हटाने के लिए स्कूल कार्यालय से संपर्क करें।\n\nसहमत हूँ और आगे बढ़ें पर टैप करके आप आज के पिकअप के लिए सहमति देते हैं।"
    const val AGREE_EN = "I agree & continue"
    const val AGREE_HI = "सहमत हूँ और आगे बढ़ें"
    const val DECLINE = "Decline / Back · अस्वीकार / वापस"
}

