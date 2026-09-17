package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import com.satcop.smartvisitor.kiosk.data.api.AuthSession
import com.satcop.smartvisitor.kiosk.data.api.LoginErrors
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.LoginRequest
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthLoginTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun apiConfigHasNoEmbeddedGatePassword() {
        val names = ApiConfig::class.java.declaredFields.map { it.name }
        assertFalse(names.contains("GATE_USERNAME"))
        assertFalse(names.contains("GATE_PASSWORD"))
        assertFalse(names.contains("HOST_USERNAME"))
        assertFalse(names.contains("HOST_PASSWORD"))
    }

    @Test
    fun loginRequestUsesContractFieldNames() {
        val body = json.encodeToString(LoginRequest("pranay.gate", "secret"))
        assertTrue(body.contains("\"username\""))
        assertTrue(body.contains("\"password\""))
        assertTrue(body.contains("pranay.gate"))
        assertFalse(body.contains("gate123"))
    }

    @Test
    fun loginResponseParsesPranayGateUser() {
        val raw = """
            {
              "accessToken":"tok",
              "tokenType":"Bearer",
              "expiresIn":43200,
              "user":{
                "id":"U-PRANAY-GATE",
                "schoolId":"SCH-PRANAY-01",
                "role":"gate",
                "staffId":"PS-G01",
                "displayName":"Pranay Gate"
              },
              "meta":{"watermark":"DEMO"}
            }
        """.trimIndent()
        val parsed = json.decodeFromString<LoginResponse>(raw)
        assertEquals("tok", parsed.accessToken)
        assertEquals("SCH-PRANAY-01", parsed.user?.schoolId)
        assertEquals("gate", parsed.user?.role)
        assertEquals("PS-G01", parsed.user?.staffId)
        assertEquals("Pranay Gate", parsed.user?.displayName)
    }

    @Test
    fun hostJwtUserIsNotRejected() {
        val host = json.decodeFromString<MeResponse>(
            """{"id":"U-HOST","schoolId":"SCH-DEMO-01","role":"host","staffId":"H03","displayName":"Anita Joshi"}""",
        )
        assertEquals("host", host.role)
        assertEquals("Anita Joshi", host.displayName)
    }

    @Test
    fun loginResponseParsesPranayHostUser() {
        val raw = """
            {
              "accessToken":"tok-host",
              "tokenType":"Bearer",
              "expiresIn":43200,
              "user":{
                "id":"U-PRANAY-HOST",
                "schoolId":"SCH-PRANAY-01",
                "role":"host",
                "displayName":"Pranay Host"
              },
              "meta":{"watermark":"DEMO"}
            }
        """.trimIndent()
        val parsed = json.decodeFromString<LoginResponse>(raw)
        assertEquals("tok-host", parsed.accessToken)
        assertEquals("SCH-PRANAY-01", parsed.user?.schoolId)
        assertEquals("host", parsed.user?.role)
        assertEquals("Pranay Host", parsed.user?.displayName)
    }

    @Test
    fun invalidCredentialsShowsFriendlyError() {
        val error = ApiException("INVALID_CREDENTIALS", "nope", 401)
        assertEquals("Invalid username or password", LoginErrors.message(error))
    }

    @Test
    fun sessionLogoutClearsJwt() {
        val session = AuthSession()
        session.accept(
            "tok",
            MeResponse(
                id = "U-PRANAY-GATE",
                schoolId = "SCH-PRANAY-01",
                role = "gate",
                staffId = "PS-G01",
                displayName = "Pranay Gate",
            ),
        )
        assertTrue(session.isSignedIn)
        session.clear()
        assertFalse(session.isSignedIn)
        assertNull(session.accessToken)
        assertNull(session.user)
    }
}
