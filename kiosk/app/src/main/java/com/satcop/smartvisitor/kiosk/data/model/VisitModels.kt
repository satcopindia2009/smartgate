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
    /** Backend 1059 contract: face gate flags (informational; client policy is stricter). */
    val faceVerified: Boolean = false,
    val faceRequired: Boolean = false,
    /** IST cut-off of this token (guard: next 00:00 IST). */
    val sessionExpiresAt: String? = null,
    val mustChangePassword: Boolean = false,
    // Clock-in addendum: login top level (also inside user).
    val guardName: String? = null,
    val schoolName: String? = null,
    val profilePhotoUrl: String? = null,
    // Guard shift (Backend 2026-10-01, additive, null when no shift is assigned).
    val shiftName: String? = null,
    val shiftStartTime: String? = null,
    /** e.g. "Morning · starts 06:30 am" - shown verbatim. */
    val shiftStartDisplay: String? = null,
    // 1077 duty (Backend frozen contract 2026-10-01): additive, null for other roles / until Backend ships it.
    val dutyTypes: List<String>? = null,
    val primaryHome: String? = null,
    val homeOrder: List<String>? = null,
    val hasDuty: Boolean? = null,
    val dutyRevision: String? = null,
    val dutyAssignments: List<DutyCompact>? = null,
    val shiftEndTime: String? = null,
    val shiftEndDisplay: String? = null,
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

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
data class VisitCreate(
    val visitorName: String,
    val mobile: String,
    /** null = omitted: the server links to the existing profile's type (Add Visitor contract 2026-09-30 section 4). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val visitorType: String? = null,
    val purpose: String,
    /** Omitted for a vendor who picked a department instead of a host (N1: the server routes it). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val hostId: String? = null,
    /** Vendor only, sent when no host is chosen (hostId omitted). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val department: String? = null,
    val livePhotoKey: String,
    val idType: String,
    /** null = omitted = "use the saved ID". A masked value is never sent. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val idNumber: String? = null,
    val idImageKey: String? = null,
    /** Required by the server when idType is "Other" and a new ID number is sent (400 VALIDATION otherwise). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val idTypeName: String? = null,
    val vehicleNumber: String? = null,
    val accompanyingCount: Int? = null,
    val notes: String? = null,
    val signatureKey: String? = null,
    val gateId: String,
    val blacklistOverride: Boolean = false,
    val consentVersion: String? = null,
    val consentAt: String? = null,
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val profileId: String? = null,
    /** true only after the guard confirmed "Register as different type". */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val confirmKindSwitch: Boolean? = null,
    /** Vendor company (trimmed). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val company: String? = null,
    /** Visitors only; a vendor with scheduledAt is refused (VENDOR_NO_SCHEDULE). ISO-8601 with +05:30. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val scheduledAt: String? = null,
    /** One id per submit; the same id returns the original entry (idempotentReplay). */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val attemptId: String? = null,
    /** visitor_verify: the visitorVerifyId returned by POST /otp/verify. Omitted when OTP was not used/skipped. */
    @kotlinx.serialization.EncodeDefault(kotlinx.serialization.EncodeDefault.Mode.NEVER)
    val otpId: String? = null,
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
    // Add Visitor contract 2026-09-30 (additive)
    val profileId: String? = null,
    val kind: String? = null,
    val company: String? = null,
    val scheduledAt: String? = null,
    val statusCode: String? = null,
    val displayStatus: String? = null,
    val displayNote: String? = null,
    val mobileMasked: String? = null,
    val idNumberMasked: String? = null,
    val idOnFile: Boolean? = null,
    val created: Boolean? = null,
    val profileCreated: Boolean? = null,
    val idempotentReplay: Boolean? = null,
    val meta: Meta? = null,
) {
    val isVendor: Boolean get() = kind.equals("vendor", true) || visitorType.equals("Vendor", true)
}

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
    val details: kotlinx.serialization.json.JsonElement? = null,
)

enum class IdType(val apiValue: String) {
    Aadhaar("Aadhaar"),
    PAN("PAN"),
    Passport("Passport"),
    Voter("Voter"),
    DL("DL"),
    Other("Other"),
    ;

    companion object {
        fun fromApi(value: String): IdType =
            entries.firstOrNull { it.apiValue == value } ?: Aadhaar
    }
}

enum class DataSource { LIVE, OFFLINE }

class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int = 0,
    /** Flattened error `details` (e.g. field, profileId, profileType, activeVisit.id, activeVisit.status). */
    val details: Map<String, String> = emptyMap(),
) : RuntimeException(message)
