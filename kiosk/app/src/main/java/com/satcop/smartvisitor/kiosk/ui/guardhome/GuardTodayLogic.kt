package com.satcop.smartvisitor.kiosk.ui.guardhome

import com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance

/** Pure (Compose-free) logic behind the Guard Today Progress / Incidents cards and shift text (boards 40, 49). */
object GuardTodayLogic {
    const val NO_ROUNDS = "No rounds assigned today"
    const val NO_INCIDENTS = "No incidents today"
    const val PROGRESS_LOADING = "Loading progress…"
    const val INCIDENTS_LOADING = "Loading incidents…"
    const val RETRY_HINT = "Pull down to try again."

    /** True when the server reports nothing to patrol today (zeros everywhere): never show fake progress. */
    fun hasRounds(s: GuardTodaySummary?): Boolean {
        val p = s?.patrol ?: return false
        return p.roundsTotal > 0 || p.checkpointsTotal > 0 || p.assignedToday > 0
    }

    /** Big number on the Progress card, e.g. "3/8" (checkpoints scanned / total); null when there is nothing assigned. */
    fun checkpointsText(s: GuardTodaySummary?): String? =
        if (hasRounds(s)) s!!.patrol.let { "${it.checkpointsScanned}/${it.checkpointsTotal}" } else null

    fun roundsText(s: GuardTodaySummary?): String? =
        if (hasRounds(s)) s!!.patrol.let { "Rounds ${it.roundsCompleted}/${it.roundsTotal}" } else null

    fun missedText(s: GuardTodaySummary?): String? =
        if (hasRounds(s)) s!!.patrol.missedCount.let { "Missed $it" } else null

    /** 0f..1f for the thin bar; server percent clamped to 0..100. */
    fun progressFraction(s: GuardTodaySummary?): Float =
        if (hasRounds(s)) (s!!.patrol.progressPercent.coerceIn(0, 100)) / 100f else 0f

    fun percentText(s: GuardTodaySummary?): String? =
        if (hasRounds(s)) "${s!!.patrol.progressPercent.coerceIn(0, 100)}%" else null

    /** "Next: Main Gate · Gate A" or null when the server has no next checkpoint. */
    fun nextText(s: GuardTodaySummary?): String? {
        val n = s?.patrol?.nextCheckpoint ?: return null
        val name = n.name?.trim().orEmpty()
        if (name.isEmpty()) return null
        val where = n.gateOrZone?.trim().orEmpty()
        return "Next: " + if (where.isNotEmpty()) "$name · $where" else name
    }

    fun incidentCountText(s: GuardTodaySummary?): String? = s?.incidentsToday?.count?.toString()

    fun incidentOpenText(s: GuardTodaySummary?): String? = s?.incidentsToday?.let { "${it.open} open" }

    fun incidentsEmpty(s: GuardTodaySummary?): Boolean = s != null && s.incidentsToday.count <= 0

    /** Shift text for the lock screen row (board 40): server text only; null hides the row. */
    fun shiftRow(t: TodayAttendance?): String? =
        t?.shiftStartDisplay?.trim()?.takeIf { it.isNotEmpty() }
            ?: t?.attendance?.shiftStartDisplay?.trim()?.takeIf { it.isNotEmpty() }

    private fun shiftName(t: TodayAttendance?): String? =
        t?.shiftName?.trim()?.takeIf { it.isNotEmpty() } ?: t?.attendance?.shiftName?.trim()?.takeIf { it.isNotEmpty() }

    /** Guard Today header subtitle (board 49 "Main Gate · Shift A"): "<place> · <shiftName> shift" from server data only. */
    fun headerSubtitle(fallback: String, t: TodayAttendance?): String {
        val shift = shiftName(t)?.let { "$it shift" } ?: return fallback
        val place = t?.gateName?.trim()?.takeIf { it.isNotEmpty() } ?: fallback.takeIf { it.isNotBlank() }
        return listOfNotNull(place, shift).joinToString(" · ")
    }
}
