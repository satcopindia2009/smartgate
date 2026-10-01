package com.satcop.smartvisitor.kiosk.duty

import com.satcop.smartvisitor.kiosk.data.model.DutyGate
import com.satcop.smartvisitor.kiosk.data.model.DutyMe
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.ui.duty.DutyArea
import com.satcop.smartvisitor.kiosk.ui.duty.DutyHome
import com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo
import com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DutyLogicTest {
    private val G = DutyArea.GATE
    private val P = DutyArea.PATROL
    private fun info(types: List<String>? = null, home: String? = null, has: Boolean? = null, gates: List<DutyGate> = emptyList()) =
        DutyInfo(types, home, has, "rev1", gates)

    @Test fun gateOnly() {
        val r = DutyLogic.areas("guard", info(listOf("gate")))
        assertEquals(setOf(G), r.areas); assertEquals(DutyHome.GATE, r.home); assertTrue(r.fromServer)
    }

    @Test fun patrolOnly() {
        val r = DutyLogic.areas("guard", info(listOf("patrol")))
        assertEquals(setOf(P), r.areas); assertEquals(DutyHome.PATROL, r.home)
    }

    @Test fun bothLandsOnGateFirst() {
        val r = DutyLogic.areas("guard", info(listOf("patrol", "gate")))
        assertEquals(setOf(G, P), r.areas); assertEquals(DutyHome.BOTH, r.home); assertEquals(G, r.defaultArea)
    }

    @Test fun noneFromServerIsNoDutyForGuard() {
        val r = DutyLogic.areas("guard", info(emptyList(), "none", false), patrolAssignedToday = 3)
        assertTrue(r.areas.isEmpty()); assertEquals(DutyHome.NONE, r.home); assertNull(r.defaultArea)
    }

    @Test fun caseAndHyphenVariants() {
        assertEquals(setOf(G, P), DutyLogic.areas("Guard", info(listOf("GATE", " Patrol "))).areas)
        assertEquals(setOf(G), DutyLogic.areas("guard", info(null, " GATE ")).areas)
        assertEquals(setOf(G, P), DutyLogic.areas("guard", info(null, "Both")).areas)
        assertTrue(DutyLogic.isLegacyGateRole("Gate-Staff"))
        assertEquals(G, DutyLogic.parseType(" Gate "))
        assertNull(DutyLogic.parseType("reception"))
    }

    @Test fun dutyTypesWinsOverPrimaryHome() {
        assertEquals(setOf(P), DutyLogic.areas("guard", info(listOf("patrol"), "both")).areas)
        assertEquals(setOf(G, P), DutyLogic.areas("guard", info(listOf("gate", "patrol"), "gate")).areas)
        assertTrue(DutyLogic.areas("guard", info(emptyList(), "gate")).areas.isEmpty())
    }

    @Test fun unknownTypesAreIgnored() {
        assertEquals(setOf(G), DutyLogic.areas("guard", info(listOf("gate", "reception"))).areas)
    }

    @Test fun legacyGateAccountNeverNoDutyWhileServerHasNoRows() {
        val r = DutyLogic.areas("gate", info(emptyList(), "none", false))
        assertEquals(setOf(G), r.areas)
        assertEquals(setOf(G, P), DutyLogic.areas("gate_staff", info(emptyList(), "none", false), patrolAssignedToday = 2).areas)
    }

    @Test fun absentFieldLegacyGate() {
        assertEquals(setOf(G), DutyLogic.areas("gate", null).areas)
        assertEquals(setOf(G), DutyLogic.areas("gate-staff", DutyInfo()).areas)
        assertFalse(DutyLogic.areas("gate", null).fromServer)
    }

    @Test fun absentFieldGateIdsOrAssignedGate() {
        assertEquals(setOf(G), DutyLogic.areas("guard", null, gateIds = listOf("G-MAIN")).areas)
        assertEquals(setOf(G), DutyLogic.areas("guard", null, attendanceGateId = "G-MAIN", attendanceGateSource = "assignment").areas)
        // geofence / user_gate sources do not make a gate duty
        assertEquals(setOf(P), DutyLogic.areas("guard", null, attendanceGateId = "G-MAIN", attendanceGateSource = "geofence").areas)
    }

    @Test fun absentFieldPatrolAndBoth() {
        assertEquals(setOf(P), DutyLogic.areas("guard", null, patrolAssignedToday = 2).areas)
        assertEquals(setOf(G, P), DutyLogic.areas("guard", null, gateIds = listOf("G-MAIN"), patrolAssignedToday = 1).areas)
        assertEquals(setOf(G, P), DutyLogic.areas("gate", null, patrolAssignedToday = 1).areas)
    }

    @Test fun legacyGuardWithNothingStaysPatrol() {
        assertEquals(setOf(P), DutyLogic.areas("guard", null).areas)
        assertEquals(setOf(P), DutyLogic.areas("guard", null, gateIds = emptyList(), patrolAssignedToday = 0).areas)
        assertEquals(setOf(P), DutyLogic.areas("security", null).areas)
        assertEquals(setOf(P), DutyLogic.areas(" GUARD ", DutyInfo()).areas)
    }

    @Test fun unknownRoleWithNothingIsNone() {
        assertTrue(DutyLogic.areas("escort", null).areas.isEmpty())
    }

    @Test fun selectAreaKeepsCurrentWhenStillThere() {
        val both = DutyLogic.areas("guard", info(listOf("gate", "patrol")))
        assertEquals(P, DutyLogic.selectArea(P, both))
        assertEquals(G, DutyLogic.selectArea(null, both))
        val gateOnly = DutyLogic.areas("guard", info(listOf("gate")))
        assertEquals(G, DutyLogic.selectArea(P, gateOnly))
        assertNull(DutyLogic.selectArea(P, DutyLogic.areas("guard", info(emptyList()))))
    }

    @Test fun gateChooser() {
        val one = listOf(DutyGate("G-MAIN", "Main Gate"))
        val two = one + DutyGate("G-PED", "Pedestrian Gate")
        assertFalse(DutyLogic.needsGateChooser(one))
        assertTrue(DutyLogic.needsGateChooser(two))
        assertTrue(DutyLogic.needsGateChooser(two + DutyGate("G-MAIN", "Main Gate")))
        assertFalse(DutyLogic.needsGateChooser(one + DutyGate("G-MAIN", "Main Gate")))
        assertEquals("G-MAIN", DutyLogic.defaultGate(two)?.id)
        assertNull(DutyLogic.defaultGate(emptyList()))
    }

    @Test fun revisionChange() {
        assertTrue(DutyLogic.revisionChanged("a", "b"))
        assertFalse(DutyLogic.revisionChanged("a", "a"))
        assertFalse(DutyLogic.revisionChanged(null, "b"))
        assertFalse(DutyLogic.revisionChanged("a", null))
    }

    @Test fun errorCodes() {
        assertTrue(DutyLogic.isDutyError("NO_GATE_DUTY")); assertTrue(DutyLogic.isDutyError("no_patrol_duty"))
        assertFalse(DutyLogic.isDutyError("FORBIDDEN"))
        assertTrue(DutyLogic.isNotClockedIn("NOT_CLOCKED_IN"))
        assertFalse(DutyLogic.isNotClockedIn("ALREADY_CHECKED_IN"))
    }

    @Test fun needsPatrolSummaryOnlyWithoutServerAnswer() {
        assertTrue(DutyLogic.needsPatrolSummary("guard", null))
        assertFalse(DutyLogic.needsPatrolSummary("guard", info(listOf("gate"))))
        assertFalse(DutyLogic.needsPatrolSummary("host", null))
    }

    @Test fun copy() {
        assertEquals("No duty assigned today. Ask your supervisor.", DutyLogic.NO_DUTY_TEXT)
        assertEquals("Your duty was updated. Tap to refresh.", DutyLogic.DUTY_UPDATED_TEXT)
        assertEquals("Face verified", DutyLogic.VERIFIED_TITLE)
        assertEquals("Your face is verified. Welcome!", DutyLogic.VERIFIED_BODY)
    }

    // ---- JSON shapes of Backend's frozen contract ----
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test fun parsesDutyMe() {
        val raw = """{"dutyDate":"2026-10-01","serverTime":"2026-10-01T18:00:00+05:30","hasDuty":true,"dutyTypes":["gate","patrol"],
          "primaryHome":"both","homeOrder":["gate","patrol"],"dutyRevision":"7c1e9a4b02d35f68",
          "assignments":[{"id":"DUT-0001","type":"gate","gateId":"G-MAIN","gateName":"Main Gate","routeName":null,"shiftName":"Day","shiftStart":"06:00","shiftEnd":"14:00","inForce":true,"source":"admin"}],
          "gates":[{"id":"G-MAIN","name":"Main Gate"}],"patrol":{"assignmentIds":["PA-0007"],"routeNames":["Main Round"],"count":1},
          "legacyGateIds":["G-MAIN"],"meta":{"watermark":"DEMO"},"newFutureKey":1}"""
        val m = json.decodeFromString(DutyMe.serializer(), raw)
        assertEquals(listOf("gate", "patrol"), m.dutyTypes)
        assertEquals("Main Gate", m.assignments.first().gateName)
        assertEquals(1, m.patrol?.count)
        val r = DutyLogic.areas("guard", DutyInfo.from(m))
        assertEquals(setOf(G, P), r.areas)
        assertEquals("G-MAIN", DutyLogic.defaultGate(m.gates)?.id)
    }

    @Test fun parsesNoDutyAndEmptyBody() {
        val m = json.decodeFromString(DutyMe.serializer(), """{"hasDuty":false,"dutyTypes":[],"primaryHome":"none","assignments":[],"gates":[]}""")
        assertTrue(DutyLogic.areas("guard", DutyInfo.from(m)).areas.isEmpty())
        val empty = json.decodeFromString(DutyMe.serializer(), "{}")
        assertFalse(DutyInfo.from(empty)!!.present)
    }

    @Test fun loginMeTodayAndRowCarryDutyFields() {
        val login = json.decodeFromString(LoginResponse.serializer(),
            """{"accessToken":"t","user":{"id":"U","schoolId":"S","role":"guard","displayName":"G","dutyTypes":["gate"],"primaryHome":"gate","hasDuty":true,"dutyRevision":"r","dutyAssignments":[{"id":"D1","type":"gate","gateId":"G-MAIN","gateName":"Main Gate"}]},"dutyTypes":["gate"],"primaryHome":"gate"}""")
        assertEquals(listOf("gate"), login.user?.dutyTypes)
        assertEquals("Main Gate", login.user?.dutyAssignments?.first()?.gateName)
        assertEquals(listOf("gate"), login.dutyTypes)
        val today = json.decodeFromString(TodayAttendance.serializer(), """{"attendanceStatus":"NONE","dutyTypes":["patrol"],"primaryHome":"patrol"}""")
        assertEquals(listOf("patrol"), today.dutyTypes)
        val row = json.decodeFromString(AttendanceRow.serializer(),
            """{"id":"GA-1","gateId":"G-MAIN","gateName":"Main Gate","dutyTypes":["gate"],"dutyGateId":"G-MAIN","dutyGateName":"Main Gate","dutyAssignmentIds":["DUT-1"],"noDuty":false}""")
        assertEquals("Main Gate", row.dutyGateName); assertEquals(false, row.noDuty)
        val old = json.decodeFromString(AttendanceRow.serializer(), """{"id":"GA-2"}""")
        assertNull(old.dutyGateName); assertNull(old.noDuty)
    }
}
