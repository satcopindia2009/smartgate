package com.satcop.smartvisitor.kiosk.guardhome

import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.AttendanceState
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeApi
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeController
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.HomeView
import com.satcop.smartvisitor.kiosk.ui.guardhome.VisitFilter
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeApi : GuardHomeApi {
    var today = TodayAttendance(attendanceStatus = "NONE", canCheckIn = true)
    var visits: List<VisitOut> = emptyList()
    var checkInError: Throwable? = null
    var clockOutError: Throwable? = null
    val checkIns = mutableListOf<AttendanceRequest>()
    val clockOuts = mutableListOf<AttendanceRequest>()
    val queries = mutableListOf<String?>()
    var todayCalls = 0
    var searchResult: List<VisitOut> = emptyList()
    var searchError: Throwable? = null

    var summary = com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary()
    var summaryError: Throwable? = null
    override fun todaySummary(): com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary {
        summaryError?.let { throw it }
        return summary
    }
    override fun attendanceToday(): TodayAttendance { todayCalls++; return today }
    override fun checkIn(req: AttendanceRequest): AttendanceRow {
        checkIns += req
        checkInError?.let { throw it }
        today = TodayAttendance(attendanceStatus = "PRESENT", canClockOut = true)
        return AttendanceRow(id = "GA-1")
    }
    override fun clockOut(req: AttendanceRequest): AttendanceRow {
        clockOuts += req
        clockOutError?.let { throw it }
        today = TodayAttendance(attendanceStatus = "CLOCKED_OUT")
        return AttendanceRow(id = "GA-1")
    }
    override fun visitsBetween(dateFrom: String, dateTo: String, q: String?): List<VisitOut> {
        queries += q
        if (q != null) { searchError?.let { throw it }; return searchResult }
        return visits
    }
}

class GuardHomeControllerTest {
    private var clock = 1_000_000L
    private val api = FakeApi()
    private val fixedNow = Instant.parse("2026-09-30T11:00:00Z")
    private var attempt = 0

    private fun controller() = GuardHomeController(
        scope = CoroutineScope(Dispatchers.Unconfined),
        api = api,
        io = Dispatchers.Unconfined,
        nowElapsedMs = { clock },
        nowInstant = { fixedNow },
        newAttemptId = { "att-${++attempt}" },
        hasLocationPermission = { true },
    )

    private fun fix(ageMs: Long = 500, acc: Double = 10.0) = GpsFix(18.5, 73.8, acc, clock - ageMs)
    private fun err(code: String, http: Int = 400, msg: String = "server text") = ApiException(code, msg, http)

    @Test
    fun refreshLoadsAttendanceAndTodaysVisits() {
        api.visits = listOf(VisitOut(id = "1", status = "pending", createdAt = "2026-09-30T10:00:00+05:30"))
        val c = controller()
        c.refresh()
        assertEquals(1, c.state.value.summary.pending)
        assertEquals(AttendanceState.NONE, c.state.value.attendance?.state)
        assertFalse(c.state.value.loading)
    }

    @Test
    fun refreshFailureKeepsLastGoodDataAndShowsPlainError() {
        val c = controller()
        c.refresh()
        val failing = object : GuardHomeApi by api {
            override fun attendanceToday(): TodayAttendance = throw java.io.IOException("Unable to resolve host broadband-headers")
        }
        val c2 = GuardHomeController(CoroutineScope(Dispatchers.Unconfined), failing, Dispatchers.Unconfined, { clock }, { fixedNow })
        c2.refresh()
        val msg = c2.state.value.attendanceError!!
        assertFalse(msg.contains("broadband"))
        assertEquals("Can't reach the server right now. Check your internet connection and try again.", msg)
    }

    @Test
    fun checkInSendsOneRequestWithEverythingThenRefreshes() {
        val c = controller()
        c.refresh()
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        c.submit("B64", clock - 1000, fix())
        assertEquals(1, api.checkIns.size)
        val r = api.checkIns.single()
        assertEquals("B64", r.imageBase64)
        assertEquals(18.5, r.lat!!, 0.0)
        assertEquals(10.0, r.accuracyM!!, 0.0)
        assertEquals("att-1", r.attemptId)
        assertEquals("2026-09-30T11:00:00Z", r.capturedAt)
        assertNull(c.state.value.panel)
        assertEquals(AttendanceState.PRESENT, c.state.value.attendance?.state)
        assertEquals("You are checked in.", c.state.value.toast)
    }

    @Test
    fun noRequestIsSentWithoutFreshLocation() {
        val c = controller()
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        c.submit("B64", clock, null)
        c.submit("B64", clock, fix(ageMs = 60_000))
        c.submit("B64", clock, fix(acc = 500.0))
        assertTrue(api.checkIns.isEmpty())
        assertTrue(c.state.value.panelError)
    }

