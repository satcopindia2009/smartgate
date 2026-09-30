package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.AuthSession
import com.satcop.smartvisitor.kiosk.data.api.LoginErrors
import com.satcop.smartvisitor.kiosk.data.api.LoginInput
import com.satcop.smartvisitor.kiosk.data.face.FaceErrors
import com.satcop.smartvisitor.kiosk.data.face.FaceImage
import com.satcop.smartvisitor.kiosk.data.geo.GeoFenceCodes
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.FaceVerifyRequest
import com.satcop.smartvisitor.kiosk.data.model.FaceVerifyResponse
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.ui.FaceGateMachine
import com.satcop.smartvisitor.kiosk.ui.FaceGatePolicy
import com.satcop.smartvisitor.kiosk.ui.GateStage
import com.satcop.smartvisitor.kiosk.ui.KioskRole
import com.satcop.smartvisitor.kiosk.ui.KioskUiState
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginFaceHotfixTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    private fun loginJson(role: String, verified: Boolean, required: Boolean) = """
        {"accessToken":"t","tokenType":"Bearer","expiresIn":43200,
         "user":{"id":"U","schoolId":"S","role":"$role","staffId":"X","gateIds":null,"displayName":"N","phone":"1","email":"e"},
         "faceVerified":$verified,"faceRequired":$required,"mustChangePassword":false,
         "sessionExpiresAt":"2026-10-01T04:27:00+05:30","meta":{"watermark":"DEMO"}}
    """.trimIndent()

    private fun stageFor(resp: LoginResponse) =
        FaceGateMachine.onLoginResult(GateStage.SIGNED_OUT, resp.faceVerified, resp.faceRequired)

    // ---- real /auth/login bodies captured from the live API for the 4 demo roles ----
    @Test
    fun gateIsNotEnforcedSoNoFaceStep() {
        val r = json.decodeFromString<LoginResponse>(loginJson("gate", verified = false, required = false))
        assertEquals(GateStage.VERIFIED, stageFor(r))
    }

    @Test
    fun hostAndAdminAlreadyVerifiedSkipFace() {
        for (role in listOf("host", "admin")) {
            val r = json.decodeFromString<LoginResponse>(loginJson(role, verified = true, required = false))
            assertEquals(role, GateStage.VERIFIED, stageFor(r))
        }
    }

    @Test
    fun guardMustDoFace() {
        val r = json.decodeFromString<LoginResponse>(loginJson("guard", verified = false, required = true))
        assertEquals(GateStage.FACE_PENDING, stageFor(r))
        assertTrue(FaceGatePolicy.needsFace(false, true))
        assertFalse(FaceGatePolicy.needsFace(true, true))
        assertFalse(FaceGatePolicy.needsFace(false, false))
    }

    @Test
    fun sessionDataAccessFollowsServerFlags() {
        val s = AuthSession()
        val me = json.decodeFromString<LoginResponse>(loginJson("guard", false, true)).user
        s.accept("t", me, faceVerified = false, faceRequired = true)
        assertFalse(s.dataAccessAllowed)
        s.accept("t2", me, faceVerified = true, faceRequired = true)
        assertTrue(s.dataAccessAllowed)
    }

    @Test
    fun roleRouting() {
        assertEquals(KioskRole.GATE, KioskRole.fromJwt("gate"))
        assertEquals(KioskRole.HOST, KioskRole.fromJwt("host"))
        assertEquals(KioskRole.GUARD, KioskRole.fromJwt("guard"))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt("admin"))
    }

    // ---- login input hygiene ----
    @Test
    fun usernameIsTrimmedAndLowercased() {
        assertEquals("gate", LoginInput.normalizeUsername("Gate "))
        assertEquals("guard", LoginInput.normalizeUsername("  GUARD\n"))
    }

    @Test
    fun noPrefilledCredentials() {
        val st = KioskUiState()
        assertEquals("", st.loginUsername)
        assertEquals("", st.loginPassword)
    }

    // ---- error mapping ----
    @Test
    fun loginErrorsAreFriendlyAndHostFree() {
        val all = listOf(
            LoginErrors.message(UnknownHostException("broadband-headers-commander-drug.trycloudflare.com")),
            LoginErrors.message(SocketTimeoutException("timeout")),
            LoginErrors.message(SSLHandshakeException("x")),
            LoginErrors.message(SerializationException("bad")),
            LoginErrors.message(ApiException("TUNNEL_DOWN", "Cloudflare tunnel 1033", 530)),
            LoginErrors.message(ApiException("HTTP_502", "<html>cloudflare</html>", 502)),
            LoginErrors.message(RuntimeException("boom broadband-headers")),
        )
        for (m in all) {
            assertFalse(m, m.contains("trycloudflare", true))
            assertFalse(m, m.contains("broadband", true))
            assertFalse(m, m.contains("<html", true))
        }
        assertEquals(LoginErrors.OFFLINE_MESSAGE, all[0])
        assertEquals(LoginErrors.TIMEOUT_MESSAGE, all[1])
        assertEquals(LoginErrors.TLS_MESSAGE, all[2])
        assertEquals(LoginErrors.INVALID_MESSAGE, LoginErrors.message(ApiException("INVALID_CREDENTIALS", "x", 401)))
    }

    // ---- face-verify: server text wins ----
    @Test
    fun geoFenceShowsServerMessage() {
        val server = "You are outside the school campus. Please move inside the campus and try again."
        val m = FaceErrors.message(ApiException("GEO_FENCE_RESTRICTED", server, 403))
        assertEquals(server, m)
        assertEquals(GeoFenceCodes.DEFAULT_MESSAGE, FaceErrors.message(ApiException("GEO_FENCE_RESTRICTED", "", 403)))
    }

    @Test
    fun faceRequiredIsNotMappedToGeoFence() {
        assertFalse(GeoFenceCodes.isRestricted("FACE_REQUIRED", "Face verification required"))
        assertTrue(GeoFenceCodes.isRestricted("GEO_FENCE_RESTRICTED", "x"))
    }

    @Test
    fun otherFaceErrorsUseServerBodyNotGenericText() {
        assertEquals("No face matched", FaceErrors.message(ApiException("FACE_MISMATCH", "No face matched", 401)))
        assertEquals("Face login is disabled for this school", FaceErrors.message(ApiException("FACE_LOGIN_DISABLED", "Face login is disabled for this school", 403)))
        assertEquals("imageBase64 required", FaceErrors.message(ApiException("VALIDATION", "imageBase64 required", 400)))
        assertEquals(LoginErrors.OFFLINE_MESSAGE, FaceErrors.message(UnknownHostException("h")))
    }

    // ---- face payload shape ----
    @Test
    fun faceVerifyPayloadIsRawBase64WithoutDataUriAndOmitsMissingGps() {
        val body = json.encodeToString(
            FaceVerifyRequest(imageBase64 = "QUJD", username = null, capturedAt = "2026-09-30T11:00:00Z", gpsMissing = true),
        )
        assertTrue(body.contains("\"imageBase64\":\"QUJD\""))
        assertFalse(body.contains("data:image"))
        assertFalse(body.contains("\"lat\""))
        assertFalse(body.contains("\"lng\""))
        assertTrue(body.contains("\"gpsMissing\":true"))
    }

    @Test
    fun faceVerifyPayloadSendsGpsOnlyWhenPresent() {
        val body = json.encodeToString(FaceVerifyRequest("QUJD", null, null, 18.5, 73.8, 12f, false))
        assertTrue(body.contains("\"lat\":18.5"))
        assertTrue(body.contains("\"gpsMissing\":false"))
    }

    @Test
    fun faceVerifyResponseParsesLiveShapeAndSoftWarning() {
        val raw = """{"accessToken":"new","tokenType":"Bearer","expiresIn":27158,
          "user":{"id":"U-GUARD","schoolId":"SCH","role":"guard","staffId":"G1","gateIds":null,"displayName":"Guard G1","phone":"1","email":"e"},
          "faceVerified":true,"sessionExpiresAt":"x","matched":true,"staffId":"G1","role":"guard","loginAt":"x",
          "lat":18.52,"lng":73.85,"gpsMissing":false,"capturedAt":null,"meta":{"watermark":"DEMO","demoAccept":true},
          "warn":"Outside campus geo-fence (soft); flagged offCampusSuspect"}""".trimIndent()
        val r = json.decodeFromString<FaceVerifyResponse>(raw)
        assertTrue(r.faceVerified)
        assertEquals("new", r.accessToken)
        assertEquals("Outside campus geo-fence (soft); flagged offCampusSuspect", r.warn)
    }

    // ---- image pipeline ----
    @Test
    fun imageIsDownscaledTo1024AndNeverUpscaled() {
        assertEquals(1024 to 768, FaceImage.targetSize(4000, 3000))
        assertEquals(768 to 1024, FaceImage.targetSize(3000, 4000))
        assertEquals(640 to 480, FaceImage.targetSize(640, 480))
        assertEquals(1 to 1, FaceImage.targetSize(0, 0))
        assertEquals(2, FaceImage.sampleSize(4000, 3000))
        assertEquals(1, FaceImage.sampleSize(1024, 768))
        assertEquals(80, FaceImage.QUALITY)
    }
}
