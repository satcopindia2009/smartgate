package com.satcop.smartvisitor.kiosk.teal15

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.DutyCompact
import com.satcop.smartvisitor.kiosk.data.model.DutyGate
import com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.guardpatrol.data.AssignmentStatus
import com.satcop.smartvisitor.kiosk.guardpatrol.data.PatrolAssignment
import com.satcop.smartvisitor.kiosk.guardpatrol.data.PatrolDates
import com.satcop.smartvisitor.kiosk.ui.apple.GuardTabs
import com.satcop.smartvisitor.kiosk.ui.duty.DutyArea
import com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeApi
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeController
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1079 (TEAL-15): D4 chooser before camera, patrol-only home, duty header, today-only patrol, no patrol calls on gate, selfie key. */
class Teal15Test {
    private fun src(p: String) = File("src/main/java/com/satcop/smartvisitor/kiosk/$p").readText()
    private fun d(type: String, gate: String? = null, name: String? = null, inForce: Boolean? = true, shift: String? = "Day") =
        DutyCompact(id = "x", type = type, gateId = gate, gateName = name, shiftName = shift, shiftStart = "06:00", shiftEnd = "14:00", inForce = inForce)

    // ---- 1. D4 ----
    @Test fun twoGateDutiesNeedAChooserFromLoginSnapshot() {
        val gates = DutyLogic.gatesFromAssignments(listOf(d("gate", "G-MAIN", "Main Gate"), d("gate", "G-PED", "Pedestrian Gate"), d("patrol")))
        assertEquals(listOf("G-MAIN", "G-PED"), gates.map { it.id })
        assertTrue(DutyLogic.needsGateChooser(gates))
        assertEquals("G-MAIN", DutyLogic.defaultGate(gates)?.id)
        assertFalse(DutyLogic.needsGateChooser(DutyLogic.gatesFromAssignments(listOf(d("gate", "G-MAIN", "Main Gate"), d("gate", "G-MAIN", "Main Gate")))))
    }

    @Test fun preCameraLockScreenGetsTheChooser() {
        val app = src("ui/KioskApp.kt")
        val lock = app.substringAfter("if (lockUp) {").substringBefore("} else if (!signedIn)")
        assertTrue(lock.contains("onClockIn = viewModel::startFaceVerify"))
        assertTrue(lock.contains("gateChoices = state.dutyGates"))
        assertTrue(lock.contains("chosenGateId = state.chosenGateId"))
        assertTrue(lock.contains("onChooseGate = viewModel::chooseGate"))
        // the chosen gate is sent on check-in
        assertTrue(src("ui/KioskViewModel.kt").contains("gateId = _state.value.chosenGateId"))
        // the chooser composable is drawn by the lock screen for 2+ gates
        assertTrue(src("ui/guardhome/ClockFlowScreens.kt").contains("if (gateChoices.size > 1)"))
    }

    @Test fun chosenGateIsSentOnCheckIn() {
        val req = FaceClockInLogic.buildCheckIn("abc", com.satcop.smartvisitor.kiosk.data.geo.CaptureStamp(capturedAt = "2026-10-02T06:30:00+05:30"), Instant.parse("2026-10-02T01:00:00Z"), "a1", gateId = "G-PED")
        assertEquals("G-PED", req.gateId)
    }

    // ---- 2. patrol-only ----
    @Test fun gateActionsOnlyWithGateDuty() {
        assertTrue(DutyLogic.gateActionsVisible(setOf(DutyArea.GATE, DutyArea.PATROL)))
        assertTrue(DutyLogic.gateActionsVisible(setOf(DutyArea.GATE)))
        assertFalse(DutyLogic.gateActionsVisible(setOf(DutyArea.PATROL)))
        assertFalse(DutyLogic.gateActionsVisible(emptySet()))
        assertTrue(DutyLogic.gateActionsVisible(null)) // not read yet = legacy
    }

    @Test fun patrolOnlyHasNoDeskTab() {
        assertEquals(listOf(0, 1, 2, 3), GuardTabs.ids(true))
        assertEquals(listOf(0, 1, 3), GuardTabs.ids(false))
        assertEquals(3, GuardTabs.contentId(2, false)) // third visible tab is Settings
        assertEquals(3, GuardTabs.items(false).size)
        assertFalse(GuardTabs.items(false).any { it.label == "Desk" })
        assertTrue(GuardTabs.items(true).any { it.label == "Desk" })
    }

    @Test fun patrolHomeWiring() {
        val home = src("ui/guardhome/GuardHomeScreen.kt")
        assertTrue(home.contains("if (gateActions) {") && home.contains("\"Find visitor\"") && home.contains("\"Courier log\""))
        assertTrue(home.contains("\"Lost & Found\"") && home.contains("\"Report incident\""))
        assertTrue(src("guardpatrol/ui/GuardPatrolScreens.kt").contains("showDesk = gateActions"))
        assertTrue(src("ui/KioskApp.kt").contains("gateActions = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.gateActionsVisible(state.dutyAreas)"))
    }

