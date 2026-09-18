package com.satcop.smartvisitor.kiosk.guardpatrol.data

import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import com.satcop.smartvisitor.kiosk.data.api.AuthSession
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.ErrorEnvelope
import com.satcop.smartvisitor.kiosk.data.model.LoginRequest
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Living F3 Guard Patrol client — Start+Active against /v1 rounds|scans|end.
 * Default demo: SCH-DEMO-01 · guard/guard123.
 */
class LiveGuardPatrolApi(
    private val baseUrl: String = ApiConfig.BASE_URL,
    private val session: AuthSession = AuthSession(),
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

    fun startRound(templateId: String, guardId: String): PatrolRoundDto =
        post("/rounds", json.encodeToString(StartRoundRequest(templateId, guardId)))

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
