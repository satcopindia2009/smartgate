package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.ApproveBody
import com.satcop.smartvisitor.kiosk.data.model.AuthorizedPickupListResponse
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchRequest
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchResponse
import com.satcop.smartvisitor.kiosk.data.model.ErrorEnvelope
import com.satcop.smartvisitor.kiosk.data.model.FaceEnrollRequest
import com.satcop.smartvisitor.kiosk.data.model.FaceEnrollResponse
import com.satcop.smartvisitor.kiosk.data.model.FaceVerifyRequest
import com.satcop.smartvisitor.kiosk.data.model.FaceVerifyResponse
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.HoursListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.LoginRequest
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.NotificationListResponse
import com.satcop.smartvisitor.kiosk.data.model.PassOut
import com.satcop.smartvisitor.kiosk.data.model.PassScanRequest
import com.satcop.smartvisitor.kiosk.data.model.PickupConsentBody
import com.satcop.smartvisitor.kiosk.data.model.PickupCreate
import com.satcop.smartvisitor.kiosk.data.model.PickupListResponse
import com.satcop.smartvisitor.kiosk.data.model.PickupOut
import com.satcop.smartvisitor.kiosk.data.model.PickupReleaseBody
import com.satcop.smartvisitor.kiosk.data.model.RejectBody
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse
import com.satcop.smartvisitor.kiosk.data.model.StudentListResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitListResponse
import com.satcop.smartvisitor.kiosk.data.model.CourierCreate
import com.satcop.smartvisitor.kiosk.data.model.CourierEvent
import com.satcop.smartvisitor.kiosk.data.model.CourierListResponse
import com.satcop.smartvisitor.kiosk.data.model.LostFoundCreate
import com.satcop.smartvisitor.kiosk.data.model.LostFoundItem
import com.satcop.smartvisitor.kiosk.data.model.LostFoundItemCreate
import com.satcop.smartvisitor.kiosk.data.model.LostFoundListResponse
import com.satcop.smartvisitor.kiosk.data.model.GateHistoryListResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.model.VisitorLookupResponse
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
        .addInterceptor { chain ->
            var resp = chain.proceed(chain.request())
            // Cloudflare quick-tunnel blip: one quiet retry on 530/1033 HTML.
            if (resp.code == 530 || (resp.code in 502..504)) {
                resp.close()
                Thread.sleep(400)
                resp = chain.proceed(chain.request())
            }
            resp
        }
        .build()

    val accessToken: String?
        get() = session.accessToken

    val signedInUser: MeResponse?
        get() = session.user

    fun login(username: String, password: String): LoginResponse {
        val body = json.encodeToString(LoginRequest(username, password))
        val req = Request.Builder()
            .url("$baseUrl/auth/login")
            .post(body.toRequestBody(JSON))
            .build()
        val text = execute(req)
        val parsed = json.decodeFromString<LoginResponse>(text)
        session.accept(parsed.accessToken, parsed.user)
        return parsed
    }

    fun logout() {
        session.clear()
    }

    fun me(): MeResponse = get<MeResponse>("/auth/me").also { session.updateUser(it) }

    fun listStaff(active: Boolean = true): StaffListResponse =
        get("/staff?active=$active")

    fun listGates(): GateListResponse = get("/gates")

    fun listInside(): InsideListResponse = get("/visits/inside")

    fun matchBlacklist(body: BlacklistMatchRequest): BlacklistMatchResponse =
        post("/blacklist/match", json.encodeToString(body))

    fun createVisit(body: VisitCreate): VisitOut =
        post("/visits", json.encodeToString(body))

    fun getVisit(id: String): VisitOut = get("/visits/$id")

    fun listPendingVisits(hostId: String? = null): VisitListResponse {
        val path = buildString {
            append("/visits?status=pending")
            if (!hostId.isNullOrBlank()) append("&hostId=").append(hostId)
        }
        return get(path)
    }

    fun approveVisit(id: String, reason: String? = null): VisitOut =
        post("/visits/$id/approve", json.encodeToString(ApproveBody(reason = reason)))

    fun rejectVisit(id: String, reason: String): VisitOut =
        post("/visits/$id/reject", json.encodeToString(RejectBody(reason = reason)))

    fun meetingDone(id: String): VisitOut =
        post("/visits/$id/meeting-done", "{}")

    fun listVisits(status: String, hostId: String? = null): VisitListResponse {
        val path = buildString {
            append("/visits?status=").append(status)
            if (!hostId.isNullOrBlank()) append("&hostId=").append(hostId)
        }
        return get(path)
    }

    fun listNotifications(limit: Int = 50): NotificationListResponse =
        get("/notifications?limit=$limit")

    fun listHours(): HoursListResponse = get("/access-rules/hours")

    fun getMediaBytes(key: String): ByteArray {
        val url = ApiConfig.mediaUrl(key)
        val req = authorized(Request.Builder().url(url).get())
        return executeBytes(req)
    }

    fun listStudents(q: String? = null, active: Boolean = true): StudentListResponse {
        val path = buildString {
            append("/students?active=$active")
            if (!q.isNullOrBlank()) {
                append("&q=").append(java.net.URLEncoder.encode(q, Charsets.UTF_8.name()))
            }
        }
        return get(path)
    }

    fun listAuthorizedPickup(studentId: String): AuthorizedPickupListResponse =
        get("/students/$studentId/authorized-pickup")

    fun listPickups(status: String? = null): PickupListResponse {
        val path = if (status.isNullOrBlank()) "/pickups" else "/pickups?status=$status"
        return get(path)
    }

    fun startPickup(body: PickupCreate): PickupOut =
        post("/pickups", json.encodeToString(body))

    fun getPickup(id: String): PickupOut = get("/pickups/$id")

    fun consentPickup(id: String, version: String): PickupOut =
        post("/pickups/$id/consent", json.encodeToString(PickupConsentBody(version)))

    fun releasePickup(id: String, photoRef: String): PickupOut =
        post("/pickups/$id/release", json.encodeToString(PickupReleaseBody(photoRef)))

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
        val token = session.accessToken ?: throw ApiException("UNAUTHENTICATED", "Not logged in", 401)
        return builder.header("Authorization", "Bearer $token").build()
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw apiError(resp.code, text)
            }
            return text
        }
    }

    private fun executeBytes(request: Request): ByteArray {
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                val text = resp.body?.string().orEmpty()
                throw apiError(resp.code, text)
            }
            return resp.body?.bytes() ?: ByteArray(0)
        }
    }

    private fun apiError(code: Int, text: String): ApiException {
        val lower = text.lowercase()
        if (code == 530 || "error code: 1033" in lower || ("cloudflare" in lower && "1033" in lower)) {
            return ApiException(
                code = "TUNNEL_DOWN",
                message = "Cloudflare tunnel 1033/530 — origin offline. Retry; not invalid password.",
                httpStatus = code,
            )
        }
        val parsed = runCatching { json.decodeFromString<ErrorEnvelope>(text) }.getOrNull()
        return ApiException(
            code = parsed?.error?.code ?: "HTTP_$code",
            message = parsed?.error?.message ?: text.take(180).ifBlank { "HTTP $code" },
            httpStatus = code,
        )
    }



    // --- Staff face login (Gate|Host|Guard) — valley POST /auth/face/* ---
    // Enroll + DELETE require Bearer (password login first). Verify is public unlock.

    fun faceEnroll(body: FaceEnrollRequest): FaceEnrollResponse {
        val consentVersion = body.consentVersion ?: body.faceConsentVersion
        val consentAt = body.consentAt ?: body.faceConsentAt
        val primary = body.copy(
            consentVersion = consentVersion,
            consentAt = consentAt,
            faceConsentVersion = (consentVersion ?: body.faceConsentVersion),
            faceConsentAt = (consentAt ?: body.faceConsentAt),
        )
        val payload = json.encodeToString(primary)
        return post("/auth/face/enroll", payload)
    }

    fun faceVerify(body: FaceVerifyRequest): FaceVerifyResponse {
        val req = Request.Builder()
            .url("$baseUrl/auth/face/verify")
            .post(json.encodeToString(body).toRequestBody(JSON))
            .build()
        val text = execute(req)
        val parsed = json.decodeFromString<FaceVerifyResponse>(text)
        if (!parsed.accessToken.isNullOrBlank() && parsed.user != null) {
            session.accept(parsed.accessToken, parsed.user)
        }
        return parsed
    }

    fun faceDeleteTemplate(): FaceEnrollResponse {
        val req = authorized(Request.Builder().url("$baseUrl/auth/face/template").delete())
        val text = execute(req)
        return runCatching { json.decodeFromString<FaceEnrollResponse>(text) }.getOrElse {
            FaceEnrollResponse(ok = true, message = text.take(120))
        }
    }

    // --- Guard ASAP living endpoints ---

    fun lookupVisitorByMobile(mobile: String): VisitorLookupResponse {
        val q = java.net.URLEncoder.encode(mobile, Charsets.UTF_8.name())
        return get("/visitors/lookup-by-mobile?mobile=$q")
    }

    fun listGateHistory(
        dateFrom: String? = null,
        dateTo: String? = null,
        kind: String? = null,
        status: String? = null,
        gateId: String? = null,
    ): GateHistoryListResponse {
        val parts = mutableListOf<String>()
        if (!dateFrom.isNullOrBlank()) parts += "dateFrom=$dateFrom"
        if (!dateTo.isNullOrBlank()) parts += "dateTo=$dateTo"
        if (!kind.isNullOrBlank() && kind != "all") parts += "type=$kind"
        if (!status.isNullOrBlank() && status != "all") parts += "status=$status"
        if (!gateId.isNullOrBlank()) parts += "gateId=$gateId"
        val qs = if (parts.isEmpty()) "" else "?" + parts.joinToString("&")
        return get("/gate/history$qs")
    }

    fun listCouriers(): CourierListResponse = get("/couriers")

    fun receiveCourier(body: CourierCreate): CourierEvent =
        post("/couriers", json.encodeToString(body))

    fun handOverCourier(id: String): CourierEvent =
        post("/couriers/$id/handover", "{}")

    fun returnCourier(id: String): CourierEvent =
        post("/couriers/$id/return", "{}")

    fun getCourier(id: String): CourierEvent = get("/couriers/$id")

    fun checkoutVisit(visitId: String, gateId: String? = null): VisitOut {
        val payload = if (gateId.isNullOrBlank()) "{}" else """{"gateId":"$gateId"}"""
        return post("/visits/$visitId/check-out", payload)
    }

    fun listLostFound(status: String? = null): LostFoundListResponse {
        val path = if (status.isNullOrBlank()) "/lost-found/items" else "/lost-found/items?status=$status"
        return get(path)
    }

    fun createLostFound(body: LostFoundCreate): LostFoundItem {
        val photo = body.photoKey?.takeIf { it.isNotBlank() } ?: "media/lost_found/placeholder"
        val payload = LostFoundItemCreate(
            description = body.description.trim(),
            photoKey = photo,
            foundLocation = body.locationFound.trim(),
            foundGateId = body.gateId?.takeIf { it.isNotBlank() },
            foundZone = body.foundZone?.takeIf { it.isNotBlank() },
            foundAt = body.foundAt.takeIf { it.isNotBlank() },
            status = "Open",
        )
        return post("/lost-found/items", json.encodeToString(payload))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