    // ---- 3. header ----
    @Test fun headerFollowsDutyNotAttendance() {
        val gate = listOf(d("gate", "G-MAIN", "Main Gate", true, "Day"))
        assertEquals("Main Gate · Day shift", DutyLogic.dutyHeader("Ravi · School", DutyArea.GATE, gate))
        assertEquals("Day shift", DutyLogic.dutyHeader("Ravi · School", DutyArea.PATROL, listOf(d("patrol", shift = "Day"))))
        // patrol-only duty never shows a gate
        assertEquals("Day shift", DutyLogic.dutyHeader("x", DutyArea.PATROL, listOf(d("gate", "G-MAIN", "Main Gate"), d("patrol", shift = "Day"))))
        assertEquals("Ravi · School", DutyLogic.dutyHeader("Ravi · School", DutyArea.PATROL, listOf(d("patrol", shift = null))))
    }

    @Test fun noShiftInForceAtNightMeansNoGateOrShiftText() {
        val night = listOf(d("gate", "G-MAIN", "Main Gate", false, "Morning"), d("patrol", inForce = false, shift = "Morning"))
        assertEquals("Ravi · School", DutyLogic.dutyHeader("Ravi · School", DutyArea.GATE, night))
        assertEquals("Ravi · School", DutyLogic.dutyHeader("Ravi · School", DutyArea.PATROL, night))
    }

    @Test fun headerNullInForceMeansInForceAndOldServerKeepsLegacyLine() {
        assertEquals("Main Gate · Day shift", DutyLogic.dutyHeader("x", DutyArea.GATE, listOf(d("gate", "G", "Main Gate", null, "Day"))))
        assertNull(DutyLogic.dutyHeader("x", DutyArea.GATE, null))
        assertNull(DutyLogic.dutyHeader("x", DutyArea.GATE, emptyList()))
        assertNull(DutyLogic.dutyHeader("x", null, listOf(d("gate"))))
    }

    // ---- 4. today only ----
    private fun pa(id: String, date: String, st: AssignmentStatus = AssignmentStatus.ASSIGNED) =
        PatrolAssignment(id = id, schoolId = "S", templateId = "t", guardId = "g", dutyDate = date, status = st)

    @Test fun patrolListsOnlyToday() {
        val all = listOf(pa("a", "2026-09-20"), pa("b", "2026-10-02"), pa("c", "2026-10-01"), pa("d", "2026-10-02T00:00:00+05:30"))
        assertEquals(listOf("b", "d"), PatrolDates.todayOnly(all, "2026-10-02").map { it.id })
        assertTrue(PatrolDates.todayOnly(listOf(pa("a", "2026-09-20")), "2026-10-02").isEmpty())
    }

    @Test fun pastDutyIsNeverStartable() {
        assertFalse(PatrolDates.canStart(pa("a", "2026-09-20"), "2026-10-02"))
        assertFalse(PatrolDates.canStart(pa("a", "2026-09-20", AssignmentStatus.STARTED), "2026-10-02"))
        assertTrue(PatrolDates.canStart(pa("b", "2026-10-02"), "2026-10-02"))
        assertFalse(PatrolDates.isToday(null, "2026-10-02"))
    }

    @Test fun patrolWiringDropsOldDuties() {
        val api = src("guardpatrol/data/LiveGuardPatrolApi.kt")
        assertFalse(api.contains("if (all.isNotEmpty()) return all"))
        assertTrue(api.contains("PatrolDates.todayOnlyDto"))
        val vm = src("guardpatrol/ui/GuardPatrolViewModel.kt")
        assertTrue(vm.contains("PatrolDates.canStart(asg"))
        assertTrue(src("guardpatrol/ui/GuardPatrolScreens.kt").contains("val canStart = isToday &&"))
    }

    // ---- 5. no patrol calls on the gate home ----
    @Test fun patrolCallsOnlyWithPatrolDuty() {
        assertTrue(DutyLogic.patrolCallsAllowed(setOf(DutyArea.PATROL)))
        assertTrue(DutyLogic.patrolCallsAllowed(setOf(DutyArea.GATE, DutyArea.PATROL)))
        assertFalse(DutyLogic.patrolCallsAllowed(setOf(DutyArea.GATE)))
        assertFalse(DutyLogic.patrolCallsAllowed(emptySet()))
        assertTrue(DutyLogic.patrolCallsAllowed(null)) // unknown yet: DutyHome shows Guard Today until duty is read
    }

    @Test fun patrolViewModelDoesNotBootstrapInInit() {
        val vm = src("guardpatrol/ui/GuardPatrolViewModel.kt")
        val init = vm.substringAfter("    init {").substringBefore("    /** Identity")
        assertFalse(init.contains("bootstrapLive()"))
        assertTrue(vm.contains("fun startIfNeeded()"))
        assertTrue(src("guardpatrol/ui/GuardPatrolScreens.kt").contains("LaunchedEffect(Unit) { vm.startIfNeeded() }"))
        // the only GuardPatrolApp call site is the PATROL area
        assertTrue(src("ui/KioskApp.kt").contains("active == com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.PATROL ->"))
        assertTrue(src("ui/KioskApp.kt").contains("patrolCalls = com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.patrolCallsAllowed(state.dutyAreas)"))
    }

