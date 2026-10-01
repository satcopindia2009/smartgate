package com.satcop.smartvisitor.kiosk.teal10

import com.satcop.smartvisitor.kiosk.data.geo.CaptureStamp
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.GeofenceInfo
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.GeoMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardGeoLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.LockState
import java.io.File
import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Teal10Test {
    private fun src(p: String) = File("src/main/java/com/satcop/smartvisitor/kiosk/$p").readText()

    // 3 business date
    @Test fun businessDateFollowsTheCutoff() {
        val t = Instant.parse("2026-10-01T20:00:00Z") // 01:30 IST on 2 Oct
        assertEquals(LocalDate.parse("2026-10-02"), GuardGeoLogic.businessDate(t, null))
        assertEquals(LocalDate.parse("2026-10-02"), GuardGeoLogic.businessDate(t, "00:00"))
        assertEquals(LocalDate.parse("2026-10-01"), GuardGeoLogic.businessDate(t, "04:00"))
        assertEquals(LocalDate.parse("2026-10-02"), GuardGeoLogic.businessDate(Instant.parse("2026-10-01T23:00:00Z"), "04:00")) // 04:30 IST
        assertEquals(LocalDate.parse("2026-10-02"), GuardGeoLogic.businessDate(t, "garbage"))
    }

    @Test fun oldShiftCompleteIsDroppedAndNewDayShowsFaceClockIn() {
        val bd = LocalDate.parse("2026-10-02")
        assertEquals(LockState.LOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "CLOCKED_OUT", dutyDate = "2026-10-01"), true, bd))
        assertEquals(LockState.SHIFT_COMPLETE, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "CLOCKED_OUT", dutyDate = "2026-10-02"), true, bd))
        assertEquals(LockState.LOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "2026-10-01"), true, bd))
        assertEquals(LockState.UNLOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "2026-10-02"), true, bd))
    }

    // 5 mock location
    @Test fun mockRule() {
        assertEquals(GuardGeoLogic.MOCK_BLOCKED, GuardGeoLogic.mockBlock(AttendanceMode.CHECK_IN, GeoMode.RESTRICT, true))
        assertNull(GuardGeoLogic.mockBlock(AttendanceMode.CHECK_IN, GeoMode.SOFT, true))
        assertNull(GuardGeoLogic.mockBlock(AttendanceMode.CHECK_IN, GeoMode.UNKNOWN, true))
        assertEquals(GuardGeoLogic.MOCK_BLOCKED, GuardGeoLogic.mockBlock(AttendanceMode.CLOCK_OUT, GeoMode.SOFT, true))
        assertEquals(GuardGeoLogic.MOCK_BLOCKED, GuardGeoLogic.mockBlock(AttendanceMode.CLOCK_OUT, GeoMode.OFF, true))
        assertNull(GuardGeoLogic.mockBlock(AttendanceMode.CLOCK_OUT, GeoMode.RESTRICT, false))
    }

    @Test fun mockCopyIsExact() {
        assertEquals("Fake location detected. Turn off mock-location apps and try again.", GuardGeoLogic.MOCK_BLOCKED)
        assertEquals("Mock location detected. Your check-in will be flagged for review.", GuardGeoLogic.MOCK_LOCATION_NOTE)
        assertEquals("नकली लोकेशन मिली है। मॉक-लोकेशन ऐप बंद करें और दोबारा कोशिश करें।", GuardGeoLogic.Hi.MOCK_BLOCKED)
        assertEquals("मॉक लोकेशन मिली है। आपका चेक इन समीक्षा के लिए चिह्नित किया जाएगा।", GuardGeoLogic.Hi.MOCK_LOCATION_NOTE)
    }

    @Test fun faceFlowSendsIsMockWhenFlagged() {
        val r = FaceClockInLogic.buildCheckIn("B", CaptureStamp("x", 18.5, 73.8, 5f, false, isMock = true), Instant.parse("2026-10-01T10:00:00Z"), "a")
        assertEquals(true, r.isMock)
        assertNull(FaceClockInLogic.buildCheckIn("B", CaptureStamp("x", 18.5, 73.8, 5f, false), Instant.parse("2026-10-01T10:00:00Z"), "a").isMock)
    }

    // 6 clock-out banner shows distance like check-in
    @Test fun clockOutOutsideShowsFriendlyNoteWithDistance() {
        val row = AttendanceRow(id = "1", outGeofenceStatus = "outside", outGeofence = GeofenceInfo(status = "outside", distanceM = 340.4, radiusM = 192.0),
            warn = "Outside the school campus (soft mode); flagged.")
        val r = ClockInLogic.resultFrom(AttendanceMode.CLOCK_OUT, row)
        assertEquals(GuardGeoLogic.CLOCK_OUT_OUTSIDE_NOTE, r.flaggedNote)
        assertEquals("You were about 350 m from the campus; the limit is 192 m.", r.flaggedDetail)
        // inside: no note
        assertNull(ClockInLogic.resultFrom(AttendanceMode.CLOCK_OUT, AttendanceRow(outGeofenceStatus = "inside")).flaggedNote)
    }

    // 1 + 2 + 4 + 6 wiring (source level)
    @Test fun checkInCardWiring() {
        val vm = src("ui/KioskViewModel.kt")
        assertTrue(vm.contains("clockInRow = ci.getOrNull()"))
        assertTrue(src("ui/KioskApp.kt").contains("initialCheckInRow = state.clockInRow"))
        val screens = src("ui/guardhome/ClockFlowScreens.kt")
        assertTrue(screens.contains("delay(4_000L); onDone()"))
        assertTrue(screens.contains("ClockInLogic.resultHeader(result)"))
        assertTrue(screens.contains("controller.showCheckInResult(initialCheckInRow)"))
    }

    @Test fun faceCardIsNeutral() {
        assertEquals("Verify your face to clock in", GuardGeoLogic.FACE_CARD_NEUTRAL)
        val s = src("ui/face/FaceLoginScreen.kt")
        assertFalse(s.contains("Face not enrolled"))
        assertFalse(src("ui/GuardAsapScreens.kt").contains("\"Face not enrolled\""))
    }

    @Test fun backDoesNothingOnLockAndFaceScreens() {
        assertTrue(src("ui/guardhome/ClockFlowScreens.kt").contains("BackHandler(enabled = panel == null && result == null && lock != LockState.UNLOCKED) { }"))
        assertTrue(src("ui/KioskViewModel.kt").contains("s.gateStage == GateStage.FACE_PENDING) return true"))
    }

    @Test fun lowItemsInScreens() {
        val s = src("ui/guardhome/ClockFlowScreens.kt")
        assertFalse(s.contains("Server shift text only; hidden when the guard has no shift."))
        assertTrue(s.contains("Icons.Outlined.CloudUpload"))
        assertFalse(s.contains("Text(\"\${i + 1}\""))
        assertTrue(s.contains("hintAfterMs = SystemClock.elapsedRealtime()"))
        assertTrue(s.contains("GuardOutlineButton(GuardCopy.RETRY"))
    }

    @Test fun patrolCardIsHonest() {
        assertTrue(src("ui/guardhome/GuardHomeScreen.kt").contains("if (noRounds) GuardTodayLogic.NO_ROUNDS"))
    }

    @Test fun cutoffParsesFromSchool() {
        val s = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }.decodeFromString<com.satcop.smartvisitor.kiosk.data.model.School>(
            """{"id":"S","name":"n","timezone":"Asia/Calcutta","guardSessionCutoff":"00:00","x":1}""")
        assertEquals("00:00", s.guardSessionCutoff)
        assertNotNull(s)
    }
}
