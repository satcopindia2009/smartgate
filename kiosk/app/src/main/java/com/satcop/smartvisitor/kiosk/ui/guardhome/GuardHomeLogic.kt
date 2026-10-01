package com.satcop.smartvisitor.kiosk.ui.guardhome

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.geo.GpsPolicy
import com.satcop.smartvisitor.kiosk.data.geo.GpsState
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.AttendanceState
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class AttendanceMode { CHECK_IN, CLOCK_OUT }

enum class VisitFilter(val label: String) {
    TODAYS("Today's"), PENDING("Pending"), REJECTED("Rejected"), ALL("All"),
}

data class VisitSummary(val todays: Int, val pending: Int, val rejected: Int, val all: Int) {
    fun countFor(filter: VisitFilter): Int = when (filter) {
        VisitFilter.TODAYS -> todays
        VisitFilter.PENDING -> pending
        VisitFilter.REJECTED -> rejected
        VisitFilter.ALL -> all
    }
}

/** What a guard may see about a visitor: no full ID number, mobile masked to the last 4 digits. */
data class GuardVisitRow(
    val id: String,
    val name: String,
    val code: String,
    val mobileMasked: String,
    val statusLabel: String,
    val status: String,
    val purpose: String,
    val hostLabel: String,
    val timeLabel: String,
    val rejectReason: String?,
)

object GuardHomeLogic {
    val IST: ZoneId = ZoneId.of("Asia/Kolkata")
    const val NOT_PRESENT = "Visitor not present."
    const val MIN_QUERY = 2
    /** Backend caps a /visits date range at 90 days. */
    const val SEARCH_DAYS = 90L

    private val timeFmt = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM, hh:mm a", Locale.ENGLISH)

    fun todayIst(now: Instant = Instant.now()): LocalDate = now.atZone(IST).toLocalDate()

    fun istDateOf(iso: String?): LocalDate? = parse(iso)?.atZoneSameInstant(IST)?.toLocalDate()

    private fun parse(iso: String?): OffsetDateTime? {
        val s = iso?.trim().orEmpty()
        if (s.isEmpty()) return null
        return runCatching { OffsetDateTime.parse(s) }.getOrNull()
            ?: runCatching { java.time.LocalDateTime.parse(s).atZone(IST).toOffsetDateTime() }.getOrNull()
    }

    /** Visits created on the current business date (IST). Never cached: callers re-fetch. */
    fun forDate(visits: List<VisitOut>, today: LocalDate): List<VisitOut> =
        visits.filter { istDateOf(it.createdAt) == today }

    private fun isPending(v: VisitOut) = v.status.equals("pending", true)
    private fun isRejected(v: VisitOut) = v.status.equals("rejected", true)

    /**
     * Four tiles for the current business date. They partition the day so the numbers add up:
     * Today's (admitted: approved / inside / completed) + Pending + Rejected = All.
     */
    fun summary(visits: List<VisitOut>, today: LocalDate): VisitSummary {
        val day = forDate(visits, today)
        val pending = day.count(::isPending)
        val rejected = day.count(::isRejected)
        return VisitSummary(
            todays = day.size - pending - rejected,
            pending = pending,
            rejected = rejected,
            all = day.size,
        )
    }

    fun filtered(visits: List<VisitOut>, today: LocalDate, filter: VisitFilter): List<VisitOut> {
        val day = forDate(visits, today)
        val list = when (filter) {
            VisitFilter.TODAYS -> day.filter { !isPending(it) && !isRejected(it) }
            VisitFilter.PENDING -> day.filter(::isPending)
            VisitFilter.REJECTED -> day.filter(::isRejected)
            VisitFilter.ALL -> day
        }
        return list.sortedByDescending { parse(it.createdAt)?.toInstant() ?: Instant.EPOCH }
    }

    fun searchWindow(today: LocalDate): Pair<String, String> =
        today.minusDays(SEARCH_DAYS - 1).toString() to today.toString()

