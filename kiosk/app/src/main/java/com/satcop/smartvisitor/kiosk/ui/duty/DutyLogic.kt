package com.satcop.smartvisitor.kiosk.ui.duty

import com.satcop.smartvisitor.kiosk.data.model.DutyGate
import com.satcop.smartvisitor.kiosk.data.model.DutyMe

enum class DutyArea { GATE, PATROL }

/** What the server told us about duty, from any of its carriers (GET /duty/me, /auth/me, login, /attendance/me/today). */
data class DutyInfo(
    val dutyTypes: List<String>? = null,
    val primaryHome: String? = null,
    val hasDuty: Boolean? = null,
    val revision: String? = null,
    val gates: List<DutyGate> = emptyList(),
    val patrolCount: Int? = null,
) {
    /** The server spoke about duty at all. */
    val present: Boolean get() = dutyTypes != null || !primaryHome.isNullOrBlank() || hasDuty != null

    companion object {
        fun fromUser(u: com.satcop.smartvisitor.kiosk.data.model.MeResponse?): DutyInfo? = u?.let {
            DutyInfo(it.dutyTypes, it.primaryHome, it.hasDuty, it.dutyRevision, DutyLogic.gatesFromAssignments(it.dutyAssignments), null)
        }

        fun from(m: DutyMe?): DutyInfo? = m?.let {
            DutyInfo(it.dutyTypes, it.primaryHome, it.hasDuty, it.dutyRevision, it.gates, it.patrol?.count)
        }
    }
}

enum class DutyHome { NONE, GATE, PATROL, BOTH }

data class DutyResult(val areas: Set<DutyArea>, val fromServer: Boolean) {
    val home: DutyHome
        get() = when {
            DutyArea.GATE in areas && DutyArea.PATROL in areas -> DutyHome.BOTH
            DutyArea.GATE in areas -> DutyHome.GATE
            DutyArea.PATROL in areas -> DutyHome.PATROL
            else -> DutyHome.NONE
        }

    /** Both lands on the Gate desk first. */
    val defaultArea: DutyArea? get() = if (DutyArea.GATE in areas) DutyArea.GATE else if (DutyArea.PATROL in areas) DutyArea.PATROL else null
}

/** 1077: ONE pure rule for "which areas does this guard see". Compose-free. */
object DutyLogic {
    private fun norm(s: String?) = s?.trim()?.lowercase()?.replace('-', '_').orEmpty()

    fun parseType(raw: String?): DutyArea? = when (norm(raw)) {
        "gate" -> DutyArea.GATE
        "patrol" -> DutyArea.PATROL
        else -> null
    }

    /**
     * Areas from the server fields. dutyTypes WINS over primaryHome when both are present and disagree.
     * primaryHome alone is used only when dutyTypes is absent. hasDuty=false with no types = none.
     */
    fun serverAreas(info: DutyInfo): Set<DutyArea> {
        info.dutyTypes?.let { list -> return list.mapNotNull(::parseType).toSet() }
        when (norm(info.primaryHome)) {
            "gate" -> return setOf(DutyArea.GATE)
            "patrol" -> return setOf(DutyArea.PATROL)
            "both" -> return setOf(DutyArea.GATE, DutyArea.PATROL)
            "none" -> return emptySet()
        }
        if (info.hasDuty == false) return emptySet()
        return emptySet()
    }

    fun isLegacyGateRole(role: String?) = norm(role) in setOf("gate", "gate_staff")

    /**
     * 1077/1078: a legacy (not yet migrated) gate account lands on the GATE desk when the server sent NO duty field at all
     * (older server). 1078 (Product item 5): once the duty field is PRESENT it is trusted, so hasDuty:false / empty dutyTypes
     * for role gate = the plain no-duty screen. The constant therefore only governs the field-absent fallback.
     */
    const val LEGACY_GATE_NEVER_NO_DUTY = true

    /**
     * @param info server duty (any carrier); null or not [DutyInfo.present] => fallback.
     * @param gateIds user.gateIds; @param attendanceGateId / attendanceGateSource from the attendance row.
     * @param patrolAssignedToday from /guards/me/today-summary (null = unknown).
     */
    fun areas(
        role: String?,
        info: DutyInfo?,
        gateIds: List<String>? = null,
        attendanceGateId: String? = null,
        attendanceGateSource: String? = null,
        patrolAssignedToday: Int? = null,
    ): DutyResult {
        if (info != null && info.present) {
            return DutyResult(serverAreas(info), fromServer = true)
        }
        val gate = (LEGACY_GATE_NEVER_NO_DUTY && isLegacyGateRole(role)) ||
            !gateIds.isNullOrEmpty() ||
            (!attendanceGateId.isNullOrBlank() && norm(attendanceGateSource) == "assignment")
        val patrol = patrolAssignedToday != null && patrolAssignedToday > 0
        val set = buildSet {
            if (gate) add(DutyArea.GATE)
            if (patrol) add(DutyArea.PATROL)
            // A guard with nothing at all behaves exactly as before 1077: patrol.
            if (isEmpty() && norm(role) in setOf("guard", "security")) add(DutyArea.PATROL)
        }
        return DutyResult(set, fromServer = false)
    }

