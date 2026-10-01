package com.satcop.smartvisitor.kiosk.teal14

import com.satcop.smartvisitor.kiosk.data.model.DutyCompact
import com.satcop.smartvisitor.kiosk.data.model.DutyGate
import com.satcop.smartvisitor.kiosk.ui.KioskUiState
import com.satcop.smartvisitor.kiosk.ui.duty.DutyArea
import com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo
import com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic
import com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.DutyChange
import com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.ShiftInfo
import com.satcop.smartvisitor.kiosk.ui.newActionsEnabled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Duty1078Test {
    private val G = DutyArea.GATE
    private val P = DutyArea.PATROL
    private fun a(type: String, inForce: Boolean?, name: String? = "Day", s: String? = "06:00", e: String? = "14:00", gate: String? = "Main Gate") =
        DutyCompact(id = "x", type = type, gateId = if (type == "gate") "G-MAIN" else null, gateName = if (type == "gate") gate else null,
            shiftName = name, shiftStart = s, shiftEnd = e, inForce = inForce)
    private fun res(vararg ar: DutyArea) = DutyLogic.DutyResult(ar.toSet(), true)

    // ---- (1) shift not active ----
    @Test fun noticeText() {
        assertEquals("Your shift is not active now (Day 06:00–14:00).", DutyLogic.shiftNoticeText(ShiftInfo("Day", "06:00", "14:00")))
        assertEquals("Your shift is not active now (06:00–14:00).", DutyLogic.shiftNoticeText(ShiftInfo(null, "06:00", "14:00")))
        assertEquals("Your shift is not active now (06:00–14:00).", DutyLogic.shiftNoticeText(ShiftInfo("  ", "06:00:00", "14:00:00")))
        assertEquals("Your shift is not active now (Day 06:00–14:00).", DutyLogic.shiftNoticeText(ShiftInfo(slot = "Day 06:00–14:00")))
        assertEquals("Your shift is not active now.", DutyLogic.shiftNoticeText(ShiftInfo()))
        assertEquals("Your shift is not active now.", DutyLogic.shiftNoticeText(null))
    }

    @Test fun allOutOfForceGivesNotice() {
        val n = DutyLogic.shiftNotices(listOf(a("gate", false)))
        assertEquals(mapOf(G to "Your shift is not active now (Day 06:00–14:00)."), n)
    }

    @Test fun anyInForceMeansNoNotice() {
        assertTrue(DutyLogic.shiftNotices(listOf(a("gate", false), a("gate", true, "Evening", "14:00", "22:00"))).isEmpty())
    }

    @Test fun missingOrNullInForceIsInForce() {
        assertTrue(DutyLogic.shiftNotices(listOf(a("gate", null))).isEmpty())
        assertTrue(DutyLogic.shiftNotices(listOf(a("gate", false), a("gate", null))).isEmpty())
        assertTrue(DutyLogic.shiftNotices(null).isEmpty())
        assertTrue(DutyLogic.shiftNotices(emptyList()).isEmpty())
    }

    @Test fun noticeIsPerArea() {
        val n = DutyLogic.shiftNotices(listOf(a("gate", true), a("patrol", false, null, "17:00", "18:00")))
        assertEquals(setOf(P), n.keys)
        assertEquals("Your shift is not active now (17:00–18:00).", n[P])
    }

    @Test fun firstAssignmentOfAreaIsShown() {
        val n = DutyLogic.shiftNotices(listOf(a("gate", false, "Day", "06:00", "14:00"), a("gate", false, "Evening", "14:00", "22:00")))
        assertEquals("Your shift is not active now (Day 06:00–14:00).", n[G])
    }

    @Test fun newActionsOnlyOffForTheAreaOutsideItsShift() {
        val n = mapOf(G to "x")
        assertFalse(DutyLogic.newActionsEnabled(G, n))
        assertTrue(DutyLogic.newActionsEnabled(P, n))
        assertTrue(DutyLogic.newActionsEnabled(null, n))
        assertTrue(DutyLogic.newActionsEnabled(G, emptyMap()))
        assertFalse(KioskUiState(activeArea = G, shiftNotices = n).newActionsEnabled())
        assertTrue(KioskUiState(activeArea = P, shiftNotices = n).newActionsEnabled())
        assertTrue(KioskUiState().newActionsEnabled())
    }

    // ---- error order and OUTSIDE_SHIFT ----
    @Test fun dutyCodeOrder() {
        assertEquals("NOT_CLOCKED_IN", DutyLogic.dutyCodeOf("NOT_CLOCKED_IN"))
        assertEquals("NOT_CLOCKED_IN", DutyLogic.dutyCodeOf("NO_GATE_DUTY", mapOf("reasonCode" to "NO_GATE_DUTY", "reason" to "NOT_CLOCKED_IN")))
        assertEquals("NOT_CLOCKED_IN", DutyLogic.dutyCodeOf("FORBIDDEN", mapOf("reasonCode" to "NOT_CLOCKED_IN")))
        assertEquals("NO_GATE_DUTY", DutyLogic.dutyCodeOf("NO_GATE_DUTY", mapOf("reason" to "OUTSIDE_SHIFT")))
        assertEquals("NO_PATROL_DUTY", DutyLogic.dutyCodeOf("forbidden", mapOf("reasonCode" to "no_patrol_duty")))
        assertEquals("NO_GATE_DUTY", DutyLogic.dutyCodeOf("GATE_NOT_ON_DUTY", mapOf("reasonCode" to "NO_GATE_DUTY")))
        assertEquals("GATE_NOT_ON_DUTY", DutyLogic.dutyCodeOf("GATE_NOT_ON_DUTY"))
        assertNull(DutyLogic.dutyCodeOf("FACE_REQUIRED"))
        assertNull(DutyLogic.dutyCodeOf(null))
    }

    @Test fun planNotClockedInWinsOverEverything() {
        val p = DutyLogic.plan("NO_GATE_DUTY", mapOf("reasonCode" to "NO_GATE_DUTY", "reason" to "NOT_CLOCKED_IN"))
        assertTrue(p.recheckAttendance); assertFalse(p.rereadDuty); assertNull(p.outsideShift)
        val q = DutyLogic.plan("NOT_CLOCKED_IN", mapOf("reasonCode" to "NOT_CLOCKED_IN", "reason" to "OUTSIDE_SHIFT"))
        assertTrue(q.recheckAttendance); assertNull(q.outsideShift)
    }

    @Test fun planNoDutyRereadsDuty() {
        val p = DutyLogic.plan("NO_GATE_DUTY", mapOf("reasonCode" to "NO_GATE_DUTY", "reason" to "NO_DUTY"))
        assertFalse(p.recheckAttendance); assertTrue(p.rereadDuty); assertNull(p.outsideShift)
    }

    @Test fun planOutsideShiftAfterReread() {
        val d = mapOf("reasonCode" to "NO_GATE_DUTY", "reason" to "OUTSIDE_SHIFT", "shift.name" to "Day", "shift.start" to "06:00", "shift.end" to "14:00", "shift.slot" to "Day 06:00–14:00")
        val p = DutyLogic.plan("NO_GATE_DUTY", d)
        assertTrue(p.rereadDuty)
        assertEquals(G, p.outsideShift?.first)
        assertEquals("Your shift is not active now (Day 06:00–14:00).", DutyLogic.shiftNoticeText(p.outsideShift?.second))
        val pp = DutyLogic.plan("NO_PATROL_DUTY", d - "shift.name" - "shift.start" - "shift.end")
        assertEquals(P, pp.outsideShift?.first)
        assertEquals("Your shift is not active now (Day 06:00–14:00).", DutyLogic.shiftNoticeText(pp.outsideShift?.second))
    }

    @Test fun planOutsideShiftWithoutShiftDetails() {
        val p = DutyLogic.plan("NO_GATE_DUTY", mapOf("reason" to "OUTSIDE_SHIFT"))
        assertEquals("Your shift is not active now.", DutyLogic.shiftNoticeText(p.outsideShift?.second))
    }

    @Test fun planOtherCodesDoNothing() {
        val p = DutyLogic.plan("FACE_REQUIRED")
        assertFalse(p.recheckAttendance); assertFalse(p.rereadDuty); assertNull(p.outsideShift)
    }

    @Test fun gates403NeverDrivesTheUi() {
        assertFalse(DutyLogic.shouldEmitDutyEvent("/v1/gates", "NO_GATE_DUTY"))
        assertFalse(DutyLogic.shouldEmitDutyEvent("/v1/gates/", "NOT_CLOCKED_IN"))
        assertTrue(DutyLogic.shouldEmitDutyEvent("/v1/visits", "NO_GATE_DUTY"))
        assertTrue(DutyLogic.shouldEmitDutyEvent("/v1/visits", "NOT_CLOCKED_IN"))
        assertFalse(DutyLogic.shouldEmitDutyEvent("/v1/visits", null))
    }

    @Test fun errorNoticeStaysUntilExplicitInForce() {
        val err = mapOf(G to "txt")
        // assignments say nothing explicit (null) -> keep the server's word
        assertEquals(err, DutyLogic.mergeNotices(emptyMap(), err, listOf(a("gate", null))))
        // assignments explicitly in force -> clear
        assertTrue(DutyLogic.mergeNotices(emptyMap(), err, listOf(a("gate", true))).isEmpty())
        // computed wins over the error text
        assertEquals(mapOf(G to "calc"), DutyLogic.mergeNotices(mapOf(G to "calc"), err, listOf(a("gate", false))))
    }

    // ---- (2) change handling ----
    @Test fun fromNoDutyScreenAutoSwitches() {
        assertEquals(DutyChange.AUTO_SWITCH, DutyLogic.change(emptySet(), "r1", res(G), "r2"))
        assertEquals(DutyChange.AUTO_SWITCH, DutyLogic.change(emptySet(), null, res(P), "r2"))
        assertEquals(DutyChange.AUTO_SWITCH, DutyLogic.change(emptySet(), "r1", res(G, P), "r2"))
    }

    @Test fun noDutyStaysNoDutyIsNoChange() {
        assertEquals(DutyChange.NONE, DutyLogic.change(emptySet(), "r1", res(), "r1"))
        assertEquals(DutyChange.NONE, DutyLogic.change(emptySet(), "r1", res(), "r2").let { if (it == DutyChange.BANNER) DutyChange.NONE else it })
    }

    @Test fun otherTransitionsAreBanners() {
        assertEquals(DutyChange.BANNER, DutyLogic.change(setOf(G), "r1", res(G, P), "r2"))   // gate -> both
        assertEquals(DutyChange.BANNER, DutyLogic.change(setOf(G, P), "r1", res(P), "r2"))   // removal
        assertEquals(DutyChange.BANNER, DutyLogic.change(setOf(G), "r1", res(), "r2"))       // duty removed -> banner, not silent
        assertEquals(DutyChange.BANNER, DutyLogic.change(setOf(P), "r1", res(G), "r2"))      // patrol -> gate
        assertEquals(DutyChange.BANNER, DutyLogic.change(setOf(G), null, res(P), "r2"))      // no applied revision, areas differ
    }

    @Test fun sameRevisionAndSameAreasNoChange() {
        assertEquals(DutyChange.NONE, DutyLogic.change(setOf(G), "r1", res(G), "r1"))
        assertEquals(DutyChange.NONE, DutyLogic.change(setOf(G), null, res(G), "r1"))
        assertEquals(DutyChange.NONE, DutyLogic.change(null, "r1", res(G), "r2"))
    }

    @Test fun assignedToastText() {
        val gates = listOf(DutyGate("G-MAIN", "Main Gate"))
        assertEquals("Duty assigned: Main Gate", DutyLogic.assignedToast(res(G), gates))
        assertEquals("Duty assigned: Main Gate", DutyLogic.assignedToast(res(G, P), gates))
        assertEquals("Duty assigned: Patrol", DutyLogic.assignedToast(res(P), gates))
        assertEquals("Duty assigned: Main Gate", DutyLogic.assignedToast(res(G), emptyList(), listOf(a("gate", true))))
        assertEquals("Duty assigned: Gate desk", DutyLogic.assignedToast(res(G), emptyList()))
        assertEquals("Duty assigned: Patrol", DutyLogic.assignedToast(res(P), gates, listOf(a("gate", true))))
    }

    // ---- (4) trust /duty/me ----
    @Test fun roleGateHasDutyFalseIsNoDuty() {
        for (role in listOf("gate", "Gate", "gate_staff", "gate-staff")) {
            val r = DutyLogic.areas(role, DutyInfo(emptyList(), "none", false, "r"), gateIds = listOf("G-MAIN"), patrolAssignedToday = 3)
            assertTrue(role, r.areas.isEmpty())
        }
        assertTrue(DutyLogic.areas("gate", DutyInfo(hasDuty = false)).areas.isEmpty())
        assertTrue(DutyLogic.areas("gate", DutyInfo(primaryHome = "none")).areas.isEmpty())
    }

    @Test fun roleGateFieldAbsentStillFallsBackToGate() {
        assertEquals(setOf(G), DutyLogic.areas("gate", null).areas)
        assertEquals(setOf(G), DutyLogic.areas("gate", DutyInfo()).areas)
        assertEquals(setOf(G, P), DutyLogic.areas("gate_staff", null, patrolAssignedToday = 1).areas)
        assertFalse(DutyLogic.areas("gate", null).fromServer)
    }

    @Test fun guardNoDutyFieldPresentIsPlain() {
        assertTrue(DutyLogic.areas("guard", DutyInfo(emptyList(), "none", false, "r")).areas.isEmpty())
        assertEquals(setOf(P), DutyLogic.areas("guard", null).areas)
    }

    @Test fun patrolSummaryNotNeededOnceFieldPresent() {
        assertFalse(DutyLogic.needsPatrolSummary("gate", DutyInfo(emptyList(), "none", false)))
        assertTrue(DutyLogic.needsPatrolSummary("gate", null))
    }
}
