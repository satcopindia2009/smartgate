package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.api.LoginErrors
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorCopyTest {
    private fun e(code: String, http: Int = 400, msg: String = "x") = ApiException(code, msg, http)

    @Test
    fun contractCopyTable() {
        assertEquals("Invalid username or password", ErrorCopy.forApi(e("INVALID_CREDENTIALS", 401)))
        assertEquals("Your account is inactive. Please contact administrator.", ErrorCopy.forApi(e("ACCOUNT_INACTIVE", 403)))
        assertEquals("You do not have permission to access this page.", ErrorCopy.forApi(e("FORBIDDEN", 403)))
        assertEquals("Your session has expired. Please sign in again.", ErrorCopy.forApi(e("TOKEN_EXPIRED", 401)))
        assertEquals("Your session is not valid. Please sign in again.", ErrorCopy.forApi(e("TOKEN_INVALID", 401)))
        assertEquals("You are outside the school campus. Please move inside the campus and try again.", ErrorCopy.forApi(e("GEO_FENCE_RESTRICTED", 403)))
        assertEquals("Unable to get an accurate location. Please enable GPS and try again.", ErrorCopy.forApi(e("GPS_ACCURACY_LOW")))
        assertEquals("You have already clocked out for today.", ErrorCopy.forApi(e("ALREADY_CLOCKED_OUT", 409)))
        assertEquals("You are already checked in for today.", ErrorCopy.forApi(e("ALREADY_CHECKED_IN", 409)))
        assertEquals("Something went wrong. Please try again.", ErrorCopy.forApi(e("INTERNAL", 500)))
    }

    @Test
    fun legacyInactiveCodeStillMapped() {
        assertEquals(ErrorCopy.ACCOUNT_INACTIVE, ErrorCopy.forApi(e("USER_INACTIVE", 403, "User is inactive")))
    }

    @Test
    fun technicalTextNeverReachesTheScreen() {
        listOf(
            "Outside campus geo-fence; action blocked (geoFenceMode=restrict)",
            "Role 'host' not allowed for this action",
            "Traceback (most recent call last)",
            "https://broadband-headers-commander-drug.trycloudflare.com/v1/visits",
            "NullPointerException at com.x",
            "Cloudflare tunnel 1033",
        ).forEach {
            val out = ErrorCopy.forApi(e("SOMETHING", 400, it))
            assertEquals(it, ErrorCopy.GENERIC, out)
        }
    }

    @Test
    fun plainServerMessagesPassThrough() {
        assertEquals("Visit is not pending.", ErrorCopy.forApi(e("INVALID_STATE", 409, "Visit is not pending.")))
    }

    @Test
    fun tunnelStatusAndNetworkFailuresAreCalm() {
        assertEquals(ErrorCopy.TEMPORARILY_DOWN, ErrorCopy.forApi(e("HTTP_530", 530, "whatever")))
        assertEquals(ErrorCopy.TEMPORARILY_DOWN, ErrorCopy.forApi(e("TUNNEL_DOWN", 530)))
        assertEquals(ErrorCopy.TEMPORARILY_DOWN, ErrorCopy.forApi(e("HTTP_502", 502)))
        assertEquals(ErrorCopy.UNREACHABLE, ErrorCopy.forThrowable(IOException("Unable to resolve host \"broadband-headers-commander-drug.trycloudflare.com\"")))
        assertEquals(ErrorCopy.GENERIC, ErrorCopy.forThrowable(IllegalStateException("java.lang.Boom")))
    }

    @Test
    fun tunnelMessageHasNoHostName() {
        val msg = LoginErrors.TUNNEL_MESSAGE
        assertFalse(msg.contains("broadband", ignoreCase = true))
        assertFalse(msg.contains("cloudflare", ignoreCase = true))
        assertFalse(msg.contains("1033"))
        assertFalse(msg.contains("trycloudflare"))
        assertTrue(msg.isNotBlank())
        assertEquals(msg, LoginErrors.message(e("HTTP_530", 530)))
    }

    @Test
    fun sessionEndDetection() {
        assertTrue(ErrorCopy.isSessionEnd(e("TOKEN_EXPIRED", 401)))
        assertTrue(ErrorCopy.isSessionEnd(e("TOKEN_INVALID", 401)))
        assertFalse(ErrorCopy.isSessionEnd(e("FORBIDDEN", 403)))
    }
}
