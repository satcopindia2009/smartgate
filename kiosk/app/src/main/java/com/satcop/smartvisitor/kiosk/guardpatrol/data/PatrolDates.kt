package com.satcop.smartvisitor.kiosk.guardpatrol.data

/** 1079: the Patrol tab lists TODAY's duties only; a past-dated duty is never listed and never startable. Compose-free. */
object PatrolDates {
    fun isToday(dutyDate: String?, today: String): Boolean =
        dutyDate?.trim()?.take(10).orEmpty() == today.trim().take(10)

    fun todayOnly(list: List<PatrolAssignment>, today: String): List<PatrolAssignment> = list.filter { isToday(it.dutyDate, today) }

    fun todayOnlyDto(list: List<PatrolAssignmentDto>, today: String): List<PatrolAssignmentDto> = list.filter { isToday(it.dutyDate, today) }

    /** Start / Continue is allowed only for a duty dated today. */
    fun canStart(a: PatrolAssignment, today: String): Boolean = isToday(a.dutyDate, today)

    const val NOT_TODAY = "This duty is not for today"
}