    private class Api : GuardHomeApi {
        var summaryCalls = 0
        override fun attendanceToday() = TodayAttendance()
        override fun checkIn(req: AttendanceRequest) = AttendanceRow()
        override fun clockOut(req: AttendanceRequest) = AttendanceRow()
        override fun visitsBetween(dateFrom: String, dateTo: String, q: String?): List<VisitOut> = emptyList()
        override fun todaySummary(): GuardTodaySummary { summaryCalls++; return GuardTodaySummary() }
    }

    private fun controller(api: Api) = GuardHomeController(
        scope = CoroutineScope(Dispatchers.Unconfined), api = api, io = Dispatchers.Unconfined,
        nowElapsedMs = { 1L }, nowInstant = { Instant.parse("2026-10-02T01:00:00Z") },
    )

    @Test fun gateHomeRefreshSkipsTodaySummary() {
        val api = Api(); val c = controller(api)
        c.patrolCalls = false
        c.refresh()
        assertEquals(0, api.summaryCalls)
        assertTrue(c.state.value.attendanceLoaded)
    }

    @Test fun patrolDutyRefreshStillReadsSummary() {
        val api = Api(); val c = controller(api)
        c.refresh()
        assertEquals(1, api.summaryCalls)
    }

    // ---- 6. selfie ----
    @Test fun selfieReadFromEveryKnownKey() {
        assertTrue(ClockInLogic.selfieUploadedOf(AttendanceRow(selfieUrl = "https://x/s.jpg")))
        assertTrue(ClockInLogic.selfieUploadedOf(AttendanceRow(photoUrl = "https://x/p.jpg")))
        assertTrue(ClockInLogic.selfieUploadedOf(AttendanceRow(photoKey = "media/a")))
        assertTrue(ClockInLogic.selfieUploadedOf(AttendanceRow(selfiePhotoUrl = "u")))
        assertTrue(ClockInLogic.selfieUploadedOf(AttendanceRow(checkInSelfieUploaded = true)))
        assertFalse(ClockInLogic.selfieUploadedOf(AttendanceRow(id = "GA-1")))
        assertFalse(ClockInLogic.selfieUploadedOf(AttendanceRow(selfieUrl = "  ")))
        // an explicit server flag wins
        assertFalse(ClockInLogic.selfieUploadedOf(AttendanceRow(selfieUploaded = false, selfieUrl = "u")))
    }

    @Test fun cardSaysUploadedWhenRowHasSelfieUrl() {
        val r = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(id = "GA-9", selfieUrl = "https://x/s.jpg"))
        assertTrue(r.selfieUploaded)
        assertFalse(ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(id = "GA-9")).selfieUploaded)
    }

    @Test fun apiSelfieUrlKeyIsParsed() {
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
        val row = json.decodeFromString<AttendanceRow>("""{"id":"GA-5","selfieUrl":"https://x/s.jpg","newKey":1}""")
        assertEquals("https://x/s.jpg", row.selfieUrl)
    }

    @Test fun detailsRowKeepsSelfieKeys() {
        val e = ApiException("ALREADY_CHECKED_IN", "m", 409, mapOf(
            "attendance.id" to "GA-7", "attendance.dutyDate" to "2026-10-02", "attendance.checkInAt" to "2026-10-02T09:00:00+05:30",
            "attendance.selfieUrl" to "https://x/s.jpg", "attendance.photoKey" to "media/k",
        ))
        val row = FaceClockInLogic.rowForAlreadyIn(e, null, LocalDate.parse("2026-10-02"))
        assertEquals("https://x/s.jpg", row?.selfieUrl)
        assertTrue(ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row!!).selfieUploaded)
    }

    @Test fun selfieBorrowedFromTodayWhenDetailsRowHasNone() {
        val e = ApiException("ALREADY_CHECKED_IN", "m", 409, mapOf("attendance.id" to "GA-7", "attendance.dutyDate" to "2026-10-02"))
        val today = TodayAttendance(attendance = AttendanceRow(id = "GA-7", dutyDate = "2026-10-02", selfieUrl = "u"))
        assertTrue(FaceClockInLogic.rowForAlreadyIn(e, today, LocalDate.parse("2026-10-02"))!!.let { ClockInLogic.selfieUploadedOf(it) })
        val none = TodayAttendance(attendance = AttendanceRow(id = "GA-7", dutyDate = "2026-10-02"))
        assertFalse(FaceClockInLogic.rowForAlreadyIn(e, none, LocalDate.parse("2026-10-02"))!!.let { ClockInLogic.selfieUploadedOf(it) })
        val flag = TodayAttendance(attendance = AttendanceRow(id = "GA-7", dutyDate = "2026-10-02"), selfieUploaded = true)
        assertTrue(FaceClockInLogic.rowForAlreadyIn(e, flag, LocalDate.parse("2026-10-02"))!!.let { ClockInLogic.selfieUploadedOf(it) })
    }

    @Test fun versionIs1079() {
        val g = File("build.gradle.kts").readText()
        assertTrue(g.contains("versionCode = 1079") && g.contains("1.0.27-1role-PREVIEW-TEAL-15"))
    }
}
