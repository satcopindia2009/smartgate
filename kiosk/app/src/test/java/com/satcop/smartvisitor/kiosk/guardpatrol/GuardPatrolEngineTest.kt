package com.satcop.smartvisitor.kiosk.guardpatrol

import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolEngine
import com.satcop.smartvisitor.kiosk.guardpatrol.data.GuardPatrolFixtures
import com.satcop.smartvisitor.kiosk.guardpatrol.data.RoundStatus
import com.satcop.smartvisitor.kiosk.guardpatrol.data.ScanResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardPatrolEngineTest {

    private val evening = GuardPatrolFixtures.template("tpl-evening")!!
    private val spot = GuardPatrolFixtures.template("tpl-spot")!!

    @Test
    fun startRound_setsInProgressWithGuardG1() {
        val round = GuardPatrolEngine.startRound(evening, nowMs = 1_000L)
        assertEquals("tpl-evening", round.templateId)
        assertEquals("G1", round.guardId)
        assertEquals(RoundStatus.IN_PROGRESS, round.status)
        assertEquals(1_000L, round.startedAtEpochMs)
        assertTrue(round.scans.isEmpty())
    }

    @Test
    fun fixtures_threeActiveTemplatesWithExpectedMeta() {
        val active = GuardPatrolFixtures.activeTemplates()
        assertEquals(3, active.size)
        val eveningTpl = active.first { it.id == "tpl-evening" }
        assertTrue(eveningTpl.ordered)
        assertEquals(45, eveningTpl.expectedDurationMin)
        assertEquals(6, eveningTpl.checkpointIds.size)
        val spotTpl = active.first { it.id == "tpl-spot" }
        assertFalse(spotTpl.ordered)
        assertEquals(20, spotTpl.expectedDurationMin)
        val shortTpl = active.first { it.id == "tpl-short" }
        assertEquals(5, shortTpl.expectedDurationMin)
        assertEquals(3, shortTpl.checkpointIds.size)
    }

    @Test
    fun orderedSkipAhead_warnsButCountsWithOutOfOrder() {
        var round = GuardPatrolEngine.startRound(evening, nowMs = 10_000L)
        val (r1, res1) = GuardPatrolEngine.simulateScan(round, evening, "cp-main", nowMs = 10_100L)
        assertTrue(res1 is ScanResult.Accepted)
        assertFalse((res1 as ScanResult.Accepted).outOfOrder)
        round = r1

        val (r2, res2) = GuardPatrolEngine.simulateScan(round, evening, "cp-staff", nowMs = 10_200L)
        assertTrue(res2 is ScanResult.Accepted)
        assertTrue((res2 as ScanResult.Accepted).outOfOrder)
        assertEquals(2, GuardPatrolEngine.uniqueScannedCount(r2))
    }

    @Test
    fun twoMinuteDedupe_ignoresRescan() {
        var round = GuardPatrolEngine.startRound(evening, nowMs = 20_000L)
        val (r1, _) = GuardPatrolEngine.simulateScan(round, evening, "cp-main", nowMs = 20_100L)
        round = r1
        val (r2, res2) = GuardPatrolEngine.simulateScan(round, evening, "cp-main", nowMs = 20_100L + 60_000L)
        assertTrue(res2 is ScanResult.Rejected)
        assertEquals("dedupe", (res2 as ScanResult.Rejected).reason)
        assertEquals(1, r2.scans.size)
    }

    @Test
    fun unordered_neverSetsOutOfOrder() {
        var round = GuardPatrolEngine.startRound(spot, nowMs = 30_000L)
        val (r1, res1) = GuardPatrolEngine.simulateScan(round, spot, "cp-park", nowMs = 30_100L)
        assertTrue(res1 is ScanResult.Accepted)
        assertFalse((res1 as ScanResult.Accepted).outOfOrder)
        round = r1
        val (_, res2) = GuardPatrolEngine.simulateScan(round, spot, "cp-main", nowMs = 30_200L)
        assertTrue(res2 is ScanResult.Accepted)
        assertFalse((res2 as ScanResult.Accepted).outOfOrder)
    }

    @Test
    fun endRound_completedPartialMissed() {
        var round = GuardPatrolEngine.startRound(spot, nowMs = 40_000L)
        // zero scans → Missed
        var ended = GuardPatrolEngine.endRound(round, spot, nowMs = 41_000L)
        assertEquals(RoundStatus.MISSED, ended.status)

        // one scan → Partial
        val (r1, _) = GuardPatrolEngine.simulateScan(round, spot, "cp-main", nowMs = 40_100L)
        ended = GuardPatrolEngine.endRound(r1, spot, nowMs = 41_000L)
        assertEquals(RoundStatus.PARTIAL, ended.status)

        // all → Completed
        round = r1
        listOf("cp-staff", "cp-bus", "cp-park").forEachIndexed { i, id ->
            val (next, _) = GuardPatrolEngine.simulateScan(round, spot, id, nowMs = 40_200L + i)
            round = next
        }
        ended = GuardPatrolEngine.endRound(round, spot, nowMs = 42_000L)
        assertEquals(RoundStatus.COMPLETED, ended.status)
    }

    @Test
    fun unknownTag_rejected() {
        val round = GuardPatrolEngine.startRound(evening, nowMs = 50_000L)
        val (_, res) = GuardPatrolEngine.simulateScan(round, evening, "cp-not-real", nowMs = 50_100L)
        assertTrue(res is ScanResult.Rejected)
    }

    @Test
    fun assignmentsForToday_g1HasEveningAndSpot() {
        val list = GuardPatrolFixtures.assignmentsForToday("G1")
        assertEquals(2, list.size)
        assertEquals("tpl-evening", list[0].templateId)
        assertEquals("tpl-spot", list[1].templateId)
        assertEquals(GuardPatrolFixtures.todayDutyDateIst(), list[0].dutyDate)
        assertEquals("assigned", list[0].status.apiValue())
        assertTrue(GuardPatrolFixtures.assignmentsForToday("G2").isEmpty())
    }

    @Test
    fun startRound_linksAssignmentId() {
        val round = GuardPatrolEngine.startRound(
            evening,
            assignmentId = "asg-demo-evening-x",
            nowMs = 99L,
        )
        assertEquals("asg-demo-evening-x", round.assignmentId)
    }

}