    /** The fallback needs the patrol summary only when the server gave no duty answer and the role is not a plain legacy gate. */
    fun needsPatrolSummary(role: String?, info: DutyInfo?): Boolean =
        !(info != null && info.present) && norm(role) !in setOf("host", "admin")

    /** Keep the area the guard is on if it still exists, else the default of the new set. */
    fun selectArea(current: DutyArea?, r: DutyResult): DutyArea? = if (current != null && current in r.areas) current else r.defaultArea

    /** Distinct gates of the gate-type assignments (login snapshot), in server order. */
    fun gatesFromAssignments(list: List<com.satcop.smartvisitor.kiosk.data.model.DutyCompact>?): List<DutyGate> =
        list.orEmpty().filter { parseType(it.type) == DutyArea.GATE && !it.gateId.isNullOrBlank() }
            .map { DutyGate(it.gateId, it.gateName) }.distinctBy { it.id }

    /** Two or more distinct gates in today's gate duty => the guard chooses (first by default). */
    fun needsGateChooser(gates: List<DutyGate>): Boolean = gates.filter { !it.id.isNullOrBlank() }.distinctBy { it.id }.size > 1

    fun defaultGate(gates: List<DutyGate>): DutyGate? = gates.firstOrNull { !it.id.isNullOrBlank() }

    /** A duty change while the app is open: both sides have a revision and they differ. */
    fun revisionChanged(applied: String?, fresh: String?): Boolean =
        !applied.isNullOrBlank() && !fresh.isNullOrBlank() && applied != fresh

    /** Error codes (Backend phase B) the app reacts to. */
    fun isDutyError(code: String?): Boolean = code?.uppercase() in setOf("NO_GATE_DUTY", "NO_PATROL_DUTY")
    fun isNotClockedIn(code: String?): Boolean = code?.uppercase() == "NOT_CLOCKED_IN"

    const val NO_DUTY_TEXT = "No duty assigned today. Ask your supervisor."
    const val DUTY_UPDATED_TEXT = "Your duty was updated. Tap to refresh."
    const val AREA_GATE = "Gate desk"
    const val AREA_PATROL = "Patrol"
    const val VERIFIED_TITLE = "Face verified"
    const val VERIFIED_BODY = "Your face is verified. Welcome!"

    // ---------------- 1078 ----------------

    /** The shift of an area whose assignments are all outside their window. Every field optional. */
    data class ShiftInfo(val slot: String? = null, val start: String? = null, val end: String? = null)

    private fun hhmm(t: String?): String? = t?.trim()?.takeIf { it.isNotEmpty() }?.take(5)

    /** "Your shift is not active now (Day 06:00–14:00)." Slot omitted when unknown; no brackets when nothing is known. */
    fun shiftNoticeText(s: ShiftInfo?): String {
        val slot = s?.slot?.trim()?.takeIf { it.isNotEmpty() }
        val start = hhmm(s?.start)
        val end = hhmm(s?.end)
        val inner = when {
            start != null && end != null -> listOfNotNull(slot, "$start–$end").joinToString(" ")
            slot != null -> slot
            else -> null
        }
        return if (inner == null) "Your shift is not active now." else "Your shift is not active now ($inner)."
    }

    /**
     * Area is in the outside-shift state when it has assignments and NONE is in force. inForce null/missing = in force
     * (never block on a missing field). Returns the first assignment of the area (server order).
     */
    fun inactiveShift(area: DutyArea, assignments: List<com.satcop.smartvisitor.kiosk.data.model.DutyCompact>?): ShiftInfo? {
        val mine = assignments.orEmpty().filter { parseType(it.type) == area }
        if (mine.isEmpty()) return null
        if (mine.any { it.inForce != false }) return null
        val a = mine.first()
        return ShiftInfo(a.shiftName, a.shiftStart, a.shiftEnd)
    }

    /** Banner text per area that is outside its shift window. */
    fun shiftNotices(assignments: List<com.satcop.smartvisitor.kiosk.data.model.DutyCompact>?): Map<DutyArea, String> =
        DutyArea.values().mapNotNull { a -> inactiveShift(a, assignments)?.let { a to shiftNoticeText(it) } }.toMap()

    /**
     * Notices from the assignments, plus the ones a 403 OUTSIDE_SHIFT told us about. An error notice stays until the assignments
     * of that area explicitly say inForce=true (a missing inForce never clears it).
     */
    fun mergeNotices(
        computed: Map<DutyArea, String>,
        fromError: Map<DutyArea, String>,
        assignments: List<com.satcop.smartvisitor.kiosk.data.model.DutyCompact>?,
    ): Map<DutyArea, String> {
        val out = computed.toMutableMap()
        for ((area, text) in fromError) {
            if (area in out) continue
            val explicitlyIn = assignments.orEmpty().any { parseType(it.type) == area && it.inForce == true }
            if (!explicitlyIn) out[area] = text
        }
        return out
    }

