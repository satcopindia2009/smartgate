package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Int? = null,
    val user: MeResponse? = null,
    val meta: Meta? = null,
)

@Serializable
data class MediaUploadResponse(
    val key: String,
    val url: String? = null,
    val meta: Meta? = null,
)

@Serializable
data class BlacklistEntry(
    val id: String,
    val schoolId: String? = null,
    val name: String? = null,
    val mobile: String? = null,
    val idType: String? = null,
    val idNumber: String? = null,
    val reason: String? = null,
    val severity: String,
    val active: Boolean = true,
    val notes: String? = null,
)

@Serializable
data class BlacklistMatchRequest(
    val mobile: String? = null,
    val idType: String? = null,
    val idNumber: String? = null,
)

@Serializable
data class BlacklistMatchResponse(
    val hit: BlacklistEntry? = null,
    val meta: Meta? = null,
)

@Serializable
data class VisitCreate(
    val visitorName: String,
    val mobile: String,
    val visitorType: String,
    val purpose: String,
    val hostId: String,
    val livePhotoKey: String,
    val idType: String,
    val idNumber: String? = null,
    val idImageKey: String? = null,
    val vehicleNumber: String? = null,
    val accompanyingCount: Int? = null,
    val notes: String? = null,
    val signatureKey: String? = null,
    val gateId: String,
    val blacklistOverride: Boolean = false,
    val consentVersion: String? = null,
    val consentAt: String? = null,
)

@Serializable
data class VisitOut(
    val id: String,
    val schoolId: String? = null,
    val visitorName: String? = null,
    val mobile: String? = null,
    val visitorType: String? = null,
    val purpose: String? = null,
    val hostId: String? = null,
    val livePhotoKey: String? = null,
    val idType: String? = null,
    val idNumber: String? = null,
    val idImageKey: String? = null,
    val vehicleNumber: String? = null,
    val accompanyingCount: Int? = null,
    val notes: String? = null,
    val signatureKey: String? = null,
    val gateId: String? = null,
    val status: String,
    val passId: String? = null,
    val qrToken: String? = null,
    val blacklistHit: Boolean = false,
    val blacklistId: String? = null,
    val createdAt: String? = null,
    val timeIn: String? = null,
    val timeOut: String? = null,
    val meetingDoneAt: String? = null,
    val rejectReason: String? = null,
    val afterHours: Boolean = false,
    val policyTrigger: String? = null,
    val livePhotoUrl: String? = null,
    val consentVersion: String? = null,
    val consentAt: String? = null,
    val decidedAt: String? = null,
    val meta: Meta? = null,
)

@Serializable
data class PassScanRequest(
    val passId: String? = null,
    val token: String? = null,
    val action: String,
    val gateId: String? = null,
)

@Serializable
data class PassOut(
    val passId: String,
    val visitId: String,
    val schoolId: String? = null,
    val visitorName: String? = null,
    val photoUrl: String? = null,
    val hostId: String? = null,
    val hostName: String? = null,
    val gateId: String? = null,
    val gateName: String? = null,
    val status: String,
    val qrToken: String? = null,
    val issuedAt: String? = null,
    val expiresAt: String? = null,
    val revoked: Boolean = false,
    val meta: Meta? = null,
) {
    fun toVisit(): VisitOut = VisitOut(
        id = visitId,
        schoolId = schoolId,
        visitorName = visitorName,
        hostId = hostId,
        gateId = gateId,
        status = status,
        passId = passId,
        qrToken = qrToken,
        meta = meta,
    )
}

@Serializable
data class ErrorEnvelope(
    val error: ErrorBody? = null,
)

@Serializable
data class ErrorBody(
    val code: String? = null,
    val message: String? = null,
)

enum class IdType(val apiValue: String) {
    Aadhaar("Aadhaar"),
    DL("DL"),
    Voter("Voter"),
    Passport("Passport"),
    Other("Other"),
    ;

    companion object {
        fun fromApi(value: String): IdType =
            entries.firstOrNull { it.apiValue == value } ?: Aadhaar
    }
}

enum class DataSource { LIVE, FIXTURES }

class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int = 0,
) : RuntimeException(message)
