package com.satcop.smartvisitor.kiosk.guardpatrol.data

import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import com.satcop.smartvisitor.kiosk.data.api.AppAuth
import com.satcop.smartvisitor.kiosk.data.api.AuthSession
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.ErrorEnvelope
import com.satcop.smartvisitor.kiosk.data.model.LoginRequest
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Living F3 Guard Patrol client — Start+Active against /v1 rounds|scans|end.
 * Default demo: SCH-DEMO-01 · guard/guard123.
 */
class LiveGuardPatrolApi(
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val session: AuthSession = AppAuth.session,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(ApiConfig.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(ApiConfig.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(ApiConfig.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    val accessToken: String? get() = session.accessToken
    val signedInUser: MeResponse? get() = session.user
    val isSignedIn: Boolean get() = session.isSignedIn

    fun login(username: String, password: String): LoginResponse {
        val body = json.encodeToString(LoginRequest(username, password))
        val req = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(body.toRequestBody(JSON))
            .build()
        val parsed = json.decodeFromString<LoginResponse>(execute(req))
        session.accept(parsed.accessToken, parsed.user)
        return parsed
    }

    fun listTemplates(): List<PatrolTemplateDto> =
        get<PatrolTemplateListResponse>("/round-templates").data

    fun listCheckpoints(): List<PatrolCheckpointDto> =
        get<PatrolListResponse>("/checkpoints").data

    /**
     * GET /patrol-assignments?dutyDate=&guardId=
     * Prefer staffId (G1). Fixture-fallback only on transport/HTTP failure.
     */
    fun listAssignments(dutyDate: String, guardId: String = GuardPatrolFixtures.DEFAULT_GUARD_ID): List<PatrolAssignmentDto> {
        val path = "/patrol-assignments?dutyDate=$dutyDate&guardId=$guardId"
        return get<PatrolAssignmentListResponse>(path).data
    }

    /** Admin create — Mobile mainly GETs; stub ready for when living is seedable. */
    fun createAssignment(body: CreatePatrolAssignmentRequest): PatrolAssignmentDto =
        post("/patrol-assignments", json.encodeToString(body))

    fun startRound(
        templateId: String,
        guardId: String,
        assignmentId: String? = null,
    ): PatrolRoundDto {
        val body = if (assignmentId.isNullOrBlank()) {
            json.encodeToString(StartRoundRequest(templateId = templateId, guardId = guardId))
        } else {
            json.encodeToString(
                StartRoundRequest(
                    templateId = templateId,
                    guardId = guardId,
                    assignmentId = assignmentId,
                ),
            )
        }
        return post("/rounds", body)
    }

    fun getRound(id: String): PatrolRoundDto = get("/rounds/$id")

    fun scan(
        roundId: String,
        checkpointId: String,
        deviceId: String,
        lat: Double? = null,
        lng: Double? = null,
        offCampusSuspect: Boolean = false,
    ): PatrolRoundDto = post(
        "/rounds/$roundId/scans",
        json.encodeToString(
            ScanRequest(
                checkpointId = checkpointId,
                deviceId = deviceId,
                lat = lat,
                lng = lng,
                offCampusSuspect = offCampusSuspect,
            ),
        ),
    )

    fun endRound(roundId: String): PatrolRoundDto =
        post("/rounds/$roundId/end", "{}")


    /** POST /media/upload — kind=patrol_incident_photo (AC-PI1 / AC-PI4). */
    fun uploadMedia(
        bytes: ByteArray,
        filename: String = "incident.jpg",
        contentType: String = "image/jpeg",
        kind: String = "patrol_incident_photo",
    ): MediaUploadResponse {
        val part = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                filename,
                bytes.toRequestBody(contentType.toMediaType()),
            )
            .addFormDataPart("kind", kind)
            .build()
        val req = authorized(Request.Builder().url("$baseUrl/media/upload").post(part))
        return json.decodeFromString(execute(req))
    }

    /**
     * Wave 2 Patrol Incident — POST /patrol-incidents (fallback /rounds/{id}/incidents).
     * Prefer live; callers fixture-fallback only on transport/HTTP failure.
     */
    fun createIncident(body: CreatePatrolIncidentRequest): PatrolIncidentDto {
        val payload = json.encodeToString(body)
        return try {
            post("/patrol-incidents", payload)
        } catch (e: ApiException) {
            if (e.httpStatus == 404 && !body.roundId.isNullOrBlank()) {
                post("/rounds/${body.roundId}/incidents", payload)
            } else {
                throw e
            }
        }
    }


    private inline fun <reified T> get(path: String): T {
        val req = authorized(Request.Builder().url("$baseUrl$path").get())
        return json.decodeFromString(execute(req))
    }

    private inline fun <reified T> post(path: String, body: String): T {
        val req = authorized(
            Request.Builder().url("$baseUrl$path").post(body.toRequestBody(JSON)),
        )
        return json.decodeFromString(execute(req))
    }

    private fun authorized(builder: Request.Builder): Request {
        val token = session.accessToken
            ?: throw ApiException("UNAUTHENTICATED", "Not logged in", 401)
        return builder.header("Authorization", "Bearer $token").build()
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw apiError(resp.code, text)
            return text
        }
    }

    private fun apiError(code: Int, text: String): ApiException {
        val parsed = runCatching { json.decodeFromString<ErrorEnvelope>(text) }.getOrNull()
        return ApiException(
            code = parsed?.error?.code ?: "HTTP_$code",
            message = parsed?.error?.message ?: text.take(180).ifBlank { "HTTP $code" },
            httpStatus = code,
        )
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        const val DEMO_USERNAME = "guard"
        const val DEMO_PASSWORD = "guard123"
        const val DEMO_SCHOOL_ID = "SCH-DEMO-01"
        const val DEMO_DEVICE_ID = "guard-phone-G1"

        /** Optional secondary seed (Pranay school). */
        const val PRANAY_USERNAME = "pranay.sh"
        const val PRANAY_PASSWORD = "PranaySH@2026"
        const val PRANAY_SCHOOL_ID = "SCH-PRANAY-01"
    }
}
