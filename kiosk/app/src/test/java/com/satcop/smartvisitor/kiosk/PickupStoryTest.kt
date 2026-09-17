package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.fixture.PickupStory
import com.satcop.smartvisitor.kiosk.data.model.VisitorType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupStoryTest {
    @Test
    fun pranayGateStoryIsAshaRameshSmitaNotPriya() {
        assertEquals("Pranay School Pune", PickupStory.SCHOOL_PRANAY)
        assertEquals("SCH-PRANAY-01", PickupStory.SCHOOL_ID_PRANAY)
        assertEquals("PRANAY", PickupStory.SCHOOL_CODE_PRANAY)
        assertEquals("pranay.gate", PickupStory.GATE_USER_PRANAY)
        assertEquals("Asha Patil", PickupStory.STUDENT_ASHA)
        assertEquals("5-B", PickupStory.CLASS_ASHA)
        assertEquals("Ramesh Patil", PickupStory.COLLECTOR_PARENT)
        assertEquals("Smita Patil", PickupStory.COLLECTOR_GUARDIAN)
        assertEquals("Rohan Shah", PickupStory.STUDENT_ROHAN_SHAH)
        assertFalse(PickupStory.COLLECTOR_PARENT.contains("Priya"))
        assertFalse(PickupStory.COLLECTOR_GUARDIAN.contains("Priya"))
        assertFalse(PickupStory.STUDENT_ASHA.contains("Priya"))
    }

    @Test
    fun p6StoryIsAaravNehaRohanAndKabirNotPriya() {
        assertEquals("Aarav Mehta", PickupStory.STUDENT_AARAV)
        assertEquals("5-B", PickupStory.CLASS_AARAV)
        assertEquals("Neha Mehta", PickupStory.COLLECTOR_MOTHER)
        assertEquals("Rohan Mehta", PickupStory.COLLECTOR_UNCLE)
        assertEquals("Kabir Singh", PickupStory.STUDENT_KABIR)
        assertEquals("court_order", PickupStory.CUSTODY_FLAG)
        assertFalse(PickupStory.COLLECTOR_MOTHER.contains("Priya"))
        assertFalse(PickupStory.STUDENT_KABIR.contains("Priya"))
    }

    @Test
    fun visitorTypeEnumUnchangedByPickupEntry() {
        assertEquals(
            listOf("Parent", "Vendor", "Guest", "Official", "Alumni"),
            VisitorType.entries.map { it.apiValue },
        )
    }

    @Test
    fun gateScreenMapHasSevenSteps() {
        assertEquals(7, PickupStory.gateScreens.size)
        assertTrue(PickupStory.gateScreens.first().startsWith("1 Purpose"))
        assertTrue(PickupStory.gateScreens.last().startsWith("7 Override"))
    }
}
