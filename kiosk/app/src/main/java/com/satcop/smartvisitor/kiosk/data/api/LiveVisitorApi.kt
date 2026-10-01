package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
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
import com.satcop.smartvisitor.kiosk.data.model.ProfileLookupResponse
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.School
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.DeviceRegisterBody
import com.satcop.smartvisitor.kiosk.data.model.DeviceRegisterResponse
import com.satcop.smartvisitor.kiosk.data.model.HostFeedResponse
import com.satcop.smartvisitor.kiosk.data.model.MarkReadBody
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

    // lazy: building an OkHttpClient (pool, dispatcher) is not free; do it on the first real call (always on IO), not in the ViewModel constructor on main.
    private val client by lazy { buildClient() }

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(ApiConfig.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(ApiConfig.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(ApiConfig.CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            var resp = chain.proceed(chain.request())
            // Cloudflare quick-tunnel blip: one quiet retry on 530/502–504 (no sleep — avoid jank).
            if (resp.code == 530 || (resp.code in 502..504)) {
                resp.close()
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
            .header("Accept", "application/json")
            .post(body.toRequestBody(JSON))
            .build()
        val text = execute(req)
        val parsed = json.decodeFromString<LoginResponse>(text)
        // 1059b: follow the server. Only a role with faceRequired && !faceVerified (guard) must do the face step.
        // 1073: a GUARD is face-checked on every sign-in even if the server omits faceRequired (the face step IS the clock-in).
        // 1076: the same lock -> face -> clock-in flow for guard, gate, gate_staff, security, security_head (host/admin untouched).
        val isGuard = com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.requiresClockIn(parsed.user?.role)
        val needsFace = com.satcop.smartvisitor.kiosk.ui.FaceGatePolicy.needsFace(parsed.faceVerified, parsed.faceRequired || isGuard)
        session.accept(
            parsed.accessToken, parsed.user, faceVerified = !needsFace, faceRequired = parsed.faceRequired || isGuard,
            expiresAtMs = SessionExpiry.expiresAtMs(parsed.sessionExpiresAt, parsed.expiresIn, System.currentTimeMillis()),
        )
        return parsed
    }

    fun logout() {
        session.clear()
    }

    fun me(): MeResponse = get<MeResponse>("/auth/me", allowUnverified = true).also { session.updateUser(it) }

    /** GET /schools/me — faceLoginEnabled + geoFenceMode when Backend READY. */
    fun schoolMe(): School = get("/schools/me")

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

    /** Host feed (Backend contract 2026-09-30): newest first, `since` exclusive (NTF id or ISO). */
    fun hostFeed(sinceId: String?, limit: Int = 50): HostFeedResponse {
        val path = buildString {
            append("/notifications?unreadOnly=true&limit=").append(limit)
            if (!sinceId.isNullOrBlank()) {
                append("&since=").append(java.net.URLEncoder.encode(sinceId, Charsets.UTF_8.name()))
            }
        }
        return get(path)
    }

    fun markNotificationRead(id: String) {
        val req = authorized(
            Request.Builder().url("$baseUrl/notifications/$id/read").post("{}".toRequestBody(JSON)),
        )
        execute(req)
    }

    fun markNotificationsRead(ids: List<String>) {
        val body = json.encodeToString(MarkReadBody(ids = ids))
        val req = authorized(
            Request.Builder().url("$baseUrl/notifications/read").post(body.toRequestBody(JSON)),
        )
        execute(req)
    }

    /** POST /devices {token,platform} -> 201 {id}. Allowed before face verify. Stored only (no push yet). */
    fun registerDevice(token: String, platform: String = "android"): String? {
        val body = json.encodeToString(DeviceRegisterBody(token = token, platform = platform))
        val req = authorized(
            Request.Builder().url("$baseUrl/devices").post(body.toRequestBody(JSON)),
            allowUnverified = true,
        )
        return runCatching { json.decodeFromString<DeviceRegisterResponse>(execute(req)).id }.getOrNull()
    }

    fun listHours(): HoursListResponse = get("/access-rules/hours")

    fun getMediaBytes(key: String): ByteArray {
        val url = ApiConfig.mediaUrl(key)
        val req = authorized(Request.Builder().url(url).get())
        return executeBytes(req)
    }

    /**
     * Fetch a media URL exactly as the API returned it (query, incl. the signed ?t=, stays intact). The Bearer is
     * attached only for the API's own host; URLs are never built from keys here.
     */
    fun getMediaByUrl(url: String): ByteArray {
        val target = MediaUrl.resolve(url) ?: throw ApiException("MEDIA_URL", "Bad media url", 0)
        val b = Request.Builder().url(target.url).get()
        val req = if (target.attachBearer) authorized(b) else b.build()
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
        consentAt: String? = null,
        consentVersion: String? = null,
    ): MediaUploadResponse {
        val builder = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                filename,
                bytes.toRequestBody(contentType.toMediaType()),
            )
            .addFormDataPart("kind", kind)
        if (!consentAt.isNullOrBlank()) builder.addFormDataPart("consentAt", consentAt)
        if (!consentVersion.isNullOrBlank()) builder.addFormDataPart("consentVersion", consentVersion)
        val part = builder.build()
        val req = authorized(Request.Builder().url("$baseUrl/media/upload").post(part))
        return json.decodeFromString(execute(req))
    }

    /**
     * OTP endpoints (contract 2026-09-30). [withAuth] false only for password_reset send and password-reset.
     * Returns the raw JSON text; errors arrive as [ApiException] with the API's own code/message/details.
     */
    fun otpRequest(method: String, path: String, body: String?, withAuth: Boolean): String {
        val b = Request.Builder().url("$baseUrl$path")
        if (method == "GET") b.get() else b.post((body ?: "{}").toRequestBody(JSON))
        val req = if (withAuth) authorized(b, allowUnverified = false) else b.header("Accept", "application/json").build()
        return execute(req)
    }

    /** GET /schools/me (readable by every signed-in role; carries otpMode/otpChannel/visitorOtpEnabled/visitorOtpAllowSkip). */
    fun schoolSettingsText(): String = execute(authorized(Request.Builder().url("$baseUrl/schools/me").get()))

    private inline fun <reified T> get(path: String, allowUnverified: Boolean = false): T {
        val req = authorized(Request.Builder().url("$baseUrl$path").get(), allowUnverified)
        return json.decodeFromString(execute(req))
    }

    private inline fun <reified T> post(path: String, body: String, allowUnverified: Boolean = false): T {
        val req = authorized(
            Request.Builder().url("$baseUrl$path").post(body.toRequestBody(JSON)),
            allowUnverified,
        )
        return json.decodeFromString(execute(req))
    }

    /**
     * Client-side face gate: no data request leaves the device before face verification
     * (server enforces the same with 403 FACE_REQUIRED). Auth/face/devices routes pass [allowUnverified].
     */
    private fun authorized(builder: Request.Builder, allowUnverified: Boolean = false): Request {
        val token = session.accessToken ?: throw ApiException("UNAUTHENTICATED", "Not logged in", 401)
        if (session.isExpired()) {
            session.markExpired()
            throw ApiException("TOKEN_EXPIRED", ErrorCopy.SESSION_EXPIRED, 401)
        }
        if (!allowUnverified && !session.faceVerified) {
            throw ApiException("FACE_REQUIRED", "Face verification required", 403)
        }
        return builder.header("Authorization", "Bearer $token").build()
    }

    private fun execute(request: Request): String {
        client.newCall(request).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                throw apiError(resp.code, text).also {
                    noteFaceRequired(it)
                    noteSessionEnd(it, request.header("Authorization") != null)
                }
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

    private fun noteSessionEnd(e: ApiException, hadToken: Boolean) {
        if (SessionExpiry.isSessionEnd(e.httpStatus, e.code, hadToken)) session.markExpired()
    }

    private fun noteFaceRequired(e: ApiException) {
        if (e.code == "FACE_REQUIRED") session.markFaceRequired()
    }

    private fun apiError(code: Int, text: String): ApiException {
        val lower = text.lowercase()
        if (code == 530 || "error code: 1033" in lower || ("cloudflare" in lower && "1033" in lower)) {
            return ApiException(
                code = "TUNNEL_DOWN",
                message = ErrorCopy.TEMPORARILY_DOWN,
                httpStatus = code,
            )
        }
        val parsed = runCatching { json.decodeFromString<ErrorEnvelope>(text) }.getOrNull()
        return ApiException(
            code = parsed?.error?.code ?: "HTTP_$code",
            message = parsed?.error?.message ?: text.take(180).ifBlank { "HTTP $code" },
            httpStatus = code,
            details = ErrorDetails.flatten(parsed?.error?.details),
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
        return post("/auth/face/enroll", payload, allowUnverified = true)
    }

    /**
     * POST /auth/face-verify (Backend 1059). With a login Bearer it upgrades the session and returns a NEW
     * token with faceVerified=true; without a token it needs `username`. Only a response that says
     * faceVerified=true is accepted as verified — the old unverified token is replaced, never reused.
     */
    fun faceVerify(body: FaceVerifyRequest): FaceVerifyResponse {
        val builder = Request.Builder()
            .url("$baseUrl/auth/face-verify")
            .post(json.encodeToString(body).toRequestBody(JSON))
        val token = session.accessToken
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        val text = execute(builder.build())
        val parsed = json.decodeFromString<FaceVerifyResponse>(text)
        if (parsed.faceVerified && !parsed.accessToken.isNullOrBlank() && parsed.user != null) {
            session.accept(
                parsed.accessToken, parsed.user, faceVerified = true, faceRequired = session.faceRequired,
                expiresAtMs = SessionExpiry.expiresAtMs(parsed.sessionExpiresAt, parsed.expiresIn, System.currentTimeMillis())
                    ?: session.expiresAtMs,
            )
        }
        return parsed
    }

    fun faceDeleteTemplate(): FaceEnrollResponse {
        val req = authorized(Request.Builder().url("$baseUrl/auth/face/template").delete(), allowUnverified = true)
        val text = execute(req)
        return runCatching { json.decodeFromString<FaceEnrollResponse>(text) }.getOrElse {
            FaceEnrollResponse(ok = true, message = text.take(120))
        }
    }

    // --- Guard ASAP living endpoints ---

    /** Canonical unified lookup (visitor + vendor, server-side dedupe, masked for guard/gate). */
    fun lookupProfile(mobile: String): ProfileLookupResponse {
        val q = java.net.URLEncoder.encode(mobile, Charsets.UTF_8.name())
        return get("/visitors/lookup?mobile=$q")
    }

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

    // --- Attendance (Backend 2026-09-30): server clock + geofence decide; client only sends a fresh attempt ---

    fun attendanceToday(): TodayAttendance = get("/attendance/me/today")

    /** 1072: fence mode + radius + centre + rules (guard face-verified or gate). Every field optional. */
    fun guardGeofence(): com.satcop.smartvisitor.kiosk.data.model.GeofenceInfo = get("/guards/me/geofence")

    /** Guard Today: patrol progress + incidents (guard face-verified or gate; FACE_REQUIRED otherwise). */
    fun guardTodaySummary(): com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary = get("/guards/me/today-summary")

    fun attendanceCheckIn(body: AttendanceRequest): AttendanceRow =
        post("/attendance/check-in", json.encodeToString(body))

    fun attendanceClockOut(body: AttendanceRequest): AttendanceRow =
        post("/attendance/clock-out", json.encodeToString(body))

    /** Visits created between two IST dates (inclusive). Backend caps the range at 90 days. */
    fun listVisitsBetween(dateFrom: String, dateTo: String, q: String? = null, limit: Int = 500): VisitListResponse {
        val path = buildString {
            append("/visits?dateFrom=").append(dateFrom).append("&dateTo=").append(dateTo)
            append("&limit=").append(limit)
            if (!q.isNullOrBlank()) {
                append("&q=").append(java.net.URLEncoder.encode(q.trim(), Charsets.UTF_8.name()))
            }
        }
        return get(path)
    }

    fun receiveCourier(body: CourierCreate): CourierEvent =
        post("/couriers", json.encodeToString(body))

    fun handOverCourier(id: String): CourierEvent =
        post("/couriers/$id/handover", "{}")

    fun returnCourier(id: String): CourierEvent =
        post("/couriers/$id/return", "{}")

    fun getCourier(id: String): CourierEvent = get("/couriers/$id")

    /** D15: check in an APPROVED visit (gate and guard). 409 INVALID_STATE otherwise. */
    fun checkInVisit(visitId: String, gateId: String? = null): VisitOut {
        val payload = if (gateId.isNullOrBlank()) "{}" else """{"gateId":"$gateId"}"""
        return post("/visits/$visitId/check-in", payload)
    }

    fun checkoutVisit(visitId: String, gateId: String? = null): VisitOut {
        val payload = if (gateId.isNullOrBlank()) "{}" else """{"gateId":"$gateId"}"""
        return post("/visits/$visitId/check-out", payload)
    }

    fun listLostFound(status: String? = null): LostFoundListResponse {
        val path = if (status.isNullOrBlank()) "/lost-found/items" else "/lost-found/items?status=$status"
        return get(path)
    }

    fun createLostFound(body: LostFoundCreate): LostFoundItem {
        val photo = body.photoKey?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("photoKey required (AC-LF1)")
        val name = body.itemName.trim().ifBlank { body.description.trim() }
        val category = body.category.trim().ifBlank { "Other" }
        val payload = LostFoundItemCreate(
            description = body.description.trim(),
            photoKey = photo,
            foundLocation = body.locationFound.trim().ifBlank { null },
            foundGateId = body.gateId?.takeIf { it.isNotBlank() },
            foundZone = body.foundZone?.takeIf { it.isNotBlank() },
            foundAt = body.foundAt.takeIf { it.isNotBlank() },
            status = "Open",
            itemType = body.itemType,
            itemName = name,
            category = category,
            reportedBy = body.finderName.takeIf { it.isNotBlank() },
            contactNumber = body.finderMobile?.takeIf { it.isNotBlank() },
        )
        return post("/lost-found/items", json.encodeToString(payload))
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