    fun validQuery(q: String): Boolean = q.trim().length >= MIN_QUERY

    fun maskMobile(mobile: String?): String {
        val digits = mobile.orEmpty().filter { it.isDigit() }
        if (digits.length < 4) return if (digits.isEmpty()) "" else "••••"
        return "••••••" + digits.takeLast(4)
    }

    fun statusLabel(status: String): String = when (status.lowercase()) {
        "pending" -> "Pending"
        "approved" -> "Approved"
        "rejected" -> "Rejected"
        "inside" -> "Checked in"
        "completed", "force_completed" -> "Checked out"
        else -> status.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }

    fun row(v: VisitOut): GuardVisitRow {
        val at = parse(v.timeIn) ?: parse(v.createdAt)
        return GuardVisitRow(
            id = v.id,
            name = v.visitorName?.ifBlank { null } ?: "Visitor",
            code = v.passId?.takeIf { it.isNotBlank() } ?: v.id,
            mobileMasked = maskMobile(v.mobile),
            statusLabel = statusLabel(v.status),
            status = v.status.lowercase(),
            purpose = v.purpose.orEmpty(),
            hostLabel = v.hostId?.takeIf { it.isNotBlank() }?.let { "Host $it" }.orEmpty(),
            timeLabel = at?.atZoneSameInstant(IST)?.format(dateTimeFmt).orEmpty(),
            rejectReason = if (isRejected(v)) v.rejectReason?.takeIf { it.isNotBlank() } else null,
        )
    }

    // --- attendance card text (server state only; nothing cached locally) ---

    fun clockLabel(iso: String?): String =
        parse(iso)?.atZoneSameInstant(IST)?.format(timeFmt).orEmpty()

    fun attendanceHeadline(t: TodayAttendance?): String = when (t?.state ?: AttendanceState.NONE) {
        AttendanceState.NONE -> "Not checked in"
        AttendanceState.PRESENT -> "Checked in"
        AttendanceState.CLOCKED_OUT -> "Clocked out"
    }

    fun attendanceDetail(t: TodayAttendance?): String {
        val row: AttendanceRow? = t?.attendance
        return when (t?.state ?: AttendanceState.NONE) {
            AttendanceState.NONE -> t?.message?.takeIf { it.isNotBlank() } ?: "No attendance yet"
            AttendanceState.PRESENT -> listOf(
                "In ${clockLabel(row?.checkInAt)}".trim(),
                placeLabel(row?.geofenceName, row?.geofenceStatus),
            ).filter { it.isNotBlank() }.joinToString(" · ")
            AttendanceState.CLOCKED_OUT -> listOf(
                "In ${clockLabel(row?.checkInAt)}".trim(),
                "Out ${clockLabel(row?.outServerTime ?: row?.checkOutAt)}".trim(),
            ).filter { it.isNotBlank() }.joinToString(" · ")
        }
    }

    fun placeLabel(name: String?, status: String?): String {
        val n = name?.takeIf { it.isNotBlank() }
        return when (status?.lowercase()) {
            "inside" -> if (n != null) "$n (inside)" else "Inside campus"
            "outside" -> if (n != null) "Outside $n" else "Outside campus"
            "no_gps" -> "Location not available"
            "poor_accuracy" -> "Location not accurate"
            else -> n.orEmpty()
        }
    }
}

/** Client-side pre-check for one attendance attempt. The server still decides; this avoids sending doomed requests. */
sealed interface Readiness {
    data class Ready(val fix: GpsFix) : Readiness
    data class Blocked(val message: String, val needsRetakePhoto: Boolean = false) : Readiness
}

object AttendanceRules {
    /** Server stale window is 120 s; retake the photo well before that. */
    const val PHOTO_MAX_AGE_MS = 90_000L

    const val NEED_PHOTO = "Take a photo first."
    const val PHOTO_TOO_OLD = "That photo is too old. Please retake it."
    const val NO_PERMISSION = "Location permission is off. Please allow location access and try again."
    const val WAIT_FOR_FRESH = "Waiting for a fresh location reading. Try again in a few seconds."

