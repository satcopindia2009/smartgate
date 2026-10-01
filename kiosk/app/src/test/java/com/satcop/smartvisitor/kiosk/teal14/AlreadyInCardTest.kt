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
}
