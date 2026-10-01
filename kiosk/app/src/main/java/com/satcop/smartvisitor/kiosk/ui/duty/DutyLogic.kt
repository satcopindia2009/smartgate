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
     * 1077: a legacy (not yet migrated) gate account never lands on "No duty" because the server has no row for it yet
     * (Backend phase A). Flip to false when Backend migrates gate users to guard + standing GATE duty.
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
            val s = serverAreas(info)
            if (s.isEmpty() && LEGACY_GATE_NEVER_NO_DUTY && isLegacyGateRole(role)) {
                return DutyResult(setOf(DutyArea.GATE) + patrolIf(patrolAssignedToday), fromServer = true)
            }
            return DutyResult(s, fromServer = true)
        }
        val gate = isLegacyGateRole(role) ||
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

    private fun patrolIf(n: Int?): Set<DutyArea> = if (n != null && n > 0) setOf(DutyArea.PATROL) else emptySet()

    /** The fallback needs the patrol summary only when the server gave no duty answer and the role is not a plain legacy gate. */
    fun needsPatrolSummary(role: String?, info: DutyInfo?): Boolean =
        !(info != null && info.present && !(serverAreas(info).isEmpty() && LEGACY_GATE_NEVER_NO_DUTY && isLegacyGateRole(role))) &&
            norm(role) !in setOf("host", "admin")

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
}
