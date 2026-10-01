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
    // 1064 result screen. Present today: id (GA-xxxx), serverTime, timestamp (ISO +05:30), guardName, photoKey, photoUrl.
    // Announced by Backend (additive, not yet confirmed): gateName, schoolName, selfieUploaded, guardPhotoUrl.
    val serverTime: String? = null,
    val timestamp: String? = null,
    val guardName: String? = null,
    val photoKey: String? = null,
    val photoUrl: String? = null,
    val gateName: String? = null,
    val schoolName: String? = null,
    val selfieUploaded: Boolean? = null,
    val guardPhotoUrl: String? = null,
    val geofenceMode: String? = null,
    // 1072 geofence (Backend 2026-10-01, LIVE): flat radius + nested block; all optional.
    val geofenceRadiusM: Double? = null,
    val geofence: GeofenceInfo? = null,
    /** Clock-out geofence (Backend): outGeofence nested + outGeofenceRadiusM. */
    val outGeofence: GeofenceInfo? = null,
    val outGeofenceRadiusM: Double? = null,
    val isMock: Boolean? = null,
    // Guard shift (Backend 2026-10-01, additive, null when no shift is assigned).
    val shiftName: String? = null,
    val shiftStartTime: String? = null,
    /** e.g. "Morning · starts 06:30 am" - shown verbatim. */
    val shiftStartDisplay: String? = null,
    val shiftEndTime: String? = null,
    val shiftEndDisplay: String? = null,
    // Clock-in addendum 2026-09-30 (Backend, LIVE): result-screen + status fields, names exactly as the contract.
    val recordId: String? = null,
    val action: String? = null,
    val actionAt: String? = null,
    val actionTimeDisplay: String? = null,
    val gateId: String? = null,
    val gateSource: String? = null,
    // 1077 clock-in snapshot (Backend): the duty at check-in time, never rewritten.
    val dutyTypes: List<String>? = null,
    val dutyGateId: String? = null,
    val dutyGateName: String? = null,
    val dutyAssignmentIds: List<String>? = null,
    val noDuty: Boolean? = null,
    val checkInSelfieUploaded: Boolean? = null,
    val currentStatus: CurrentStatus? = null,
    val missedClockOut: Boolean = false,
    val flags: List<String> = emptyList(),
    val displayStatus: String? = null,
    val profilePhotoUrl: String? = null,
)

/** currentStatus block (Current Status card). Shown as returned; the app derives nothing when it is present. */
@Serializable
data class CurrentStatus(
    val state: String? = null,
    val label: String? = null,
    val text: String? = null,
    val chipColor: String? = null,
    val checkedInAt: String? = null,
    val checkedOutAt: String? = null,
    val checkedInAtDisplay: String? = null,
    val checkedOutAtDisplay: String? = null,
    val gateName: String? = null,
    val nextAction: String? = null,
    val actionLabel: String? = null,
    val shiftComplete: Boolean = false,
    val note: String? = null,
)

@Serializable
data class TodayAttendance(
    val attendance: AttendanceRow? = null,
    val attendanceStatus: String = "NONE",
    val message: String? = null,
    val dutyDate: String? = null,
    val canCheckIn: Boolean = false,
    val canClockOut: Boolean = false,
    // 1077 duty (Backend frozen contract 2026-10-01): additive, null for other roles / until Backend ships it.
    val dutyTypes: List<String>? = null,
    val primaryHome: String? = null,
    val homeOrder: List<String>? = null,
    val hasDuty: Boolean? = null,
    val dutyRevision: String? = null,
    val dutyAssignments: List<DutyCompact>? = null,
    val schoolName: String? = null,
    val guardName: String? = null,
    val gateName: String? = null,
    val gateId: String? = null,
    val guardPhotoUrl: String? = null,
    val selfieUploaded: Boolean? = null,
    val currentStatus: CurrentStatus? = null,
    /** 1072: fence + rules block (mode, radius, centre, accuracy limit). Null when the server does not send it. */
    val geofence: GeofenceInfo? = null,
    // Guard shift (Backend 2026-10-01, additive, null when no shift is assigned).
    val shiftName: String? = null,
    val shiftStartTime: String? = null,
    /** e.g. "Morning · starts 06:30 am" - shown verbatim. */
    val shiftStartDisplay: String? = null,
    val shiftEndTime: String? = null,
    val shiftEndDisplay: String? = null,
    val serverTime: String? = null,
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
    // null lat/lng/accuracy = location unavailable (server treats it as gpsMissing; check-in soft mode only).
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lat: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val lng: Double? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val accuracyM: Double? = null,
    val capturedAt: String,
    val gpsMissing: Boolean = false,
    val attemptId: String,
    /** 1072: true when the OS flags the reading as coming from a mock provider. Server only records/flags it. Omitted otherwise. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val isMock: Boolean? = null,
    /** 1077: gate chosen from the duty gates (only sent when the guard has more than one gate duty). */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val gateId: String? = null,
)
