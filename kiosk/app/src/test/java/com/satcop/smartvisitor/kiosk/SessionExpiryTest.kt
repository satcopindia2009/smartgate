package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.AuthSession
import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.api.SessionExpiry
import com.satcop.smartvisitor.kiosk.data.model.FaceVerifyResponse
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import java.io.File
import java.time.OffsetDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionExpiryTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val now = OffsetDateTime.parse("2026-09-30T16:15:00+05:30").toInstant().toEpochMilli()

    @Test
    fun guardCutOffIsNextMidnightIst() {
        val at = SessionExpiry.expiresAtMs("2026-10-01T00:00:00+05:30", 27835, now)!!
        assertEquals(OffsetDateTime.parse("2026-10-01T00:00:00+05:30").toInstant().toEpochMilli(), at)
        assertFalse(SessionExpiry.isExpired(at, now))
        // 23:59:59 IST still valid, 00:00:00 IST expired
        val last = OffsetDateTime.parse("2026-09-30T23:59:59+05:30").toInstant().toEpochMilli()
        assertFalse(SessionExpiry.isExpired(at, last))
        assertTrue(SessionExpiry.isExpired(at, at))
        assertTrue(SessionExpiry.isExpired(at, at + 1))
    }

    @Test
    fun fallsBackToExpiresInThenUnknown() {
        assertEquals(now + 43_200_000L, SessionExpiry.expiresAtMs(null, 43200, now))
        assertEquals(now + 10_000L, SessionExpiry.expiresAtMs("garbage", 10, now))
        assertNull(SessionExpiry.expiresAtMs(null, null, now))
        assertNull(SessionExpiry.expiresAtMs("", 0, now))
        assertFalse(SessionExpiry.isExpired(null, now))
    }

    @Test
    fun sessionEndClassification() {
        assertTrue(SessionExpiry.isSessionEnd(401, "TOKEN_EXPIRED", true))
        assertTrue(SessionExpiry.isSessionEnd(401, "TOKEN_INVALID", true))
        assertTrue(SessionExpiry.isSessionEnd(401, "UNAUTHORIZED", true))
        assertFalse("login with wrong password is not a session end", SessionExpiry.isSessionEnd(401, "INVALID_CREDENTIALS", false))
        assertFalse(SessionExpiry.isSessionEnd(401, "UNAUTHORIZED", false))
        assertFalse(SessionExpiry.isSessionEnd(403, "FORBIDDEN", true))
        assertFalse(SessionExpiry.isSessionEnd(403, "FACE_REQUIRED", true))
    }

    @Test
    fun loginAndFaceVerifyResponsesCarrySessionExpiresAt() {
        val login = json.decodeFromString<LoginResponse>(
            """{"accessToken":"t","expiresIn":27372,"sessionExpiresAt":"2026-10-01T00:00:00+05:30","faceRequired":true,"faceVerified":false,"mustChangePassword":false}""",
        )
        assertEquals("2026-10-01T00:00:00+05:30", login.sessionExpiresAt)
        val fv = json.decodeFromString<FaceVerifyResponse>(
            """{"matched":true,"faceVerified":true,"accessToken":"t2","sessionExpiresAt":"2026-10-01T00:00:00+05:30"}""",
        )
        assertEquals("2026-10-01T00:00:00+05:30", fv.sessionExpiresAt)
    }

    @Test
    fun resumeCheckFiresOnceWhenPastCutOffAndClearsWithLogout() {
        val s = AuthSession()
        assertFalse("not signed in: nothing to expire", s.isExpired(now))
        assertFalse(s.markExpired())
        val cutoff = now + 60_000
        s.accept("tok", null, faceVerified = true, faceRequired = true, expiresAtMs = cutoff)
        assertFalse(s.isExpired(now))
        assertTrue(s.isExpired(cutoff + 1))
        assertTrue(s.markExpired())
        s.clear()
        assertNull(s.expiresAtMs)
        assertFalse(s.isSignedIn)
    }

    @Test
    fun faceVerifyKeepsEarlierCutOffWhenResponseHasNone() {
        val s = AuthSession()
        s.accept("a", null, faceVerified = false, faceRequired = true, expiresAtMs = 123L)
        // same default as LiveVisitorApi.faceVerify when the response has no expiry
        s.accept("b", null, faceVerified = true, faceRequired = true)
        assertEquals(123L, s.expiresAtMs)
    }

    @Test
    fun expiryCopyIsTheContractSentence() {
        assertEquals("Your session has expired. Please sign in again.", ErrorCopy.SESSION_EXPIRED)
    }

    // ---- source guards (cheap regression nets) ----
    private fun src(rel: String): String =
        File("src/main/java/com/satcop/smartvisitor/kiosk/$rel").readText()

    @Test
    fun noRawServerMessageToastsRemainInViewModels() {
        listOf("ui/KioskViewModel.kt", "guardpatrol/ui/GuardPatrolViewModel.kt").forEach { f ->
            val body = src(f)
            assertFalse(f, Regex("toast = e\\.message").containsMatchIn(body))
            assertFalse(f, body.contains("e.message ?: \""))
            assertFalse(f, body.contains("err.message ?: \"Scan failed\""))
        }
    }

    @Test
    fun scheduledTileIsNotShownAnywhere() {
        // D8: Scheduled visits deferred -> no Scheduled tile/entry on Gate, Host or Guard.
        val shells = listOf("ui/apple/GuardTodayScreen.kt", "ui/apple/GateTodayScreen.kt", "ui/apple/HostInboxScreen.kt", "ui/guardhome/GuardHomeScreen.kt")
        shells.forEach { assertFalse(it, src(it).contains("Scheduled")) }
    }

    @Test
    fun noTunnelOrHostTextInUserFacingStrings() {
        listOf("data/api/LoginErrors.kt", "data/api/ErrorCopy.kt", "data/api/LiveVisitorApi.kt").forEach { f ->
            val body = src(f)
            assertFalse(f, body.contains("Cloudflare tunnel 1033"))
            assertFalse(f, body.contains("Base: broadband"))
            assertFalse(f, body.contains("broadband-headers"))
        }
    }

    @Test
    fun adminOnMobileAndAccessDeniedCopy() {
        assertEquals("Please use the web dashboard.", ErrorCopy.ADMIN_USE_WEB)
        assertEquals("You do not have permission to access this page.", ErrorCopy.FORBIDDEN)
        assertTrue(src("ui/KioskViewModel.kt").contains("ErrorCopy.ADMIN_USE_WEB"))
        assertTrue(src("ui/HostHomeScreen.kt").contains("You do not have permission to access this page."))
        assertNotNull(src("ui/KioskViewModel.kt").indexOf("onAppResumed"))
        assertTrue(src("ui/KioskApp.kt").contains("viewModel.onAppResumed()"))
    }
}
