package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

/** GET /attendance/me/today -> attendanceStatus NONE | PRESENT | CLOCKED_OUT (Backend contract 2026-09-30). */
enum class AttendanceState {
    NONE, PRESENT, CLOCKED_OUT;

    companion object {
        fun parse(raw: String?): AttendanceState =
            entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: NONE
    }
}

/** Subset of the attendance row the app shows. Unknown fields are ignored (contract has many aliases). */
@Serializable
data class AttendanceRow(
    val id: String? = null,
    val dutyDate: String? = null,
    val checkInAt: String? = null,
    val checkOutAt: String? = null,
    val attendanceStatus: String? = null,
    val geofenceName: String? = null,
    val geofenceStatus: String? = null,
    val distanceM: Double? = null,
    val accuracyM: Double? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val outServerTime: String? = null,
    val outGeofenceStatus: String? = null,
    val warn: String? = null,
    val idempotent: Boolean = false,
)

@Serializable
data class TodayAttendance(
    val attendance: AttendanceRow? = null,
    val attendanceStatus: String = "NONE",
    val message: String? = null,
    val dutyDate: String? = null,
    val canCheckIn: Boolean = false,
    val canClockOut: Boolean = false,
) {
    val state: AttendanceState get() = AttendanceState.parse(attendanceStatus)
}

/**
 * One request carries photo + lat/lng/accuracy + device time, all from the SAME attempt
 * (attemptId). The server uses its own clock for the stored time; [capturedAt] is stored separately.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class AttendanceRequest(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val imageBase64: String? = null,
    val lat: Double,
    val lng: Double,
    val accuracyM: Double,
    val capturedAt: String,
    val gpsMissing: Boolean = false,
    val attemptId: String,
)
