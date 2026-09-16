package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.Gate
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.Meta
import com.satcop.smartvisitor.kiosk.data.model.School
import com.satcop.smartvisitor.kiosk.data.model.SchoolConfig
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse

/**
 * Local fixtures matching FastAPI Day-1 seed + GET /v1/auth/me, /v1/staff?active=true, /v1/gates.
 * Wave 1 is fixtures-first — no live network.
 */
object DemoFixtures {
    const val WATERMARK = "DEMO"
    const val SCHOOL_ID = "SCH-DEMO-01"
    const val SCHOOL_TZ = "Asia/Calcutta"
    const val GATE_MAIN_ID = "G-MAIN"
    const val HOST_ANITA_ID = "H03"
    const val PEAK_TIP_TITLE = "Peak tip"
    const val PEAK_TIP_BODY = "PTM hours — keep IDs ready; parents first at Main Gate"

    val meta: Meta = Meta(watermark = WATERMARK)

    val school: School = School(
        id = SCHOOL_ID,
        name = "Demo International School",
        timezone = SCHOOL_TZ,
        overdueHoursDefault = 4,
        config = SchoolConfig(hostNotifyChannels = listOf("in_app")),
    )

    val me: MeResponse = MeResponse(
        id = "U-GATE",
        schoolId = SCHOOL_ID,
        role = "gate",
        staffId = "G01",
        gateIds = listOf("G-MAIN", "G-PED", "G-STAFF", "G-BUS"),
        displayName = "Gate — Ramesh",
        phone = "9000000010",
        email = "gate@demo.school",
        meta = meta,
    )

    val gates: List<Gate> = listOf(
        Gate(id = "G-MAIN", schoolId = SCHOOL_ID, name = "Main Gate", active = true),
        Gate(id = "G-PED", schoolId = SCHOOL_ID, name = "Pedestrian Gate", active = true),
        Gate(id = "G-STAFF", schoolId = SCHOOL_ID, name = "Staff Gate", active = true),
        Gate(id = "G-BUS", schoolId = SCHOOL_ID, name = "Bus Bay", active = true),
    )

    val staff: List<Staff> = listOf(
        Staff(id = "H01", schoolId = SCHOOL_ID, name = "Meera Kulkarni", roleTitle = "Principal", mobile = "9000000001", userId = null, active = true),
        Staff(id = "H02", schoolId = SCHOOL_ID, name = "Rahul Deshpande", roleTitle = "Admin Officer", mobile = "9000000002", userId = "U-HOST-H02", active = true),
        Staff(id = "H03", schoolId = SCHOOL_ID, name = "Anita Joshi", roleTitle = "Primary Coordinator", mobile = "9000000003", userId = "U-HOST", active = true),
        Staff(id = "H04", schoolId = SCHOOL_ID, name = "Sanjay Patil", roleTitle = "Accounts", mobile = "9000000004", userId = null, active = true),
        Staff(id = "G01", schoolId = SCHOOL_ID, name = "Gate — Ramesh", roleTitle = "Guard", mobile = "9000000010", userId = "U-GATE", active = true),
    )

    val inside: List<InsideVisit> = listOf(
        InsideVisit(
            id = "V-20260916-014",
            visitorName = "Priya Sharma",
            timeIn = "2026-09-16T14:10:00+05:30",
            gateId = "G-MAIN",
            hostId = "H03",
            visitorType = "Parent",
            status = "inside",
        ),
        InsideVisit(
            id = "V-20260916-021",
            visitorName = "Arjun Kale",
            timeIn = "2026-09-16T10:02:00+05:30",
            gateId = "G-STAFF",
            hostId = "H04",
            visitorType = "Vendor",
            status = "inside",
        ),
        InsideVisit(
            id = "V-20260916-033",
            visitorName = "Neha Salunkhe",
            timeIn = "2026-09-16T16:20:00+05:30",
            gateId = "G-PED",
            hostId = "H02",
            visitorType = "Vendor",
            status = "inside",
        ),
    )

    /** Shared walkthrough story — must match Hub handoff. */
    val demoStory: DemoStory = DemoStory(
        visitorName = "Priya Sharma",
        mobile = "+91 98220 11122",
        visitorType = "Parent",
        purpose = "PTM follow-up, Class 4B",
        hostId = HOST_ANITA_ID,
        gateId = GATE_MAIN_ID,
        hostName = "Anita Joshi",
        hostRoleTitle = "Primary Coordinator",
        gateName = "Main Gate",
    )

    fun staffListResponse(active: Boolean = true): StaffListResponse {
        val rows = if (active) staff.filter { it.active } else staff
        return StaffListResponse(data = rows, meta = meta)
    }

    fun gateListResponse(): GateListResponse = GateListResponse(data = gates, meta = meta)

    fun insideListResponse(): InsideListResponse = InsideListResponse(data = inside, meta = meta)

    fun hostsForPicker(activeOnly: Boolean = true): List<Staff> =
        staffListResponse(active = activeOnly).data.filter { it.roleTitle != "Guard" }

    fun gateById(gateId: String): Gate? = gates.firstOrNull { it.id == gateId }

    fun staffById(staffId: String): Staff? = staff.firstOrNull { it.id == staffId }
}
