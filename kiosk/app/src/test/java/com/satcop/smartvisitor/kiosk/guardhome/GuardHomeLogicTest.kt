package com.satcop.smartvisitor.kiosk.guardhome

import com.satcop.smartvisitor.kiosk.data.model.AttendanceState
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.VisitFilter
import java.time.Instant
import java.time.LocalDate
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardHomeLogicTest {
    private val today = LocalDate.of(2026, 9, 30)
    private fun v(id: String, status: String, at: String, reject: String? = null) = VisitOut(
        id = id, status = status, createdAt = at, visitorName = "N$id", mobile = "9765478502",
        idNumber = "111111115478", rejectReason = reject,
    )

    private val visits = listOf(
        v("1", "pending", "2026-09-30T10:00:00+05:30"),
        v("2", "rejected", "2026-09-30T11:00:00+05:30", "Not expected"),
        v("3", "approved", "2026-09-30T12:00:00+05:30"),
        v("4", "inside", "2026-09-30T13:00:00+05:30"),
        v("5", "completed", "2026-09-30T14:00:00+05:30"),
        v("old", "pending", "2026-09-29T23:59:00+05:30"),
        // 00:30 IST on 30 Sep is still 29 Sep in UTC: must count as TODAY in IST.
        v("6", "pending", "2026-09-29T19:00:00+00:00"),
    )

    @Test
    fun summaryIsScopedToTodayInIst() {
        val s = GuardHomeLogic.summary(visits, today)
        assertEquals(6, s.all)
        assertEquals(2, s.pending)
        assertEquals(1, s.rejected)
        assertEquals(3, s.todays)
    }

    @Test
    fun tilesAddUp() {
        val s = GuardHomeLogic.summary(visits, today)
        assertEquals(s.all, s.todays + s.pending + s.rejected)
    }

    @Test
    fun rolloverToNextDayStartsAtZero() {
        val s = GuardHomeLogic.summary(visits, LocalDate.of(2026, 10, 1))
        assertEquals(0, s.all)
        assertEquals(0, s.pending)
    }

    @Test
    fun filtersMatchTiles() {
        assertEquals(setOf("1", "6"), GuardHomeLogic.filtered(visits, today, VisitFilter.PENDING).map { it.id }.toSet())
        assertEquals(listOf("2"), GuardHomeLogic.filtered(visits, today, VisitFilter.REJECTED).map { it.id })
        assertEquals(setOf("3", "4", "5"), GuardHomeLogic.filtered(visits, today, VisitFilter.TODAYS).map { it.id }.toSet())
        assertEquals(6, GuardHomeLogic.filtered(visits, today, VisitFilter.ALL).size)
    }

    @Test
    fun newestFirst() {
        val ids = GuardHomeLogic.filtered(visits, today, VisitFilter.ALL).map { it.id }
        assertEquals("5", ids.first())
    }

    @Test
    fun rowMasksMobileAndNeverExposesIdNumber() {
        val r = GuardHomeLogic.row(visits[0])
        assertEquals("••••••8502", r.mobileMasked)
        assertFalse(r.toString().contains("111111115478"))
        assertFalse(r.toString().contains("9765478502"))
    }

    @Test
    fun rejectReasonShownOnlyForRejected() {
        assertEquals("Not expected", GuardHomeLogic.row(visits[1]).rejectReason)
        assertNull(GuardHomeLogic.row(v("x", "pending", "2026-09-30T10:00:00+05:30", "ignored")).rejectReason)
    }

    @Test
    fun rejectReasonParsesFromLiveJson() {
        val json = Json { ignoreUnknownKeys = true }
        val raw = """{"id":"V-1","status":"rejected","rejectReason":"latency test cleanup","createdAt":"2026-09-30T15:16:47+05:30",
            "createdBy":{"userId":"U-GATE","name":"Gate","role":"gate"},"approvalMode":null}"""
        val visit = json.decodeFromString<VisitOut>(raw)
        assertEquals("latency test cleanup", GuardHomeLogic.row(visit).rejectReason)
    }

    @Test
    fun exactNotPresentCopy() {
        assertEquals("Visitor not present.", GuardHomeLogic.NOT_PRESENT)
    }

    @Test
    fun queryNeedsTwoCharacters() {
        assertFalse(GuardHomeLogic.validQuery(" a "))
        assertTrue(GuardHomeLogic.validQuery("ab"))
    }

    @Test
    fun searchWindowIs90DaysInclusive() {
        val (from, to) = GuardHomeLogic.searchWindow(today)
        assertEquals("2026-09-30", to)
        assertEquals(LocalDate.parse(to).minusDays(89).toString(), from)
    }

    @Test
    fun todayIstUsesKolkataNotUtc() {
        // 20:00 UTC on 29 Sep = 01:30 IST on 30 Sep
        assertEquals(LocalDate.of(2026, 9, 30), GuardHomeLogic.todayIst(Instant.parse("2026-09-29T20:00:00Z")))
    }

    @Test
    fun attendanceTextFollowsServerState() {
        val none = TodayAttendance(attendanceStatus = "NONE", message = "No attendance yet", canCheckIn = true)
        assertEquals("Not checked in", GuardHomeLogic.attendanceHeadline(none))
        assertEquals("No attendance yet", GuardHomeLogic.attendanceDetail(none))
        assertEquals("Not checked in", GuardHomeLogic.attendanceHeadline(null))
        val json = Json { ignoreUnknownKeys = true }
        val present = json.decodeFromString<TodayAttendance>(
            """{"attendance":{"id":"GA-1","checkInAt":"2026-09-30T16:27:49+05:30","geofenceName":"Campus","geofenceStatus":"inside"},
               "attendanceStatus":"PRESENT","dutyDate":"2026-09-30","canCheckIn":false,"canClockOut":true}""",
        )
        assertEquals(AttendanceState.PRESENT, present.state)
        assertEquals("Checked in", GuardHomeLogic.attendanceHeadline(present))
        assertEquals("In 04:27 PM · Campus (inside)", GuardHomeLogic.attendanceDetail(present))
        val out = json.decodeFromString<TodayAttendance>(
            """{"attendance":{"id":"GA-1","checkInAt":"2026-09-30T16:27:49+05:30","outServerTime":"2026-09-30T17:10:00+05:30"},
               "attendanceStatus":"CLOCKED_OUT","canCheckIn":false,"canClockOut":false}""",
        )
        assertEquals("Clocked out", GuardHomeLogic.attendanceHeadline(out))
        assertEquals("In 04:27 PM · Out 05:10 PM", GuardHomeLogic.attendanceDetail(out))
        assertFalse(out.canClockOut)
    }

    @Test
    fun unknownStateFallsBackToNone() {
        assertEquals(AttendanceState.NONE, AttendanceState.parse("weird"))
        assertEquals(AttendanceState.NONE, AttendanceState.parse(null))
    }
}
