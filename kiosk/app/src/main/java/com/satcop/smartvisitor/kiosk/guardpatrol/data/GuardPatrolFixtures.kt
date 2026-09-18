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
}
