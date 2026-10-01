package com.satcop.smartvisitor.kiosk.teal6

import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardTodayLogic
import com.satcop.smartvisitor.kiosk.ui.steps.HostPicker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Teal6BindingTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- shift fields ----
    @Test fun todayParsesShift() {
        val t = json.decodeFromString<TodayAttendance>(
            """{"attendanceStatus":"NONE","shiftName":"Morning","shiftStartTime":"06:30","shiftStartDisplay":"Morning · starts 06:30 am",
               "shiftEndTime":"14:30","shiftEndDisplay":"02:30 pm","attendance":{"id":"GA-1","shiftName":"Morning"}}""",
        )
        assertEquals("Morning", t.shiftName)
        assertEquals("06:30", t.shiftStartTime)
        assertEquals("Morning · starts 06:30 am", t.shiftStartDisplay)
        assertEquals("14:30", t.shiftEndTime)
        assertEquals("02:30 pm", t.shiftEndDisplay)
        assertEquals("Morning", t.attendance?.shiftName)
        assertEquals("Morning · starts 06:30 am", GuardTodayLogic.shiftRow(t))
    }

    @Test fun todayWithNullShiftHidesRow() {
        val t = json.decodeFromString<TodayAttendance>("""{"attendanceStatus":"NONE","shiftName":null,"shiftStartDisplay":null}""")
        assertNull(t.shiftName); assertNull(t.shiftStartDisplay)
        assertNull(GuardTodayLogic.shiftRow(t))
        assertNull(GuardTodayLogic.shiftRow(json.decodeFromString<TodayAttendance>("""{"attendanceStatus":"NONE"}""")))
        assertNull(GuardTodayLogic.shiftRow(TodayAttendance(shiftStartDisplay = "  ")))
        assertNull(GuardTodayLogic.shiftRow(null))
    }

    @Test fun meLoginAndRowParseShift() {
        val me = json.decodeFromString<MeResponse>(
            """{"id":"U","schoolId":"S","role":"guard","displayName":"Ravi","shiftName":"Morning","shiftStartDisplay":"Morning · starts 06:30 am"}""",
        )
        assertEquals("Morning", me.shiftName)
        val login = json.decodeFromString<LoginResponse>(
            """{"accessToken":"t","shiftStartDisplay":"Morning · starts 06:30 am","user":{"id":"U","schoolId":"S","role":"guard","displayName":"R","shiftEndDisplay":"02:30 pm"}}""",
        )
        assertEquals("Morning · starts 06:30 am", login.shiftStartDisplay)
        assertEquals("02:30 pm", login.user?.shiftEndDisplay)
        val noShift = json.decodeFromString<MeResponse>("""{"id":"U","schoolId":"S","role":"admin","displayName":"A","shiftName":null}""")
        assertNull(noShift.shiftName); assertNull(noShift.shiftEndTime)
        val row = json.decodeFromString<AttendanceRow>("""{"id":"GA-2","shiftStartTime":"06:30"}""")
        assertEquals("06:30", row.shiftStartTime); assertNull(row.shiftName)
    }

    @Test fun headerSubtitleUsesServerShiftOnly() {
        assertEquals("Ravi · School", GuardTodayLogic.headerSubtitle("Ravi · School", TodayAttendance()))
        assertEquals("Main Gate · Morning shift", GuardTodayLogic.headerSubtitle("Ravi", TodayAttendance(gateName = "Main Gate", shiftName = "Morning")))
        assertEquals("Ravi · Morning shift", GuardTodayLogic.headerSubtitle("Ravi", TodayAttendance(shiftName = "Morning")))
    }

    // ---- today-summary ----
    private val full = """{"date":"2026-10-01",
        "incidentsToday":{"count":3,"open":2,"items":[{"id":"I1","title":"Gate jam","type":"Other","severity":"low","status":"open","createdAt":"2026-10-01T07:00:00+05:30","photoUrl":null}]},
        "patrol":{"assignedToday":1,"roundsCompleted":1,"roundsTotal":2,"checkpointsScanned":3,"checkpointsTotal":8,"missedCount":1,"progressPercent":38,
                  "nextCheckpoint":{"checkpointId":"C4","name":"Library","gateOrZone":"Block B","assignmentId":"A1","roundId":"R1","routeName":"Route North"}},
        "meta":{"watermark":"w","serverTime":"2026-10-01T07:10:00+05:30"}}"""

    @Test fun summaryParsesPresentData() {
        val s = json.decodeFromString<GuardTodaySummary>(full)
        assertEquals(3, s.incidentsToday.count); assertEquals(2, s.incidentsToday.open)
        assertEquals("Gate jam", s.incidentsToday.items.single().title)
        assertEquals(3, s.patrol.checkpointsScanned); assertEquals(8, s.patrol.checkpointsTotal)
        assertEquals(38, s.patrol.progressPercent)
        assertEquals("Library", s.patrol.nextCheckpoint?.name)
        assertEquals("3/8", GuardTodayLogic.checkpointsText(s))
        assertEquals("Rounds 1/2", GuardTodayLogic.roundsText(s))
        assertEquals("Missed 1", GuardTodayLogic.missedText(s))
        assertEquals("38%", GuardTodayLogic.percentText(s))
        assertEquals(0.38f, GuardTodayLogic.progressFraction(s), 0.001f)
        assertEquals("Next: Library · Block B", GuardTodayLogic.nextText(s))
        assertEquals("3", GuardTodayLogic.incidentCountText(s)); assertEquals("2 open", GuardTodayLogic.incidentOpenText(s))
        assertFalse(GuardTodayLogic.incidentsEmpty(s))
    }

    @Test fun summaryWithZerosIsHonestEmptyState() {
        val s = json.decodeFromString<GuardTodaySummary>(
            """{"date":"2026-10-01","incidentsToday":{"count":0,"open":0,"items":[]},
               "patrol":{"assignedToday":0,"roundsCompleted":0,"roundsTotal":0,"checkpointsScanned":0,"checkpointsTotal":0,"missedCount":0,"progressPercent":0,"nextCheckpoint":null},
               "meta":{"watermark":"w","serverTime":"t"}}""",
        )
        assertFalse(GuardTodayLogic.hasRounds(s))
        assertNull(GuardTodayLogic.checkpointsText(s)); assertNull(GuardTodayLogic.roundsText(s))
        assertNull(GuardTodayLogic.percentText(s)); assertNull(GuardTodayLogic.nextText(s))
        assertEquals(0f, GuardTodayLogic.progressFraction(s), 0f)
        assertEquals("No rounds assigned today", GuardTodayLogic.NO_ROUNDS)
        assertTrue(GuardTodayLogic.incidentsEmpty(s))
        assertTrue(s.incidentsToday.items.isEmpty()); assertNull(s.patrol.nextCheckpoint)
    }

    @Test fun summaryMissingKeysAndNoSummaryNeverInventNumbers() {
        val s = json.decodeFromString<GuardTodaySummary>("{}")
        assertFalse(GuardTodayLogic.hasRounds(s))
        assertNull(GuardTodayLogic.checkpointsText(null)); assertNull(GuardTodayLogic.incidentCountText(null))
        assertFalse(GuardTodayLogic.incidentsEmpty(null))
        assertNull(GuardTodayLogic.nextText(null))
        val unnamed = json.decodeFromString<GuardTodaySummary>("""{"patrol":{"roundsTotal":1,"nextCheckpoint":{"checkpointId":"C"}}}""")
        assertNull(GuardTodayLogic.nextText(unnamed))
        assertEquals(1.0f, GuardTodayLogic.progressFraction(json.decodeFromString("""{"patrol":{"roundsTotal":1,"progressPercent":140}}""")), 0f)
    }

    @Test fun faceRequiredMapsToPlainSentence() {
        val msg = ErrorCopy.forThrowable(ApiException("FACE_REQUIRED", "Face needed", 403))
        assertEquals("Face verification required. Please verify your face to continue.", msg)
    }

    @Test fun summaryFailureIsNonBlocking() {
        val api = SummaryFailApi()
        val c = com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeController(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), api = api,
            io = kotlinx.coroutines.Dispatchers.Unconfined, nowElapsedMs = { 0L },
        )
        c.refresh()
        val st = c.state.value
        assertEquals("Face verification required. Please verify your face to continue.", st.summaryError)
        assertNull(st.todaySummary)
        assertTrue(st.summaryLoaded)
        assertTrue(st.attendanceLoaded); assertNull(st.attendanceError); assertFalse(st.loading)
    }

    private class SummaryFailApi : com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeApi {
        override fun attendanceToday() = TodayAttendance(attendanceStatus = "PRESENT")
        override fun checkIn(req: com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest) = AttendanceRow()
        override fun clockOut(req: com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest) = AttendanceRow()
        override fun visitsBetween(dateFrom: String, dateTo: String, q: String?) = emptyList<com.satcop.smartvisitor.kiosk.data.model.VisitOut>()
        override fun todaySummary(): GuardTodaySummary = throw ApiException("FACE_REQUIRED", "x", 403)
    }

    // ---- N1 vendor department ----
    private fun vendor(host: String? = null, dept: String? = null) = RegistrationDraft(
        profileKind = ProfileKind.VENDOR, visitorName = "Ravi", mobile = "9800011110", purpose = "Delivery",
        company = "Acme", hostId = host, department = dept,
    )

    @Test fun vendorWithDepartmentAndNoHostPasses() {
        assertFalse(RegistrationValidator.validateStep2(vendor(dept = "Stores")).containsKey(FieldKeys.HOST_ID))
        assertFalse(RegistrationValidator.validateStep2(vendor(host = "H03")).containsKey(FieldKeys.HOST_ID))
    }

    @Test fun vendorWithNeitherFailsAndVisitorStillNeedsHost() {
        assertTrue(RegistrationValidator.validateStep2(vendor()).containsKey(FieldKeys.HOST_ID))
        assertTrue(RegistrationValidator.validateStep2(vendor(dept = "  ")).containsKey(FieldKeys.HOST_ID))
        val visitor = RegistrationDraft(profileKind = ProfileKind.VISITOR, visitorName = "A", mobile = "9800011101", purpose = "p", department = "Stores")
        assertTrue(RegistrationValidator.validateStep2(visitor).containsKey(FieldKeys.HOST_ID))
        assertFalse(RegistrationValidator.validateStep2(visitor.copy(hostId = "H03")).containsKey(FieldKeys.HOST_ID))
    }

    @Test fun createBodyOmitsHostIdAndSendsDepartment() {
        val dept = AddVisitorLogic.departmentToSend(ProfileKind.VENDOR, null, " Stores ")
        assertEquals("Stores", dept)
        val body = VisitCreate(visitorName = "A", mobile = "9800011101", purpose = "p", hostId = null, department = dept, livePhotoKey = "k", idType = "Aadhaar", gateId = "G01")
        val j = json.encodeToJsonElement(VisitCreate.serializer(), body).jsonObject
        assertFalse(j.containsKey("hostId"))
        assertEquals("Stores", j["department"]?.toString()?.trim('"'))
    }

    @Test fun hostChosenOmitsDepartment() {
        assertNull(AddVisitorLogic.departmentToSend(ProfileKind.VENDOR, "H03", "Stores"))
        assertNull(AddVisitorLogic.departmentToSend(ProfileKind.VISITOR, null, "Stores"))
        val body = VisitCreate(visitorName = "A", mobile = "9800011101", purpose = "p", hostId = "H03", department = null, livePhotoKey = "k", idType = "Aadhaar", gateId = "G01")
        val j = json.encodeToJsonElement(VisitCreate.serializer(), body).jsonObject
        assertEquals("H03", j["hostId"]?.toString()?.trim('"'))
        assertFalse(j.containsKey("department"))
    }

    @Test fun departmentsAreDistinctActiveRoleTitles() {
        fun st(id: String, role: String, active: Boolean = true) = Staff(id = id, schoolId = "S", name = id, roleTitle = role, active = active)
        val list = listOf(st("a", "Stores"), st("b", "stores"), st("c", "Admin Office"), st("d", ""), st("e", "Closed", active = false))
        assertEquals(listOf("Admin Office", "Stores"), HostPicker.departments(list))
        assertEquals(listOf("Stores"), HostPicker.filterDepartments(listOf("Admin Office", "Stores"), "sto"))
        assertNotNull(HostPicker.filterDepartments(emptyList(), ""))
    }
}
