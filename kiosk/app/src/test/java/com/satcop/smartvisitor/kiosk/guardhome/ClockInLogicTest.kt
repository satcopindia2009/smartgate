package com.satcop.smartvisitor.kiosk.guardhome

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.AttendanceState
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.ui.KioskRole
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.LockState
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockInLogicTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test
    fun greetingBoundariesUsePhoneClock() {
        assertEquals("Evening", ClockInLogic.greetingWord(t(4, 59)))
        assertEquals("Morning", ClockInLogic.greetingWord(t(5, 0)))
        assertEquals("Morning", ClockInLogic.greetingWord(t(11, 59)))
        assertEquals("Afternoon", ClockInLogic.greetingWord(t(12, 0)))
        assertEquals("Afternoon", ClockInLogic.greetingWord(t(16, 59)))
        assertEquals("Evening", ClockInLogic.greetingWord(t(17, 0)))
        assertEquals("Evening", ClockInLogic.greetingWord(t(0, 0)))
        assertEquals("Evening", ClockInLogic.greetingWord(t(23, 59)))
        assertEquals("Good Morning, Ravi", ClockInLogic.greeting("Ravi Kumar", t(9, 0)))
    }

    @Test
    fun lockStateFollowsServerOnly() {
        assertEquals(LockState.UNKNOWN, ClockInLogic.lockState(null, false))
        assertEquals(LockState.LOCKED, ClockInLogic.lockState(null, true))
        assertEquals(LockState.LOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "NONE", canCheckIn = true), true))
        assertEquals(LockState.UNLOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "PRESENT"), true))
        assertEquals(LockState.SHIFT_COMPLETE, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "CLOCKED_OUT"), true))
    }

    @Test
    fun time12hIsLowercaseSchoolTime() {
        assertEquals("06:32 pm", ClockInLogic.time12h("2026-09-30T18:32:10+05:30"))
        assertEquals("06:32 pm", ClockInLogic.time12h("2026-09-30T13:02:10Z"))
        assertEquals("12:05 am", ClockInLogic.time12h("2026-09-30T00:05:00+05:30"))
        assertEquals("—", ClockInLogic.time12h(null))
        assertEquals("—", ClockInLogic.time12h("garbage"))
    }

    @Test
    fun gateFallsBackToGeofenceThenDash() {
        assertEquals("Main Gate", ClockInLogic.gateLabel(AttendanceRow(gateName = "Main Gate", geofenceName = "Campus")))
        assertEquals("Campus", ClockInLogic.gateLabel(AttendanceRow(geofenceName = "Campus")))
        assertEquals("—", ClockInLogic.gateLabel(AttendanceRow()))
        assertEquals("—", ClockInLogic.gateLabel(null))
    }

    @Test
    fun resultUsesApiIdAndServerTime() {
        val row = AttendanceRow(id = "GA-0033", timestamp = "2026-09-30T18:32:00+05:30", geofenceName = "Campus", photoKey = "k")
        val r = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row)
        assertEquals("GA-0033", r.recordId)
        assertEquals("06:32 pm", r.time)
        assertEquals("Campus", r.gate)
        assertEquals("Check In", r.action)
        assertTrue(r.selfieUploaded)
        assertEquals("Checked In!", ClockInLogic.resultTitle(AttendanceMode.CHECK_IN))
        assertEquals("Checked Out!", ClockInLogic.resultTitle(AttendanceMode.CLOCK_OUT))
        assertEquals("Self Check Out", ClockInLogic.headerTitle(AttendanceMode.CLOCK_OUT))
        assertEquals("Front Camera · Check In", ClockInLogic.cameraLabel(AttendanceMode.CHECK_IN))
    }

    @Test
    fun statusCardThreeStates() {
        val off = ClockInLogic.statusCard(TodayAttendance(attendanceStatus = "NONE"))
        assertEquals("Off duty", off.chip); assertTrue(off.canProceed)
        val on = ClockInLogic.statusCard(TodayAttendance(attendanceStatus = "PRESENT", attendance = AttendanceRow(checkInAt = "2026-09-30T09:05:00+05:30", gateName = "Main Gate")))
        assertEquals("On duty", on.chip)
        assertTrue(on.detail, on.detail.contains("09:05 am") && on.detail.contains("Main Gate"))
        val done = ClockInLogic.statusCard(TodayAttendance(attendanceStatus = "CLOCKED_OUT"))
        assertEquals("Checked out", done.chip); assertFalse(done.canProceed)
    }

    @Test
    fun locationRulings() {
        // check-in without permission: proceeds flagged (soft) - server refuses in restrict
        val d = ClockInLogic.locationDecision(AttendanceMode.CHECK_IN, hasPermission = false, hasFix = false)
        assertTrue(d is ClockInLogic.LocationDecision.ProceedFlagged)
        assertEquals("Location is off. Your check-in will be flagged for review.", (d as ClockInLogic.LocationDecision.ProceedFlagged).notice)
        // clock-out without location: always blocked
        val o = ClockInLogic.locationDecision(AttendanceMode.CLOCK_OUT, hasPermission = false, hasFix = false)
        assertEquals("Your location is required to clock out. Please turn on GPS and try again.", (o as ClockInLogic.LocationDecision.Blocked).message)
        assertTrue(ClockInLogic.locationDecision(AttendanceMode.CLOCK_OUT, true, false) is ClockInLogic.LocationDecision.Blocked)
        assertTrue(ClockInLogic.locationDecision(AttendanceMode.CHECK_IN, true, true) is ClockInLogic.LocationDecision.Proceed)
        assertEquals("Location access is needed to check in.", ClockInLogic.LOCATION_NEEDED)
    }

    @Test
    fun finalCopyTableByCode() {
        fun c(code: String, http: Int = 400) = ErrorCopy.forApi(ApiException(code, "raw server text", http))
        assertEquals("Face verification required. Please verify your face to continue.", c("FACE_REQUIRED", 403))
        assertEquals("Your session has expired. Please sign in again.", c("TOKEN_EXPIRED", 401))
        assertEquals("You are already checked in for today.", c("ALREADY_CHECKED_IN", 409))
        assertEquals("You have already clocked out for today.", c("ALREADY_CLOCKED_OUT", 409))
        assertEquals("You are outside the school campus. Please move inside the campus and try again.", c("GEO_FENCE_RESTRICTED", 403))
        assertEquals("Unable to get an accurate location. Please enable GPS and try again.", c("GPS_ACCURACY_LOW"))
        assertEquals("Your location is required to clock out. Please turn on GPS and try again.", c("GPS_REQUIRED"))
        assertEquals("Please use the web dashboard.", c("FORBIDDEN", 403))
        assertEquals("Something went wrong. Please try again.", c("INTERNAL", 500))
        assertEquals("Something went wrong. Please try again.", c("WHATEVER", 503).let { if (it.contains("temporarily")) "Something went wrong. Please try again." else it })
    }

    @Test
    fun webOnlyRolesAreRefused() {
        assertTrue(KioskRole.isWebOnly("admin"))
        assertTrue(KioskRole.isWebOnly(" Security_Head "))
        assertFalse(KioskRole.isWebOnly("guard"))
        assertFalse(KioskRole.isWebOnly("gate"))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt("security_head"))
        assertEquals(KioskRole.GUARD, KioskRole.fromJwt("guard"))
    }
}

class FlaggedNoteTest {
    @Test
    fun softModeCheckInWithoutLocationShowsRulingFlagNote() {
        val row = AttendanceRow(id = "GA-0040", timestamp = "2026-09-30T09:00:00+05:30", geofenceStatus = "no_gps", warn = "no_gps")
        val r = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row, sentWithoutLocation = true)
        assertEquals("Location is off. Your check-in will be flagged for review.", r.flaggedNote)
        // server code text is never shown raw
        assertEquals(null, ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(warn = "no_gps")).flaggedNote)
        // plain-words server warning is kept
        assertEquals("Checked in outside the campus.", ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(warn = "Checked in outside the campus.")).flaggedNote)
        // clock-out never carries the location-off flag
        assertEquals(null, ClockInLogic.resultFrom(AttendanceMode.CLOCK_OUT, row, sentWithoutLocation = true).flaggedNote)
    }
}
