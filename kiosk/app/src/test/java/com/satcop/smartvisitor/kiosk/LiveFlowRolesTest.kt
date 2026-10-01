package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.AuthSession
import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.FaceVerifyRequest
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Runs the REAL app client (LiveVisitorApi) against the live API; skipped when the API is unreachable. */
class LiveFlowRolesTest {
    private val base = com.satcop.smartvisitor.kiosk.data.api.ApiConfig.BASE_URL

    private fun apiUp(): Boolean = runCatching {
        (URL(base.removeSuffix("/v1") + "/health").openConnection() as HttpURLConnection).run {
            connectTimeout = 5000; readTimeout = 5000; responseCode == 200
        }
    }.getOrDefault(false)

    // smallest valid 1x1 JPEG
    private val jpeg = Base64.getDecoder().decode(
        "/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wgALCAABAAEBAREA/8QAFBABAAAAAAAAAAAAAAAAAAAAAP/aAAgBAQABPxA=",
    )

    @Test
    fun allFourRolesFollowServerFaceFlags() {
        assumeTrue(apiUp())
        // username "Gate " proves trim+lowercase is required upstream; the API itself is case-sensitive.
        for ((u, verifiedAfterLogin) in listOf("gate" to false, "host" to true, "admin" to true, "guard" to false)) {
            val s = AuthSession()
            val api = LiveVisitorApi(base, s)
            val r = api.login(u, "${u}123")
            assertTrue(u, r.accessToken.isNotBlank())
            assertEquals(u, verifiedAfterLogin, s.faceVerified)
            assertEquals(u, verifiedAfterLogin, s.dataAccessAllowed)
            if (s.dataAccessAllowed) assertTrue(api.listGates().data.isNotEmpty())
        }
    }

    @Test
    fun gateFaceVerifyUpgradesSessionAndGateDataLoads() {
        assumeTrue(apiUp())
        val s = AuthSession()
        val api = LiveVisitorApi(base, s)
        api.login("gate", "gate123")
        assertFalse(s.dataAccessAllowed)
        val b64 = Base64.getEncoder().encodeToString(jpeg)
        val r = api.faceVerify(FaceVerifyRequest(imageBase64 = b64, capturedAt = "2026-09-30T11:00:00Z", gpsMissing = true))
        assertTrue(r.faceVerified)
        assertTrue(s.dataAccessAllowed)
        assertTrue(api.listGates().data.isNotEmpty())
    }

    @Test
    fun guardFaceVerifyRawBase64UpgradesSession() {
        assumeTrue(apiUp())
        val s = AuthSession()
        val api = LiveVisitorApi(base, s)
        api.login("guard", "guard123")
        assertFalse(s.dataAccessAllowed)
        try { api.listGates(); error("client gate must block") } catch (e: ApiException) { assertEquals("FACE_REQUIRED", e.code) }
        val b64 = Base64.getEncoder().encodeToString(jpeg)
        val r = api.faceVerify(FaceVerifyRequest(imageBase64 = b64, capturedAt = "2026-09-30T11:00:00Z", gpsMissing = true))
        assertTrue(r.faceVerified)
        assertTrue(s.dataAccessAllowed)
        assertTrue(api.listGates().data.isNotEmpty())
    }

    @Test
    fun wrongPasswordIsInvalidCredentials() {
        assumeTrue(apiUp())
        val api = LiveVisitorApi(base, AuthSession())
        try { api.login("gate", "nope"); error("should fail") } catch (e: ApiException) {
            assertEquals("INVALID_CREDENTIALS", e.code)
        }
    }
}
