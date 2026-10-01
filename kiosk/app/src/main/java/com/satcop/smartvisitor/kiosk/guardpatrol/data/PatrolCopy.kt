package com.satcop.smartvisitor.kiosk.guardpatrol.data

/** 1080: plain-English texts of the Patrol tab (no route names, ids or backend words). Compose-free so tests can check them. */
object PatrolCopy {
    const val NONE_TODAY = "No patrol assigned for today."
    const val UPDATED = "Updated"
    const val UNREACHABLE = "Can't reach the server. Tap Refresh to try again."
    const val NOT_READY = "Patrol is not ready yet. Tap Refresh."
    const val PICK_ONE = "Pick a patrol from Assigned today."

    fun assignedToday(count: Int): String =
        if (count <= 0) NONE_TODAY else if (count == 1) "1 patrol assigned for today" else "$count patrols assigned for today"

    /** Words that must never reach a guard's screen. */
    val DEV_WORDS = listOf("guard_id", "my-schedules", "POST /", "GET /", "patrol-schedules", "Living", "LIVE ·", "Ask Admin", "dutyDate", "duty(ies)")
}
