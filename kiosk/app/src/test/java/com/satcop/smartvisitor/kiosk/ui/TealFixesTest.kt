package com.satcop.smartvisitor.kiosk.ui

import com.satcop.smartvisitor.kiosk.ui.apple.HostCall
import com.satcop.smartvisitor.kiosk.ui.face.FaceCaptureCopy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TealFixesTest {
    @Test fun faceHintHasNoInternalKey() {
        listOf(FaceCaptureCopy.LOCATION_HINT_EN, FaceCaptureCopy.LOCATION_HINT_HI).forEach {
            assertFalse(it, it.contains("guard_capture"))
            assertFalse(it, Regex("\\b[a-z]+(_[a-z0-9]+){2,}\\b").containsMatchIn(it))
            assertFalse(it, it.startsWith("EN:") || it.startsWith("HI:"))
        }
    }

    @Test fun hostMobileIsMaskedAndDialable() {
        assertEquals("XXXXXX1151", HostCall.maskedMobile("9800011151"))
        assertEquals("XXXXXX1151", HostCall.maskedMobile("+91 98000 11151"))
        assertEquals("XXXXXX1234", HostCall.maskedMobile("XXXXXX1234"))
        assertEquals("", HostCall.maskedMobile(null))
        assertEquals("+919800011151", HostCall.dialNumber("9800011151"))
        assertEquals("+919800011151", HostCall.dialNumber("919800011151"))
        assertEquals("+919800011151", HostCall.dialNumber("+91 98000 11151"))
        assertNull(HostCall.dialNumber("XXXXXX1234"))
        assertNull(HostCall.dialNumber("••••••1234"))
        assertNull(HostCall.dialNumber(""))
    }

    @Test fun hostDetailNeverPrintsRawMobile() {
        val src = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/apple/HostInboxScreen.kt").readText()
        assertFalse(src.contains("\"Mobile number\" to visit.mobile"))
        assertTrue(src.contains("HostCall.maskedMobile(visit.mobile)"))
    }

    /** Blocking OkHttp calls in the ViewModel must go through io{} (viewModelScope is Dispatchers.Main). */
    @Test fun faceAndLoginNetworkCallsLeaveMainThread() {
        val src = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/KioskViewModel.kt").readText()
            .replace(Regex("io \\{\\s+liveApi"), "io { liveApi")
        listOf("liveApi.faceVerify(", "liveApi.faceEnroll(", "liveApi.schoolMe()", "liveApi.login(").forEach { call ->
            assertTrue("$call must be wrapped in io { }", src.contains("io { $call"))
            assertFalse("$call called outside io { }", Regex("(?<!io \\{ )" + Regex.escape(call)).containsMatchIn(src))
        }
    }
}
