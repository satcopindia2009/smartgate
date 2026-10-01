package com.satcop.smartvisitor.kiosk.teal9

import com.satcop.smartvisitor.kiosk.data.geo.CaptureStamp
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Teal9Test {
    private val now = Instant.parse("2026-10-01T10:00:00Z")

    @Test fun checkInReusesTheFacePhotoAndGps() {
        val r = FaceClockInLogic.buildCheckIn("B64FACE", CaptureStamp("x", 18.5, 73.8, 12f, false), now, "att-1")
        assertEquals("B64FACE", r.imageBase64)
        assertEquals(18.5, r.lat!!, 0.0); assertEquals(73.8, r.lng!!, 0.0); assertEquals(12.0, r.accuracyM!!, 0.0)
        assertFalse(r.gpsMissing)
        assertEquals("2026-10-01T10:00:00Z", r.capturedAt)
    }

    @Test fun noGpsGoesAsGpsMissingNeverHalfAPair() {
        val r = FaceClockInLogic.buildCheckIn("B", CaptureStamp("x", lat = 18.5, lng = null, gpsMissing = true), now, "a")
        assertTrue(r.gpsMissing); assertNull(r.lat); assertNull(r.lng); assertNull(r.accuracyM)
    }

    @Test fun guardRoleAlwaysFaceChecked() {
        assertTrue(FaceClockInLogic.requiresClockIn("guard")); assertTrue(FaceClockInLogic.isGuardRole(" Guard "))
        assertFalse(FaceClockInLogic.requiresClockIn("host")); assertFalse(FaceClockInLogic.isGuardRole(null))
    }

    @Test fun alreadyInOpensTheApp() {
        assertTrue(FaceClockInLogic.alreadyIn(ApiException("ALREADY_CHECKED_IN", "x", 409)))
        assertTrue(FaceClockInLogic.alreadyIn(ApiException("ALREADY_CLOCKED_OUT", "x", 409)))
        assertTrue(FaceClockInLogic.alreadyIn(ApiException("CONFLICT", "x", 409, mapOf("reasonCode" to "ALREADY_CLOCKED_IN"))))
        assertFalse(FaceClockInLogic.alreadyIn(ApiException("GEO_FENCE_RESTRICTED", "x", 403)))
        assertFalse(FaceClockInLogic.alreadyIn(java.io.IOException("down")))
    }

    @Test fun failuresKeepTheGuardOutWithAReason() {
        val outside = ApiException("GEO_FENCE_RESTRICTED", "Outside", 403, mapOf("distanceM" to "341", "radiusM" to "192"))
        assertEquals("You are about 350 m from the campus; the limit is 192 m. Move inside and try again.", FaceClockInLogic.checkInFailureMessage(outside, false))
        assertEquals("You are outside the school campus. Please move inside the campus and try again.",
            FaceClockInLogic.checkInFailureMessage(ApiException("GEO_FENCE_RESTRICTED", "Outside", 403), false))
        assertEquals(ClockInLogic.LOCATION_NEEDED, FaceClockInLogic.checkInFailureMessage(ApiException("LOCATION_REQUIRED", "gps", 403), true))
        assertEquals("Can't reach the server right now. Check your internet connection and try again.",
            FaceClockInLogic.checkInFailureMessage(java.io.IOException("x"), false))
    }

    @Test fun resultHeaderMatchesTheAction() {
        val inRes = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(id = "1", gateName = "Main Gate"))
        val outRes = ClockInLogic.resultFrom(AttendanceMode.CLOCK_OUT, AttendanceRow(id = "1"))
        assertEquals("Self Check In", ClockInLogic.resultHeader(inRes))
        assertEquals("Self Check Out", ClockInLogic.resultHeader(outRes))
    }

    @Test fun gateNameComesFromGateNameNotAnId() {
        assertEquals("Main Gate", ClockInLogic.gateLabel(AttendanceRow(gateName = "Main Gate")))
        assertEquals("Main Gate", ClockInLogic.gateLabel(AttendanceRow(gateName = null), fallbackGateName = "Main Gate"))
        assertEquals("Main Gate", ClockInLogic.gateLabel(AttendanceRow(gateName = "—"), fallbackGateName = "Main Gate"))
        assertEquals("—", ClockInLogic.gateLabel(AttendanceRow(gateId = "G-MAIN")))
        assertFalse(File("src/main/java/com/satcop/smartvisitor/kiosk/ui/guardhome/ClockFlowScreens.kt").readText().contains("Gate No"))
    }

    @Test fun faceStepWiresCheckInWithSamePhoto() {
        val vm = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/KioskViewModel.kt").readText()
        assertTrue(vm.contains("FaceClockInLogic.buildCheckIn(\n                    b64, stamp"))
        assertTrue(vm.contains("liveApi.attendanceCheckIn(req)"))
        val api = File("src/main/java/com/satcop/smartvisitor/kiosk/data/api/LiveVisitorApi.kt").readText()
        assertTrue(api.contains("parsed.faceRequired || isGuard"))
    }
}
