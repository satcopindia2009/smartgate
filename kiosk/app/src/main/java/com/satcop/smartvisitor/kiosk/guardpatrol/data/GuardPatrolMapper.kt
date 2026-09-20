package com.satcop.smartvisitor.kiosk.guardpatrol.data

import java.time.OffsetDateTime

object GuardPatrolMapper {
    fun toDomain(dto: PatrolCheckpointDto): Checkpoint = Checkpoint(
        id = dto.id,
        schoolId = dto.schoolId,
        name = dto.name,
        gateOrZone = dto.gateOrZone,
        tagType = when (dto.tagType.uppercase()) {
            "NFC" -> TagType.NFC
            else -> TagType.QR
        },
        active = dto.active,
        lat = dto.lat,
        lng = dto.lng,
        tagPayload = dto.tagPayload?.takeIf { it.isNotBlank() } ?: "SGCP:${dto.id}",
    )

    fun toDomain(dto: PatrolTemplateDto): RoundTemplate = RoundTemplate(
        id = dto.id,
        schoolId = dto.schoolId,
        name = dto.name,
        checkpointIds = dto.checkpointIds,
        ordered = dto.ordered,
        expectedDurationMin = dto.expectedDurationMin,
        active = dto.active,
    )

    fun toDomain(dto: PatrolRoundDto, assignmentId: String? = null): RoundInstance = RoundInstance(
        id = dto.id,
        schoolId = dto.schoolId,
        templateId = dto.templateId,
        guardId = dto.guardId,
        startedAtEpochMs = parseEpoch(dto.startedAt) ?: System.currentTimeMillis(),
        completedAtEpochMs = parseEpoch(dto.completedAt),
        status = parseStatus(dto.status),
        scans = dto.scans.map { toDomain(it) },
        assignmentId = assignmentId,
    )

    fun toDomain(dto: PatrolAssignmentDto): PatrolAssignment = PatrolAssignment(
        id = dto.id,
        schoolId = dto.schoolId,
        templateId = dto.templateId,
        guardId = dto.guardId,
        dutyDate = dto.dutyDate,
        shiftStart = dto.shiftStart,
        shiftEnd = dto.shiftEnd,
        status = parseAssignmentStatus(dto.status),
        roundId = dto.roundId,
        assignedBy = dto.assignedBy,
        assignedAtEpochMs = parseEpoch(dto.assignedAt),
        notes = dto.notes,
    )

    fun parseAssignmentStatus(raw: String?): AssignmentStatus = when (raw?.lowercase()) {
        "started" -> AssignmentStatus.STARTED
        "completed" -> AssignmentStatus.COMPLETED
        "partial" -> AssignmentStatus.PARTIAL
        "missed" -> AssignmentStatus.MISSED
        "cancelled" -> AssignmentStatus.CANCELLED
        else -> AssignmentStatus.ASSIGNED
    }

    fun toDomain(dto: PatrolScanDto): Scan = Scan(
        checkpointId = dto.checkpointId,
        scannedAtEpochMs = parseEpoch(dto.scannedAt) ?: System.currentTimeMillis(),
        deviceId = dto.deviceId ?: LiveGuardPatrolApi.DEMO_DEVICE_ID,
        lat = dto.lat,
        lng = dto.lng,
        offCampusSuspect = dto.offCampusSuspect,
        outOfOrder = dto.outOfOrder,
    )

    fun parseStatus(raw: String?): RoundStatus = when (raw?.lowercase()) {
        "completed" -> RoundStatus.COMPLETED
        "partial" -> RoundStatus.PARTIAL
        "missed" -> RoundStatus.MISSED
        else -> RoundStatus.IN_PROGRESS
    }

    fun parseEpoch(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }.getOrNull()
    }
}
