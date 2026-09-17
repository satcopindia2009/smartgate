package com.satcop.smartvisitor.kiosk.data.model

/** Live Pranay seed school — never show Priya / P-4F21 / demo chips here. */
object SchoolIds {
    const val PRANAY = "SCH-PRANAY-01"
    const val DEMO = "SCH-DEMO-01"

    fun hidesDemoStory(schoolId: String?): Boolean =
        schoolId?.trim().equals(PRANAY, ignoreCase = true)
}

object AfterHoursCopy {
    const val HOST_NO_OP =
        "After hours · Host Approve is a no-op. Admin or Security Head must approve."
    const val CODE = "AFTER_HOURS_SH_REQUIRED"
}

object CampusHours {
    fun isAfterHours(
        rows: List<CampusHoursRow>,
        now: java.time.ZonedDateTime,
    ): Boolean {
        if (rows.isEmpty()) return false
        val weekday = now.dayOfWeek.name.take(3).lowercase()
        val row = rows.firstOrNull { it.weekday.equals(weekday, ignoreCase = true) } ?: return false
        if (row.closed) return true
        val open = row.openTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
        val close = row.closeTime?.let { runCatching { java.time.LocalTime.parse(it) }.getOrNull() }
        if (open == null || close == null) return row.closed
        val t = now.toLocalTime()
        return t.isBefore(open) || !t.isBefore(close)
    }
}