    fun readiness(
        mode: AttendanceMode,
        hasPhoto: Boolean,
        photoAtElapsedMs: Long?,
        fix: GpsFix?,
        failedFixElapsedMs: Long?,
        nowElapsedMs: Long,
        locationPermission: Boolean,
        accuracyLimitM: Double = GpsPolicy.ACCURACY_LIMIT_M,
    ): Readiness {
        if (mode == AttendanceMode.CHECK_IN && !hasPhoto) return Readiness.Blocked(NEED_PHOTO)
        if (hasPhoto && photoAtElapsedMs != null && nowElapsedMs - photoAtElapsedMs > PHOTO_MAX_AGE_MS) {
            return Readiness.Blocked(PHOTO_TOO_OLD, needsRetakePhoto = true)
        }
        if (!locationPermission) return Readiness.Blocked(NO_PERMISSION)
        return when (val g = GpsPolicy.evaluate(fix, nowElapsedMs, accuracyLimitM)) {
            GpsState.Searching -> Readiness.Blocked("Still looking for your location. Please wait a moment.")
            is GpsState.Stale -> Readiness.Blocked(WAIT_FOR_FRESH)
            is GpsState.Weak -> Readiness.Blocked(ErrorCopy.GPS_LOW)
            is GpsState.Ready -> {
                // A reading that already failed on the server is never reused: retry needs a newer one.
                if (failedFixElapsedMs != null && g.fix.elapsedMs <= failedFixElapsedMs) {
                    Readiness.Blocked(WAIT_FOR_FRESH)
                } else {
                    Readiness.Ready(g.fix)
                }
            }
        }
    }

    /** Photo checks only (used when a check-in goes out without a location). */
    fun photoReadiness(mode: AttendanceMode, hasPhoto: Boolean, photoAtElapsedMs: Long?, nowElapsedMs: Long): Readiness.Blocked? {
        if (mode == AttendanceMode.CHECK_IN && !hasPhoto) return Readiness.Blocked(NEED_PHOTO)
        if (hasPhoto && photoAtElapsedMs != null && nowElapsedMs - photoAtElapsedMs > PHOTO_MAX_AGE_MS) {
            return Readiness.Blocked(PHOTO_TOO_OLD, needsRetakePhoto = true)
        }
        return null
    }

    fun buildRequest(fix: GpsFix?, photoBase64: String?, nowUtc: Instant, attemptId: String): AttendanceRequest =
        AttendanceRequest(
            imageBase64 = photoBase64,
            // Never half a pair: a lone lat or lng is a 400 VALIDATION on the server.
            lat = fix?.lat?.takeIf { fix.lng in -180.0..180.0 && it in -90.0..90.0 },
            lng = fix?.lng?.takeIf { fix.lat in -90.0..90.0 && it in -180.0..180.0 },
            accuracyM = fix?.accuracyM,
            // Always "...T11:00:00Z" (OffsetDateTime.toString() drops zero seconds, which some parsers reject).
            capturedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(nowUtc.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC)),
            gpsMissing = fix == null,
            attemptId = attemptId,
            // Only ever sent as true; the server records and flags it, it never blocks on it.
            isMock = if (fix?.isMock == true) true else null,
        )

    /** Server codes tied to the location reading: the failed reading must not be reused. */
    fun needsFreshReading(code: String): Boolean = code.uppercase() in setOf(
        "GEO_FENCE_RESTRICTED", "GPS_ACCURACY_LOW", "STALE_CAPTURE",
    )

    /** Server codes that mean "the state already moved on": close the panel and re-read the state. */
    fun stateAlreadyMoved(code: String): Boolean = code.uppercase() in setOf(
        "ALREADY_CLOCKED_OUT", "ALREADY_CHECKED_IN", "NO_ACTIVE_ATTENDANCE",
    )
}