    @Test
    fun outsideFenceStaysOnPanelWithPlainCopyAndRetryNeedsNewReading() {
        api.checkInError = err("GEO_FENCE_RESTRICTED", 403, "Outside campus geo-fence; action blocked (geoFenceMode=restrict)")
        val c = controller()
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        val first = fix()
        c.submit("B64", clock, first)
        val s = c.state.value
        assertEquals(AttendanceMode.CHECK_IN, s.panel)
        assertEquals("You are outside the school campus. Please move inside the campus and try again.", s.panelMessage)
        assertFalse(s.panelMessage!!.contains("geoFenceMode"))
        assertFalse(s.panelBusy)
        // same failed reading again -> blocked locally, no second request
        c.submit("B64", clock, first)
        assertEquals(1, api.checkIns.size)
        // a fresh reading goes through, with a NEW attempt id
        clock += 5000
        api.checkInError = null
        c.submit("B64", clock - 100, fix(ageMs = 100))
        assertEquals(2, api.checkIns.size)
        assertNotEquals(api.checkIns[0].attemptId, api.checkIns[1].attemptId)
        assertNull(c.state.value.panel)
    }

    @Test
    fun gpsAccuracyLowAndStaleShowServerCopy() {
        val c = controller()
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        api.checkInError = err("GPS_ACCURACY_LOW")
        c.submit("B", clock, fix())
        assertEquals("Unable to get an accurate location. Please enable GPS and try again.", c.state.value.panelMessage)
        clock += 3000
        api.checkInError = err("STALE_CAPTURE")
        c.submit("B", clock, fix())
        assertEquals("Your location reading is too old. Please try again to get a fresh location.", c.state.value.panelMessage)
    }

    @Test
    fun alreadyClockedOutClosesPanelAndRereadsServerState() {
        api.clockOutError = err("ALREADY_CLOCKED_OUT", 409)
        api.today = TodayAttendance(attendanceStatus = "CLOCKED_OUT")
        val c = controller()
        c.openPanel(AttendanceMode.CLOCK_OUT); c.proceedToSelfie()
        c.submit(null, null, fix())
        assertNull(c.state.value.panel)
        assertEquals("You have already clocked out for today.", c.state.value.toast)
        assertEquals(AttendanceState.CLOCKED_OUT, c.state.value.attendance?.state)
        assertTrue(api.todayCalls >= 1)
    }

    @Test
    fun clockOutWorksWithoutPhotoAndCannotDoubleSubmit() {
        api.today = TodayAttendance(attendanceStatus = "PRESENT", canClockOut = true)
        val c = controller()
        c.openPanel(AttendanceMode.CLOCK_OUT); c.proceedToSelfie()
        c.submit(null, null, fix())
        assertEquals(1, api.clockOuts.size)
        assertNull(api.clockOuts.single().imageBase64)
        assertEquals(AttendanceState.CLOCKED_OUT, c.state.value.attendance?.state)
        assertFalse(c.state.value.attendance!!.canClockOut)
        // refresh (like an app restart) never flips it back
        c.refresh()
        assertEquals(AttendanceState.CLOCKED_OUT, c.state.value.attendance?.state)
    }

    @Test
    fun findVisitorNoMatchShowsExactCopy() {
        api.searchResult = emptyList()
        val c = controller()
        c.openFind()
        c.setFindQuery("zzzz")
        c.search()
        assertEquals("Visitor not present.", c.state.value.findMessage)
        assertEquals(listOf<String?>("zzzz"), api.queries)
    }

    @Test
    fun findVisitorShortQueryDoesNotCallServer() {
        val c = controller()
        c.setFindQuery("a")
        c.search()
        assertTrue(api.queries.isEmpty())
        assertNotNull(c.state.value.findMessage)
    }

    @Test
    fun findVisitorReturnsMatchesAndErrorsArePlain() {
        api.searchResult = listOf(VisitOut(id = "V-1", status = "pending", visitorName = "Sneha Iyer"))
        val c = controller()
        c.setFindQuery("Sneha")
        c.search()
        assertEquals(1, c.state.value.findResults?.size)
        assertNull(c.state.value.findMessage)
        api.searchError = err("TOKEN_EXPIRED", 401)
        c.setFindQuery("Sneha2")
        c.search()
        assertEquals("Your session has expired. Please sign in again.", c.state.value.findMessage)
    }

    @Test
    fun backClosesPanelThenSubViewThenExits() {
        val c = controller()
        c.openFind()
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        assertTrue(c.back())
        assertNull(c.state.value.panel)
        assertTrue(c.back())
        assertEquals(HomeView.HOME, c.state.value.view)
        assertFalse(c.back())
    }

