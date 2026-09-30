package com.satcop.smartvisitor.kiosk.ui

import java.time.LocalTime

/**
 * The ONE greeting rule for every screen (Gate Today, Guard lock, Guard Today).
 * Device clock, display only, never sent to the server. Product ruling 2026-09-30:
 * Morning 05:00-11:59, Afternoon 12:00-16:59, Evening 17:00-04:59.
 */
object Greeting {
    private val MORNING_START = LocalTime.of(5, 0)
    private val AFTERNOON_START = LocalTime.NOON
    private val EVENING_START = LocalTime.of(17, 0)

    fun word(now: LocalTime): String = when {
        now >= MORNING_START && now < AFTERNOON_START -> "Morning"
        now >= AFTERNOON_START && now < EVENING_START -> "Afternoon"
        else -> "Evening"
    }

    /** "Good Morning". */
    fun phrase(now: LocalTime = LocalTime.now()): String = "Good ${word(now)}"

    /** "Good Morning, Ravi" or just "Good Morning" when [firstName] is blank. */
    fun line(firstName: String, now: LocalTime = LocalTime.now()): String =
        if (firstName.isBlank()) phrase(now) else "${phrase(now)}, $firstName"
}
