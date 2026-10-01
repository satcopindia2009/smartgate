package com.satcop.smartvisitor.kiosk.teal14

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1078: check-in returns ALREADY_CHECKED_IN -> the Checked In! card comes from today's existing row. */
class AlreadyInCardTest {
    private fun err(code: String, status: Int = 409, d: Map<String, String> = emptyMap()) = ApiException(code, "x", status, d)
    private val row = AttendanceRow(id = "GA-0004", checkInAt = "2026-10-01T09:05:00+05:30", gateName = "Main Gate")

    @Test fun alreadyInUsesTodayRow() {
        val r = FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = row))
        assertEquals("GA-0004", r?.id)
        assertEquals("Main Gate", r?.gateName)
    }

    @Test fun reasonCodeFormIsAlreadyIn() {
        val e = err("CONFLICT", 409, mapOf("reasonCode" to "ALREADY_CLOCKED_IN"))
        assertTrue(FaceClockInLogic.alreadyIn(e))
        assertEquals("GA-0004", FaceClockInLogic.rowForAlreadyIn(e, TodayAttendance(attendance = row))?.id)
    }

    @Test fun todayFetchFailsFallsBackToErrorDetails() {
        val e = err("ALREADY_CHECKED_IN", 409, mapOf("attendance.id" to "GA-0009", "attendance.checkInAt" to "2026-10-01T08:00:00+05:30", "gateName" to "Gate 2"))
        val r = FaceClockInLogic.rowForAlreadyIn(e, null)
        assertEquals("GA-0009", r?.id)
        assertEquals("Gate 2", r?.gateName)
    }

    @Test fun todayEmptyRowFallsBackToDetails() {
        val e = err("ALREADY_CHECKED_IN", 409, mapOf("id" to "GA-0010"))
        assertEquals("GA-0010", FaceClockInLogic.rowForAlreadyIn(e, TodayAttendance(attendance = AttendanceRow()))?.id)
    }

    @Test fun noRowAnywhereMeansNoCardButStillIn() {
        val e = err("ALREADY_CHECKED_IN")
        assertTrue(FaceClockInLogic.alreadyIn(e))
        assertNull(FaceClockInLogic.rowForAlreadyIn(e, null))
        assertNull(FaceClockInLogic.rowForAlreadyIn(e, TodayAttendance(attendance = null)))
    }

    @Test fun clockedOutHasNoCheckedInCard() {
        val e = err("ALREADY_CLOCKED_OUT")
        assertTrue(FaceClockInLogic.alreadyIn(e))
        assertNull(FaceClockInLogic.rowForAlreadyIn(e, TodayAttendance(attendance = row)))
    }

    @Test fun rowWithCheckOutGivesNoCard() {
        val done = row.copy(checkOutAt = "2026-10-01T17:00:00+05:30")
        assertNull(FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = done)))
    }

    @Test fun otherErrorsGiveNoRow() {
        assertNull(FaceClockInLogic.rowForAlreadyIn(err("OUTSIDE_GEOFENCE", 403), TodayAttendance(attendance = row)))
        assertNull(FaceClockInLogic.rowForAlreadyIn(RuntimeException("x"), TodayAttendance(attendance = row)))
    }

    @Test fun verifyOnlyNeverForAlreadyIn() {
        for (role in listOf("gate", "gate_staff", "security", "gate-staff")) {
            assertFalse(FaceClockInLogic.verifyOnlyFallback(role, err("ALREADY_CHECKED_IN", 404)))
            assertFalse(FaceClockInLogic.verifyOnlyFallback(role, err("ALREADY_CLOCKED_OUT", 405)))
            assertFalse(FaceClockInLogic.verifyOnlyFallback(role, err("CONFLICT", 404, mapOf("reasonCode" to "ALREADY_CLOCKED_IN"))))
        }
    }

    @Test fun verifyOnlyOnlyFor404_405AndRefusalWithoutReasonCode() {
        assertTrue(FaceClockInLogic.verifyOnlyFallback("gate", err("NOT_FOUND", 404)))
        assertTrue(FaceClockInLogic.verifyOnlyFallback("gate", err("X", 405)))
        assertTrue(FaceClockInLogic.verifyOnlyFallback("security", err("ROLE_NOT_ALLOWED", 403)))
        assertFalse(FaceClockInLogic.verifyOnlyFallback("gate", err("FORBIDDEN", 403, mapOf("reasonCode" to "OUTSIDE_GEOFENCE"))))
        assertFalse(FaceClockInLogic.verifyOnlyFallback("guard", err("NOT_FOUND", 404)))
        assertFalse(FaceClockInLogic.verifyOnlyFallback("gate", err("VALIDATION", 400)))
        assertNotNull(FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = row)))
    }

    // ---- 1078b: business date + nested details ----
    private val bd = java.time.LocalDate.parse("2026-10-01")

    @Test fun nestedDetailsFlattenAsAttendanceDot() {
        val json = """{"error":{"code":"ALREADY_CHECKED_IN","message":"m","details":{"reasonCode":"ALREADY_CLOCKED_IN","attendance":{"id":"GA-0012","dutyDate":"2026-10-01","checkInAt":"2026-10-01T09:05:00+05:30","gateName":"Main Gate","attendanceStatus":"PRESENT","flags":["A"]}}}}"""
        val env = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
        val details = com.satcop.smartvisitor.kiosk.data.api.ErrorDetails.flatten((env["error"] as kotlinx.serialization.json.JsonObject)["details"])
        assertEquals("GA-0012", details["attendance.id"])
        assertEquals("Main Gate", details["attendance.gateName"])
        val r = FaceClockInLogic.rowFromDetails(ApiException("ALREADY_CHECKED_IN", "m", 409, details))
        assertEquals("GA-0012", r?.id); assertEquals("2026-10-01", r?.dutyDate); assertEquals("PRESENT", r?.attendanceStatus)
    }

    @Test fun alreadyCheckedInPrefersDetailsOverToday() {
        val e = err("ALREADY_CHECKED_IN", 409, mapOf("attendance.id" to "GA-0020", "attendance.dutyDate" to "2026-10-01", "attendance.checkInAt" to "2026-10-01T08:00:00+05:30"))
        val r = FaceClockInLogic.rowForAlreadyIn(e, TodayAttendance(attendance = row.copy(dutyDate = "2026-10-01")), bd)
        assertEquals("GA-0020", r?.id)
    }

    @Test fun staleDetailsRowFallsBackToTodayRow() {
        val e = err("ALREADY_CHECKED_IN", 409, mapOf("attendance.id" to "GA-0004", "attendance.dutyDate" to "2026-09-18"))
        val fresh = row.copy(id = "GA-0030", dutyDate = "2026-10-01")
        assertEquals("GA-0030", FaceClockInLogic.rowForAlreadyIn(e, TodayAttendance(attendance = fresh), bd)?.id)
    }

    @Test fun staleOpenRowFromPreviousDayGivesNoCard() {
        val e = err("ALREADY_CHECKED_IN", 409, mapOf("attendance.id" to "GA-0004", "attendance.dutyDate" to "2026-09-18", "attendance.checkInAt" to "2026-09-18T09:00:00+05:30"))
        assertNull(FaceClockInLogic.rowForAlreadyIn(e, null, bd))
        val stale = AttendanceRow(id = "GA-0004", dutyDate = "2026-09-18", checkInAt = "2026-09-18T09:00:00+05:30")
        assertNull(FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = stale), bd))
    }

    @Test fun staleByCheckInAtWhenNoDutyDate() {
        val stale = AttendanceRow(id = "GA-0004", checkInAt = "2026-09-30T23:10:00+05:30")
        assertNull(FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = stale), bd))
        val ok = AttendanceRow(id = "GA-0031", checkInAt = "2026-10-01T06:10:00+05:30")
        assertEquals("GA-0031", FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = ok), bd)?.id)
    }

    @Test fun todaysTopLevelDutyDateAppliesToRowWithoutOne() {
        val r = AttendanceRow(id = "GA-0004", checkInAt = null)
        assertNull(FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = r, dutyDate = "2026-09-18"), bd))
        assertEquals("GA-0004", FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = r, dutyDate = "2026-10-01"), bd)?.id)
    }

    @Test fun rowWithoutAnyDateKeepsCurrentBehaviour() {
        val r = AttendanceRow(id = "GA-0040")
        assertEquals("GA-0040", FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = r), bd)?.id)
        assertFalse(FaceClockInLogic.isStaleRow(r, bd))
        assertFalse(FaceClockInLogic.isStaleRow(row, null))
    }

    @Test fun businessDateCutoffUsed() {
        // 00:30 IST on 2 Oct with a 04:00 cutoff is still business date 1 Oct
        val now = java.time.Instant.parse("2026-10-01T19:00:00Z") // 00:30 IST 2 Oct
        val d = com.satcop.smartvisitor.kiosk.ui.guardhome.GuardGeoLogic.businessDate(now, "04:00")
        assertEquals(java.time.LocalDate.parse("2026-10-01"), d)
        assertEquals("GA-0031", FaceClockInLogic.rowForAlreadyIn(err("ALREADY_CHECKED_IN"), TodayAttendance(attendance = AttendanceRow(id = "GA-0031", dutyDate = "2026-10-01")), d)?.id)
    }

    @Test fun clockedOutWithFullRowInDetailsStillNoCard() {
        val e = err("ALREADY_CLOCKED_OUT", 409, mapOf("attendance.id" to "GA-0050", "attendance.dutyDate" to "2026-10-01", "attendance.checkInAt" to "2026-10-01T08:00:00+05:30", "attendance.checkOutAt" to "2026-10-01T16:00:00+05:30"))
        assertTrue(FaceClockInLogic.alreadyIn(e))
        assertNull(FaceClockInLogic.rowForAlreadyIn(e, TodayAttendance(attendance = row), bd))
        assertNull(FaceClockInLogic.rowForAlreadyIn(e, null, bd))
    }
}