    @Test
    fun todayBoundaryFollowsIstClock() {
        val c = GuardHomeController(
            CoroutineScope(Dispatchers.Unconfined), api, Dispatchers.Unconfined, { clock },
            { Instant.parse("2026-09-30T19:00:00Z") }, // 00:30 IST on 1 Oct
        )
        c.refresh()
        assertEquals("2026-10-01", c.state.value.today.toString())
        assertEquals(0, c.state.value.summary.all)
        assertEquals(VisitFilter.PENDING, c.state.value.listFilter)
        assertEquals("Asia/Kolkata", GuardHomeLogic.IST.id)
    }
}

class GuardClockFlowTest {
    private var clock = 1_000_000L
    private val api = FakeApi()
    private var attempt = 0
    private var perm = true
    private fun controller() = GuardHomeController(
        scope = CoroutineScope(Dispatchers.Unconfined), api = api, io = Dispatchers.Unconfined,
        nowElapsedMs = { clock }, nowInstant = { Instant.parse("2026-09-30T11:00:00Z") },
        newAttemptId = { "att-${++attempt}" }, hasLocationPermission = { perm },
    )
    private fun fix() = GpsFix(18.5, 73.8, 10.0, clock - 500)

    @Test
    fun submitBeforeSelfieStepDoesNothing() {
        val c = controller()
        c.openPanel(AttendanceMode.CHECK_IN)
        c.submit("B64", clock - 1000, fix())
        assertEquals(0, api.checkIns.size)
    }

    @Test
    fun lockedUntilAttendanceLoadedThenServerDecides() {
        val c = controller()
        assertFalse(c.state.value.attendanceLoaded)
        c.refreshAttendance()
        assertTrue(c.state.value.attendanceLoaded)
        assertEquals(0, api.queries.size) // no visitor data is read on the lock screen
    }

    @Test
    fun successShowsResultWithApiIdAndFailureShowsNoResult() {
        val c = controller()
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        api.checkInError = ApiException("GEO_FENCE_RESTRICTED", "x", 403)
        c.submit("B64", clock - 1000, fix())
        assertNull(c.state.value.result)
        assertEquals(1, c.state.value.attemptNo)
        assertTrue(c.state.value.panelError)
        assertEquals(AttendanceMode.CHECK_IN, c.state.value.panel) // stays on selfie screen
        api.checkInError = null
        clock += 5000 // Retry = NEW selfie + NEW location reading
        c.submit("B64-2", clock - 500, fix())
        assertEquals("GA-1", c.state.value.result?.recordId)
        assertEquals(2, api.checkIns.size)
        assertEquals("att-1", api.checkIns[0].attemptId)
        assertNotEquals(api.checkIns[0].attemptId, api.checkIns[1].attemptId)
        c.clearResult()
        assertNull(c.state.value.result)
    }

    @Test
    fun checkInWithoutLocationPermissionIsSentWithoutCoordinates() {
        perm = false
        val c = controller()
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        c.submit("B64", clock - 1000, null)
        val r = api.checkIns.single()
        assertNull(r.lat); assertNull(r.lng); assertTrue(r.gpsMissing)
    }

    @Test
    fun restrictModeRefusalShowsLocationNeededCopy() {
        perm = false
        val c = controller()
        api.checkInError = ApiException("GEO_FENCE_RESTRICTED", "x", 403)
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        c.submit("B64", clock - 1000, null)
        assertEquals("Location access is needed to check in.", c.state.value.panelMessage)
    }

    @Test
    fun clockOutWithoutLocationNeverReachesServer() {
        perm = false
        val c = controller()
        c.openPanel(AttendanceMode.CLOCK_OUT); c.proceedToSelfie()
        c.submit("B64", clock - 1000, null)
        assertEquals(0, api.clockOuts.size)
        assertEquals("Your location is required to clock out. Please turn on GPS and try again.", c.state.value.panelMessage)
    }
}

class GuardSoftModeFlagTest {
    @Test
    fun locationDeniedCheckInSucceedsWithFlagNoteOnResult() {
        val api = FakeApi()
        val c = GuardHomeController(
            CoroutineScope(Dispatchers.Unconfined), api, Dispatchers.Unconfined, { 1_000_000L },
            { Instant.parse("2026-09-30T11:00:00Z") }, { "att-1" }, { false },
        )
        c.openPanel(AttendanceMode.CHECK_IN); c.proceedToSelfie()
        c.submit("B64", 999_000L, null)
        val res = c.state.value.result!!
        assertEquals("Location is off. Your check-in will be flagged for review.", res.flaggedNote)
        assertEquals("GA-1", res.recordId)
    }
}
