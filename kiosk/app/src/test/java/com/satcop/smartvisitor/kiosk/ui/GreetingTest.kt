package com.satcop.smartvisitor.kiosk.ui

import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import java.io.File
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GreetingTest {
    private fun t(h: Int, m: Int) = LocalTime.of(h, m)

    @Test fun boundaries() {
        assertEquals("Evening", Greeting.word(t(0, 32)))   // QA: Gate home said Morning at 00:32
        assertEquals("Evening", Greeting.word(t(4, 59)))
        assertEquals("Morning", Greeting.word(t(5, 0)))
        assertEquals("Morning", Greeting.word(t(11, 59)))
        assertEquals("Afternoon", Greeting.word(t(12, 0)))
        assertEquals("Afternoon", Greeting.word(t(16, 59)))
        assertEquals("Evening", Greeting.word(t(17, 0)))
        assertEquals("Evening", Greeting.word(t(23, 59)))
    }

    @Test fun phraseAndLine() {
        assertEquals("Good Evening", Greeting.phrase(t(0, 32)))
        assertEquals("Good Morning, Ravi", Greeting.line("Ravi", t(9, 0)))
        assertEquals("Good Afternoon", Greeting.line("", t(13, 0)))
    }

    @Test fun everyScreenUsesTheSharedFunction() {
        val root = "src/main/java/com/satcop/smartvisitor/kiosk/"
        val gate = File(root + "ui/apple/GateTodayScreen.kt").readText()
        assertTrue(gate.contains("Greeting.phrase()"))
        assertFalse("no private hour thresholds", gate.contains("h < 12"))
        val logic = File(root + "ui/guardhome/ClockInLogic.kt").readText()
        assertTrue(logic.contains("Greeting.word(now)"))
        assertTrue(logic.contains("Greeting.line("))
        assertFalse(logic.contains("\"Good \${"))
    }

    @Test fun draftHasNoDefaultHost() {
        assertNull(RegistrationDraft().hostId)
    }

    @Test fun strayLiveTagIsGone() {
        val s = File("src/main/java/com/satcop/smartvisitor/kiosk/guardpatrol/ui/GuardPatrolScreens.kt").readText()
        assertFalse(s.contains("\"LIVE\""))
    }

    @Test fun lockScreenHasNoFakeShiftOrAttendanceRow() {
        val s = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/guardhome/ClockFlowScreens.kt").readText()
        assertFalse(s.contains("\"Attendance\", ClockInLogic.LOCK_CHIP"))
        assertFalse(s.contains("starts 06:30"))
    }
}
