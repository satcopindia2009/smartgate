package com.satcop.smartvisitor.kiosk.guardpatrol.data

/** F3 Guard Patrol entities — camelCase aligned to API slice + demos/guard-patrol/fixtures.js */

enum class TagType { QR, NFC }

enum class RoundStatus {
    IN_PROGRESS,
    COMPLETED,
    PARTIAL,
    MISSED;

    fun display(): String = when (this) {
        IN_PROGRESS -> "In progress"
        COMPLETED -> "Completed"
        PARTIAL -> "Partial"
        MISSED -> "Missed"
    }
}

data class Checkpoint(
    val id: String,
    val schoolId: String,
    val name: String,
    val gateOrZone: String,
    val tagType: TagType,
    val active: Boolean = true,
    val lat: Double? = null,
    val lng: Double? = null,
)

data class RoundTemplate(
    val id: String,
    val schoolId: String,
    val name: String,
    val checkpointIds: List<String>,
    val ordered: Boolean,
    val expectedDurationMin: Int,
    val active: Boolean = true,
)

data class Scan(
    val checkpointId: String,
    val scannedAtEpochMs: Long,
    val deviceId: String,
    val lat: Double? = null,
    val lng: Double? = null,
    val offCampusSuspect: Boolean = false,
    val outOfOrder: Boolean = false,
)

data class RoundInstance(
    val id: String,
    val schoolId: String,
    val templateId: String,
    val guardId: String,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val status: RoundStatus = RoundStatus.IN_PROGRESS,
    val scans: List<Scan> = emptyList(),
    /** Linked when Guard starts from an Assigned-today row (POST /rounds assignmentId). */
    val assignmentId: String? = null,
)

data class Guard(
    val id: String,
    val name: String,
    val role: String,
)

data class School(
    val id: String,
    val name: String,
)

sealed class ScanResult {
    data class Accepted(
        val scan: Scan,
        val outOfOrder: Boolean,
        val message: String,
    ) : ScanResult()

    data class Rejected(
        val reason: String,
        val message: String,
    ) : ScanResult()
}

enum class AssignmentStatus {
    ASSIGNED,
    STARTED,
    COMPLETED,
    MISSED,
    CANCELLED;

    fun display(): String = when (this) {
        ASSIGNED -> "Assigned"
        STARTED -> "Started"
        COMPLETED -> "Completed"
        MISSED -> "Missed"
        CANCELLED -> "Cancelled"
    }

    fun apiValue(): String = name.lowercase()
}

/**
 * Admin-assigned patrol duty for a guard on a duty date (school TZ Asia/Kolkata).
 * AC-AP1–7 — Mobile consume of GET /patrol-assignments.
 */
data class PatrolAssignment(
    val id: String,
    val schoolId: String,
    val templateId: String,
    val guardId: String,
    val dutyDate: String,
    val shiftStart: String? = null,
    val shiftEnd: String? = null,
    val status: AssignmentStatus = AssignmentStatus.ASSIGNED,
    val roundId: String? = null,
    val assignedBy: String? = null,
    val assignedAtEpochMs: Long? = null,
    val notes: String? = null,
)