    /** Primary NEW-action buttons of an area are enabled unless that area is outside its shift. Finishing work never asks. */
    fun newActionsEnabled(area: DutyArea?, notices: Map<DutyArea, String>): Boolean = area == null || area !in notices

    private fun up(s: String?) = s?.trim()?.uppercase().orEmpty()

    /**
     * The duty error a server answer stands for, in the ruled priority: NOT_CLOCKED_IN, then NO_GATE_DUTY / NO_PATROL_DUTY,
     * then GATE_NOT_ON_DUTY. Looks at code, details.reasonCode and details.reason so a 403 with several reasons resolves
     * the same way every time. Null = not a duty error.
     */
    fun dutyCodeOf(code: String?, details: Map<String, String> = emptyMap()): String? {
        val c = up(code); val rc = up(details["reasonCode"]); val rs = up(details["reason"])
        if (c == "NOT_CLOCKED_IN" || rc == "NOT_CLOCKED_IN" || rs == "NOT_CLOCKED_IN") return "NOT_CLOCKED_IN"
        for (x in listOf(c, rc)) if (x == "NO_GATE_DUTY" || x == "NO_PATROL_DUTY") return x
        if (c == "GATE_NOT_ON_DUTY" || rc == "GATE_NOT_ON_DUTY") return "GATE_NOT_ON_DUTY"
        return null
    }

    /** /gates is common read-only data: a duty answer from it never drives the UI (no lock, no banner, no error). */
    fun shouldEmitDutyEvent(path: String?, dutyCode: String?): Boolean =
        dutyCode != null && path?.trimEnd('/')?.endsWith("/gates") != true

    /** What to do with a duty error, in order. recheckAttendance wins over everything else. */
    data class DutyErrorPlan(
        val recheckAttendance: Boolean = false,
        val rereadDuty: Boolean = false,
        val outsideShift: Pair<DutyArea, ShiftInfo>? = null,
    )

    fun plan(code: String?, details: Map<String, String> = emptyMap()): DutyErrorPlan {
        return when (val d = dutyCodeOf(code, details)) {
            null -> DutyErrorPlan()
            "NOT_CLOCKED_IN" -> DutyErrorPlan(recheckAttendance = true)
            "NO_GATE_DUTY", "NO_PATROL_DUTY" -> {
                val area = if (d == "NO_GATE_DUTY") DutyArea.GATE else DutyArea.PATROL
                val outside = if (up(details["reason"]) == "OUTSIDE_SHIFT") area to shiftFromDetails(details) else null
                DutyErrorPlan(rereadDuty = true, outsideShift = outside)
            }
            else -> DutyErrorPlan(rereadDuty = true)
        }
    }

    /** details.shift = {name,start,end,slot} (flattened as shift.name ...). */
    fun shiftFromDetails(d: Map<String, String>): ShiftInfo {
        val start = d["shift.start"]; val end = d["shift.end"]
        if (!start.isNullOrBlank() && !end.isNullOrBlank()) return ShiftInfo(d["shift.name"], start, end)
        return ShiftInfo(slot = d["shift.slot"] ?: d["shift.name"])
    }

    enum class DutyChange { NONE, AUTO_SWITCH, BANNER }

    /**
     * A fresh duty read against what the screen shows. From the plain no-duty screen a duty that now exists switches
     * automatically (with a toast); every other change is a banner (never silent). applied == null = nothing shown yet.
     */
    fun change(applied: Set<DutyArea>?, appliedRevision: String?, fresh: DutyResult, freshRevision: String?): DutyChange {
        if (applied == null) return DutyChange.NONE
        if (applied.isEmpty() && fresh.areas.isNotEmpty()) return DutyChange.AUTO_SWITCH
        val changed = revisionChanged(appliedRevision, freshRevision) || (appliedRevision == null && fresh.areas != applied)
        return if (changed) DutyChange.BANNER else DutyChange.NONE
    }

    /** "Duty assigned: Main Gate" (gate or both: the gate name) / "Duty assigned: Patrol" (patrol only). */
    fun assignedToast(
        r: DutyResult,
        gates: List<DutyGate>,
        assignments: List<com.satcop.smartvisitor.kiosk.data.model.DutyCompact>? = null,
    ): String {
        if (DutyArea.GATE in r.areas) {
            val name = gates.firstOrNull { !it.name.isNullOrBlank() }?.name
                ?: assignments.orEmpty().firstOrNull { parseType(it.type) == DutyArea.GATE && !it.gateName.isNullOrBlank() }?.gateName
                ?: AREA_GATE
            return "$ASSIGNED_PREFIX$name"
        }
        return "$ASSIGNED_PREFIX$AREA_PATROL"
    }

    const val ASSIGNED_PREFIX = "Duty assigned: "
}
