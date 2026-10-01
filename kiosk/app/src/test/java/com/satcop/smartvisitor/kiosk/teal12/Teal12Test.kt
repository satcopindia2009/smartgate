package com.satcop.smartvisitor.kiosk.teal12

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.ui.KioskRole
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Teal12Test {
    private fun src(p: String) = File("src/main/java/com/satcop/smartvisitor/kiosk/$p").readText()

    @Test fun clockInRolesAreTheFiveRoles() {
        for (r in listOf("guard", "gate", "gate_staff", "gate-staff", "security", "security_head", "security-head"))
            assertTrue(r, FaceClockInLogic.requiresClockIn(r))
    }

    @Test fun caseAndSpaceVariants() {
        assertTrue(FaceClockInLogic.requiresClockIn(" Guard "))
        assertTrue(FaceClockInLogic.requiresClockIn("GATE-STAFF"))
        assertTrue(FaceClockInLogic.requiresClockIn("Security_Head"))
        assertTrue(FaceClockInLogic.requiresClockIn(" gate "))
    }

    @Test fun hostAdminAndUnknownStayOut() {
        for (r in listOf("host", "HOST", "admin", "Admin", "", "escort", "sh", "guardian", "gatekeeper"))
            assertFalse(r, FaceClockInLogic.requiresClockIn(r))
        assertFalse(FaceClockInLogic.requiresClockIn(null))
    }

    @Test fun homeMapping() {
        assertEquals(KioskRole.GATE, KioskRole.fromJwt("gate_staff"))
        assertEquals(KioskRole.GATE, KioskRole.fromJwt("Gate-Staff"))
        assertEquals(KioskRole.GUARD, KioskRole.fromJwt("security"))
        assertEquals(KioskRole.GUARD, KioskRole.fromJwt("guard"))
        assertEquals(KioskRole.HOST, KioskRole.fromJwt("host"))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt("security_head"))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt("admin"))
    }

    @Test fun webOnlyRulingD1Unchanged() {
        assertTrue(KioskRole.isWebOnly("admin"))
        assertTrue(KioskRole.isWebOnly("security_head"))
        assertFalse(KioskRole.isWebOnly("gate"))
        assertFalse(KioskRole.isWebOnly("security"))
    }

    @Test fun verifyOnlyFallbackOnlyForRoleRefusals() {
        assertTrue(FaceClockInLogic.verifyOnlyFallback("gate", ApiException("FORBIDDEN", "no", 403)))
        assertTrue(FaceClockInLogic.verifyOnlyFallback("gate_staff", ApiException("NOT_FOUND", "no", 404)))
        assertTrue(FaceClockInLogic.verifyOnlyFallback("security", ApiException("ROLE_NOT_ALLOWED", "no", 403)))
        // guard never falls back
        assertFalse(FaceClockInLogic.verifyOnlyFallback("guard", ApiException("FORBIDDEN", "no", 403)))
        // location / face / already-in / network errors are not role refusals
        assertFalse(FaceClockInLogic.verifyOnlyFallback("gate", ApiException("GEO_FENCE_RESTRICTED", "x", 403)))
        assertFalse(FaceClockInLogic.verifyOnlyFallback("gate", ApiException("FORBIDDEN", "x", 403, mapOf("reasonCode" to "OUTSIDE_FENCE"))))
        assertFalse(FaceClockInLogic.verifyOnlyFallback("gate", ApiException("ALREADY_CHECKED_IN", "x", 409)))
        assertFalse(FaceClockInLogic.verifyOnlyFallback("gate", java.io.IOException("offline")))
    }

    @Test fun verifiedCardLabels() {
        val r = FaceClockInLogic.verifiedResult("2026-10-01T17:09:41+05:30", null)
        assertTrue(r.verifyOnly)
        assertEquals("Face verified", ClockInLogic.resultTitleOf(r))
        assertEquals("Face verified", ClockInLogic.resultHeader(r))
        assertEquals("05:09 pm", r.time)
        assertEquals("Checked In!", ClockInLogic.resultTitleOf(ClockInLogic.resultFrom(com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode.CHECK_IN, com.satcop.smartvisitor.kiosk.data.model.AttendanceRow())))
    }

    @Test fun wiringUsesOneSharedHelper() {
        assertTrue(src("data/api/LiveVisitorApi.kt").contains("FaceClockInLogic.requiresClockIn(parsed.user?.role)"))
        assertTrue(src("guardpatrol/data/LiveGuardPatrolApi.kt").contains("FaceClockInLogic.requiresClockIn(parsed.user?.role)"))
        assertTrue(src("ui/KioskViewModel.kt").contains("FaceClockInLogic.requiresClockIn(live.user.role)"))
        assertFalse(src("ui/KioskViewModel.kt").contains("isGuardRole"))
    }
}
