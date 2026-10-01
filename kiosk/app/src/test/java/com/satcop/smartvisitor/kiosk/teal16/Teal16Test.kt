package com.satcop.smartvisitor.kiosk.teal16

import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.guardpatrol.data.PatrolCopy
import com.satcop.smartvisitor.kiosk.ui.duty.DutyArea
import com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeApi
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeController
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1080 (TEAL-16): Clock out on every home, no gate calls on patrol-only, plain Patrol tab text. */
class Teal16Test {
    private fun src(p: String) = File("src/main/java/com/satcop/smartvisitor/kiosk/$p").readText()

    // ---- 1. Clock out everywhere ----
    @Test fun gateHomeAndProfileHaveClockOut() {
        val g = src("ui/apple/GateTodayScreen.kt")
        assertTrue(g.contains("onClockOut: (() -> Unit)? = null"))
        // Home (GateHome) and Profile (GateProfile) both draw the button
        assertEquals(2, Regex("SgDangerOutlineButton\\(\"Clock out\"").findAll(g).count())
        assertTrue(g.contains("onClockOut = onClockOut,"))
    }

    @Test fun clockOutIsNeverTiedToTheShiftSwitch() {
        val g = src("ui/apple/GateTodayScreen.kt")
        val clockLines = g.lines().filter { it.contains("\"Clock out\"") }
        assertTrue(clockLines.isNotEmpty())
        clockLines.forEach { assertFalse(it, it.contains("newActionsEnabled")) }
        // the shift notice only switches NEW actions; a clock out is not one
        assertTrue(DutyLogic.newActionsEnabled(DutyArea.GATE, mapOf(DutyArea.GATE to "x")).not())
    }

    @Test fun kioskAppWiresClockOutForGateAndPatrol() {
        val app = src("ui/KioskApp.kt")
        assertTrue(app.contains("onClockOut = onClockOut,")) // gate desk
        assertEquals(3, Regex("AttendanceMode\\.CLOCK_OUT").findAll(app).count()) // gate desk, patrol Settings, no-duty
    }

    @Test fun patrolSettingsTabHasClockOut() {
        val a = src("ui/apple/GuardTodayScreen.kt")
        assertTrue(a.contains("AppleCell(\"Clock out\""))
        assertTrue(src("guardpatrol/ui/GuardPatrolScreens.kt").contains("onClockOut = onClockOut,"))
        // Guard Today attendance card already has it (both-duties and patrol-only)
        assertTrue(src("ui/guardhome/GuardHomeScreen.kt").contains("SgDangerOutlineButton(\"Clock out\""))
    }

    // ---- 2. no gate calls on a patrol-only home ----
    @Test fun gateCallsOnlyWithGateDuty() {
        assertTrue(DutyLogic.gateCallsAllowed(setOf(DutyArea.GATE)))
        assertTrue(DutyLogic.gateCallsAllowed(setOf(DutyArea.GATE, DutyArea.PATROL)))
        assertFalse(DutyLogic.gateCallsAllowed(setOf(DutyArea.PATROL)))
        assertFalse(DutyLogic.gateCallsAllowed(emptySet()))
        assertTrue(DutyLogic.gateCallsAllowed(null)) // not read yet, as 1079
    }

    private class Api : GuardHomeApi {
        var visitCalls = 0
        var summaryCalls = 0
        override fun attendanceToday() = TodayAttendance()
        override fun checkIn(req: AttendanceRequest) = AttendanceRow()
        override fun clockOut(req: AttendanceRequest) = AttendanceRow()
        override fun visitsBetween(dateFrom: String, dateTo: String, q: String?): List<VisitOut> { visitCalls++; return emptyList() }
        override fun todaySummary(): GuardTodaySummary { summaryCalls++; return GuardTodaySummary() }
    }

    private fun controller(api: Api) = GuardHomeController(
        scope = CoroutineScope(Dispatchers.Unconfined), api = api, io = Dispatchers.Unconfined,
        nowElapsedMs = { 1L }, nowInstant = { Instant.parse("2026-10-02T01:00:00Z") },
    )

    @Test fun patrolOnlyRefreshDoesNotReadVisits() {
        val api = Api(); val c = controller(api)
        c.gateCalls = false
        c.refresh()
        assertEquals(0, api.visitCalls)
        assertTrue(c.state.value.attendanceLoaded)
        assertFalse(c.state.value.visitsLoaded)
    }

    @Test fun gateDutyRefreshStillReadsVisits() {
        val api = Api(); val c = controller(api)
        c.refresh()
        assertEquals(1, api.visitCalls)
        assertTrue(c.state.value.visitsLoaded)
    }

    @Test fun gateOnlyStillSkipsPatrolSummaryAndPatrolOnlySkipsVisits() {
        val api = Api(); val c = controller(api)
        c.patrolCalls = false
        c.gateCalls = true
        c.refresh()
        assertEquals(0, api.summaryCalls); assertEquals(1, api.visitCalls)
        val api2 = Api(); val c2 = controller(api2)
        c2.gateCalls = false
        c2.refresh()
        assertEquals(1, api2.summaryCalls); assertEquals(0, api2.visitCalls)
    }

    @Test fun gateCallsAreLoadedOnlyForGateDutyInViewModelAndApp() {
        // loadGateHome (inside / pickups / couriers / students sit behind it) runs only when GATE is in the duty areas
        val vm = src("ui/KioskViewModel.kt")
        assertEquals(3, Regex("GATE in r\\.areas\\) loadGateHome").findAll(vm).count())
        assertTrue(src("ui/KioskApp.kt").contains("gateCalls = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.gateCallsAllowed(state.dutyAreas)"))
        assertTrue(src("ui/guardhome/ClockFlowScreens.kt").contains("controller.gateCalls = gateCalls"))
    }

    // ---- 3. Patrol tab plain English ----
    @Test fun patrolTextHasNoDeveloperWords() {
        val texts = listOf(PatrolCopy.NONE_TODAY, PatrolCopy.UPDATED, PatrolCopy.UNREACHABLE, PatrolCopy.NOT_READY, PatrolCopy.PICK_ONE,
            PatrolCopy.assignedToday(0), PatrolCopy.assignedToday(1), PatrolCopy.assignedToday(3))
        texts.forEach { t -> PatrolCopy.DEV_WORDS.forEach { w -> assertFalse("$t has $w", t.contains(w)) } }
        assertEquals("No patrol assigned for today.", PatrolCopy.NONE_TODAY)
        assertEquals("1 patrol assigned for today", PatrolCopy.assignedToday(1))
        assertEquals("3 patrols assigned for today", PatrolCopy.assignedToday(3))
    }

    @Test fun noDeveloperStringsInPatrolSources() {
        listOf("guardpatrol/ui/GuardPatrolScreens.kt", "guardpatrol/ui/GuardPatrolViewModel.kt").forEach { f ->
            val s = src(f)
            listOf("guard_id set", "my-schedules", "POST /patrol-schedules", "Ask Admin", "Living not ready", "Living unreachable", "duty(ies)", "\"LIVE ·").forEach {
                assertFalse("$f has $it", s.contains(it))
            }
        }
    }

    @Test fun versionIs1080() {
        val g = File("build.gradle.kts").readText()
        assertTrue(g.contains("versionCode = 1080"))
        assertTrue(g.contains("1.0.28-1role-PREVIEW-TEAL-16"))
    }
}
