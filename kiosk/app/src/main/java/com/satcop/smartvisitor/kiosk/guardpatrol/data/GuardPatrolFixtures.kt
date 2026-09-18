package com.satcop.smartvisitor.kiosk.guardpatrol.data

/**
 * Fixtures aligned to demos/guard-patrol/fixtures.js
 * School: Demo Public School — Campus A; 6 checkpoints; 3 active templates.
 */
object GuardPatrolFixtures {
    const val SCHOOL_ID = "SCH-DEMO-01"
    const val DEVICE_ID = "guard-phone-G1"
    const val DEFAULT_GUARD_ID = "G1"

    val school = School(
        id = SCHOOL_ID,
        name = "Demo Public School — Campus A",
    )

    val guards = listOf(
        Guard(id = "G1", name = "Guard G1", role = "Guard"),
        Guard(id = "SH1", name = "Priya Nair", role = "Security Head"),
    )

    val checkpoints = listOf(
        Checkpoint("cp-main", SCHOOL_ID, "Main gate", "Main Gate", TagType.QR, true, 18.5204, 73.8567),
        Checkpoint("cp-ped", SCHOOL_ID, "Pedestrian", "Pedestrian Gate", TagType.QR, true, 18.5208, 73.8572),
        Checkpoint("cp-staff", SCHOOL_ID, "Staff", "Staff Gate", TagType.NFC, true, 18.5210, 73.8578),
        Checkpoint("cp-bus", SCHOOL_ID, "Bus Bay", "Bus Bay", TagType.QR, true, 18.5215, 73.8565),
        Checkpoint("cp-play", SCHOOL_ID, "Playground", "Playground", TagType.NFC, true, 18.5218, 73.8575),
        Checkpoint("cp-park", SCHOOL_ID, "Parking", "Parking", TagType.QR, true, 18.5202, 73.8580),
    )

    val templates = listOf(
        RoundTemplate(
            id = "tpl-evening",
            schoolId = SCHOOL_ID,
            name = "Evening perimeter",
            checkpointIds = listOf("cp-main", "cp-ped", "cp-staff", "cp-bus", "cp-play", "cp-park"),
            ordered = true,
            expectedDurationMin = 45,
            active = true,
        ),
        RoundTemplate(
            id = "tpl-spot",
            schoolId = SCHOOL_ID,
            name = "Spot check",
            checkpointIds = listOf("cp-main", "cp-staff", "cp-bus", "cp-park"),
            ordered = false,
            expectedDurationMin = 20,
            active = true,
        ),
        RoundTemplate(
            id = "tpl-short",
            schoolId = SCHOOL_ID,
            name = "Quick gate sweep",
            checkpointIds = listOf("cp-main", "cp-ped", "cp-staff"),
            ordered = true,
            expectedDurationMin = 5,
            active = true,
        ),
    )

    fun checkpoint(id: String): Checkpoint? = checkpoints.find { it.id == id }

    fun template(id: String): RoundTemplate? = templates.find { it.id == id }

    fun activeTemplates(): List<RoundTemplate> = templates.filter { it.active }

    fun guard(id: String): Guard? = guards.find { it.id == id }

    /** School flag — when true, hide/disable self-start template picker (AC-AP7). Default false. */
    const val REQUIRE_ASSIGNMENT_DEFAULT = false

    fun todayDutyDateIst(): String =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Kolkata")).toString()

    /**
     * Demo assignments for G1 today (fixture-first until GET /patrol-assignments returns 200).
     * Seed: tpl-evening + tpl-spot · SCH-DEMO-01 · status assigned.
     */
    fun assignmentsForToday(guardId: String = DEFAULT_GUARD_ID): List<PatrolAssignment> {
        val today = todayDutyDateIst()
        if (guardId != DEFAULT_GUARD_ID) return emptyList()
        return listOf(
            PatrolAssignment(
                id = "asg-demo-evening-$today",
                schoolId = SCHOOL_ID,
                templateId = "tpl-evening",
                guardId = DEFAULT_GUARD_ID,
                dutyDate = today,
                shiftStart = "17:00",
                shiftEnd = "19:00",
                status = AssignmentStatus.ASSIGNED,
                assignedBy = "SH1",
                assignedAtEpochMs = System.currentTimeMillis() - 3_600_000L,
                notes = "Evening perimeter — demo seed",
            ),
            PatrolAssignment(
                id = "asg-demo-spot-$today",
                schoolId = SCHOOL_ID,
                templateId = "tpl-spot",
                guardId = DEFAULT_GUARD_ID,
                dutyDate = today,
                shiftStart = "14:00",
                shiftEnd = "15:00",
                status = AssignmentStatus.ASSIGNED,
                assignedBy = "SH1",
                assignedAtEpochMs = System.currentTimeMillis() - 7_200_000L,
                notes = "Spot check — demo seed",
            ),
        )
    }

    fun assignment(id: String): PatrolAssignment? =
        assignmentsForToday().find { it.id == id }
}
