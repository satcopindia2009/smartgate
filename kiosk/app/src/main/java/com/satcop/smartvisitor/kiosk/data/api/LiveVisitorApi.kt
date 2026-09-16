package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchRequest
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchResponse
import com.satcop.smartvisitor.kiosk.data.model.ErrorEnvelope
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.LoginRequest
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.PassOut
import com.satcop.smartvisitor.kiosk.data.model.PassScanRequest
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import java.util.concurrent.TimeUnit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class LiveVisitorApi(
    private val baseUrl: String = ApiConfig.BASE_URL,
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

    @Volatile
    var accessToken: String? = null
        private set

    fun login(username: String, password: String): LoginResponse {
        val body = json.encodeToString(LoginRequest(username, password))
        val req = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(body.toRequestBody(JSON))
            .build()
        val text = execute(req)
        val parsed = json.decodeFromString<LoginResponse>(text)
        accessToken = parsed.accessToken
        return parsed
    }

    fun loginGate(): Boolean = runCatching {
        login(ApiConfig.GATE_USERNAME, ApiConfig.GATE_PASSWORD)
        true
    }.getOrDefault(false)

    fun me(): MeResponse = get("/auth/me")

    fun listStaff(active: Boolean = true): StaffListResponse =
        get("/staff?active=$active")

    fun listGates(): GateListResponse = get("/gates")

    fun listInside(): InsideListResponse = get("/visits/inside")

    fun matchBlacklist(body: BlacklistMatchRequest): BlacklistMatchResponse =
        post("/blacklist/match", json.encodeToString(body))

    fun createVisit(body: VisitCreate): VisitOut =
        post("/visits", json.encodeToString(body))

    fun getVisit(id: String): VisitOut = get("/visits/$id")

    fun getPass(passId: String): PassOut = get("/passes/$passId")

    fun scanPass(
        passId: String? = null,
        token: String? = null,
        action: String,
        gateId: String? = null,
    ): VisitOut = post(
        "/passes/scan",
        json.encodeToString(
            PassScanRequest(passId = passId, token = token, action = action, gateId = gateId),
        ),
    )

    fun uploadMedia(
        bytes: ByteArray,
        filename: String,
        contentType: String,
        kind: String,
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
        val token = accessToken ?: throw ApiException("UNAUTHENTICATED", "Not logged in", 401)
        return builder.header("Authorization", "Bearer $token").build()
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val parsed = runCatching { json.decodeFromString<ErrorEnvelope>(text) }.getOrNull()
                throw ApiException(
                    code = parsed?.error?.code ?: "HTTP_${resp.code}",
                    message = parsed?.error?.message ?: text.take(180).ifBlank { "HTTP ${resp.code}" },
                    httpStatus = resp.code,
                )
            }
            return text
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
