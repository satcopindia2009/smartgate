package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable

/** POST /v1/auth/face/enroll — live when valley returns 200; local demo until then. */
@Serializable
data class FaceEnrollRequest(
    val username: String,
    val imageBase64: String,
    /** Preferred Compliance field. */
    val faceConsentVersion: String,
    val faceConsentAt: String,
    /** Aliases accepted by Backend. */
    val consentVersion: String? = null,
    val consentAt: String? = null,
)

@Serializable
data class FaceEnrollResponse(
    val ok: Boolean = true,
    val templateId: String? = null,
    val message: String? = null,
    val meta: Meta? = null,
)

/** POST /v1/auth/face/verify — no new consent; fail → password (AC-FL3). */
@Serializable
data class FaceVerifyRequest(
    val imageBase64: String,
    val username: String? = null,
)

@Serializable
data class FaceVerifyResponse(
    val matched: Boolean = false,
    val accessToken: String? = null,
    val tokenType: String = "Bearer",
    val expiresIn: Int? = null,
    val user: MeResponse? = null,
    val username: String? = null,
    val message: String? = null,
    val meta: Meta? = null,
)
