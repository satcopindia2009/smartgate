package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import com.satcop.smartvisitor.kiosk.data.fixture.PickupFixtures
import com.satcop.smartvisitor.kiosk.data.model.CampusHours
import com.satcop.smartvisitor.kiosk.data.model.CampusHoursRow
import com.satcop.smartvisitor.kiosk.data.model.PickupCreate
import com.satcop.smartvisitor.kiosk.data.model.RejectBody
import com.satcop.smartvisitor.kiosk.data.model.SchoolIds
import com.satcop.smartvisitor.kiosk.data.model.StudentListResponse
import com.satcop.smartvisitor.kiosk.data.model.StudentOut
import com.satcop.smartvisitor.kiosk.data.model.VisitListResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeHostPickupTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun emptyPendingListStaysEmpty() {
        val parsed = json.decodeFromString<VisitListResponse>("""{"data":[],"meta":{"watermark":"DEMO"}}""")
        assertTrue(parsed.data.isEmpty())
    }

    @Test
    fun pendingVisitParsesLivePhotoKeyAndAfterHours() {
        val raw = """
            {
              "data":[{
                "id":"V-1",
                "visitorName":"Riya Sharma",
                "status":"pending",
                "hostId":"PS-H03",
                "schoolId":"SCH-PRANAY-01",
                "livePhotoKey":"media/live_photo/abc",
                "afterHours":true,
                "purpose":"PTM"
              }]
            }
        """.trimIndent()
        val visit = json.decodeFromString<VisitListResponse>(raw).data.single()
        assertEquals("Riya Sharma", visit.visitorName)
        assertEquals("media/live_photo/abc", visit.livePhotoKey)
        assertTrue(visit.afterHours)
        assertEquals(
            "https://replacing-spyware-yes-due.trycloudflare.com/v1/media/media/live_photo/abc",
            ApiConfig.mediaUrl(visit.livePhotoKey!!),
        )
    }

    @Test
    fun rejectBodyUsesReason() {
        val body = json.encodeToString(RejectBody("Host unavailable"))
        assertTrue(body.contains("\"reason\""))
        assertTrue(body.contains("Host unavailable"))
    }

    @Test
    fun thursdayAfterCloseIsAfterHours() {
        val rows = listOf(
            CampusHoursRow(weekday = "thu", openTime = "08:00", closeTime = "18:00", closed = false),
        )
        val now = ZonedDateTime.of(2026, 9, 17, 20, 10, 0, 0, ZoneId.of("Asia/Kolkata"))
        assertTrue(CampusHours.isAfterHours(rows, now))
        val daytime = ZonedDateTime.of(2026, 9, 17, 10, 0, 0, 0, ZoneId.of("Asia/Kolkata"))
        assertFalse(CampusHours.isAfterHours(rows, daytime))
    }

    @Test
    fun pranayStudentsParseClassSection() {
        val raw = """
            {"data":[
              {"id":"STU-PS-S-001","schoolId":"SCH-PRANAY-01","studentId":"PS-S-001","name":"Asha Patil","class":"5","section":"B","active":true},
              {"id":"STU-PS-S-002","schoolId":"SCH-PRANAY-01","studentId":"PS-S-002","name":"Rohan Shah","class":"3","section":"A","active":true}
            ]}
        """.trimIndent()
        val rows = json.decodeFromString<StudentListResponse>(raw).data
        assertEquals("5-B", rows[0].classSection())
        assertEquals("3-A", rows[1].classSection())
        assertEquals("Asha Patil", rows[0].name)
    }

    @Test
    fun pickupCreateUsesContractFields() {
        val body = json.encodeToString(
            PickupCreate(
                studentId = "STU-PS-S-001",
                gateId = "PS-G-MAIN",
                pickupReason = "early",
                collectorPickupPersonId = "AP-PS-001-1",
            ),
        )
        assertTrue(body.contains("\"studentId\""))
        assertTrue(body.contains("\"gateId\""))
        assertTrue(body.contains("\"pickupReason\""))
        assertTrue(body.contains("STU-PS-S-001"))
    }

    @Test
    fun pickupFixturesAreNotPranay() {
        assertTrue(PickupFixtures.students.none { it.schoolId == SchoolIds.PRANAY })
        assertTrue(PickupFixtures.students.none { it.name.contains("Priya") })
        assertTrue(PickupFixtures.authorizedFor("STU-PS-S-001").isEmpty())
        val started = PickupFixtures.start(
            studentId = "STU-DEMO-S-001",
            gateId = "G-MAIN",
            pickupReason = "early",
            collectorId = "AP-DEMO-001-1",
            collectorName = null,
            collectorMobile = null,
            reasonOther = null,
        )
        assertEquals("Rohit Mehta", started.collectorName)
        assertEquals("Matching", started.status)
    }

    @Test
    fun studentOutClassSerialName() {
        val student = json.decodeFromString<StudentOut>(
            """{"id":"STU-1","name":"Asha Patil","class":"5","section":"B"}""",
        )
        assertEquals("5", student.className)
        val encoded = json.encodeToString(student)
        assertTrue(encoded.contains("\"class\""))
    }

    @Test
    fun approvedVisitLeavesPendingFilter() {
        val pending = listOf(
            VisitOut(id = "V-KEEP", status = "pending", visitorName = "Riya"),
            VisitOut(id = "V-GONE", status = "approved", visitorName = "CHANDAN"),
        ).filter { it.status == "pending" }
        assertEquals(listOf("V-KEEP"), pending.map { it.id })
    }
}
