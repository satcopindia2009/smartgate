package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.model.AfterHoursCopy
import com.satcop.smartvisitor.kiosk.data.model.SchoolIds
import com.satcop.smartvisitor.kiosk.ui.KioskRole
import com.satcop.smartvisitor.kiosk.ui.KioskScreen
import com.satcop.smartvisitor.kiosk.ui.KioskUiState
import com.satcop.smartvisitor.kiosk.ui.homeRole
import com.satcop.smartvisitor.kiosk.ui.showsGateRegistration
import com.satcop.smartvisitor.kiosk.ui.showsHostApprove
import com.satcop.smartvisitor.kiosk.ui.showsGuardPatrol
import com.satcop.smartvisitor.kiosk.ui.showsPickup
import com.satcop.smartvisitor.kiosk.ui.showsUnsupportedRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleHomeTest {
    @Test
    fun jwtRoleSplitsGateHostAndOther() {
        assertEquals(KioskRole.GATE, KioskRole.fromJwt("gate"))
        assertEquals(KioskRole.GATE, KioskRole.fromJwt("GATE"))
        assertEquals(KioskRole.HOST, KioskRole.fromJwt("host"))
        assertEquals(KioskRole.HOST, KioskRole.fromJwt(" Host "))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt("admin"))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt("sh"))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt("escort"))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt(""))
        assertEquals(KioskRole.UNSUPPORTED, KioskRole.fromJwt(null))
    }

    @Test
    fun signedInHomesDoNotShareOptions() {
        val gate = KioskUiState(signedIn = true, meRole = "gate", meDisplayName = "Pranay Gate")
        val host = KioskUiState(signedIn = true, meRole = "host", meDisplayName = "Pranay Host")
        val admin = KioskUiState(signedIn = true, meRole = "admin", meDisplayName = "Office")
        assertTrue(gate.showsGateRegistration())
        assertFalse(gate.showsHostApprove())
        assertFalse(gate.showsUnsupportedRole())
        assertFalse(gate.showsPickup())
        assertTrue(host.showsHostApprove())
        assertFalse(host.showsGateRegistration())
        assertFalse(host.showsUnsupportedRole())
        assertTrue(admin.showsUnsupportedRole())
        assertFalse(admin.showsGateRegistration())
        assertFalse(admin.showsHostApprove())
        assertEquals(KioskRole.HOST, host.homeRole())
    }

    @Test
    fun gatePickupIsNativeScreenNotWeb() {
        val pickup = KioskUiState(
            signedIn = true,
            meRole = "gate",
            screen = KioskScreen.PICKUP,
        )
        assertTrue(pickup.showsPickup())
        assertFalse(pickup.showsGateRegistration())
        assertFalse(pickup.showsHostApprove())
    }

    @Test
    fun pranayHidesPriyaDemoStory() {
        assertTrue(SchoolIds.hidesDemoStory("SCH-PRANAY-01"))
        assertTrue(SchoolIds.hidesDemoStory("sch-pranay-01"))
        assertFalse(SchoolIds.hidesDemoStory("SCH-DEMO-01"))
        assertFalse(SchoolIds.hidesDemoStory(null))
    }

    @Test
    fun afterHoursCopyNamesAdminOrSecurityHead() {
        assertTrue(AfterHoursCopy.HOST_NO_OP.contains("Admin or Security Head"))
        assertFalse(AfterHoursCopy.HOST_NO_OP.contains("SH-only"))
        assertEquals("AFTER_HOURS_SH_REQUIRED", AfterHoursCopy.CODE)
    }
}


    @Test
    fun gateHomeShellSurvivesRegistrationStep() {
        // AC-APP1: screen==HOME keeps Gate shell even when step>1 (not old webpage).
        val registering = KioskUiState(
            signedIn = true,
            meRole = "gate",
            screen = KioskScreen.HOME,
            step = 2,
        )
        assertTrue(registering.showsGateRegistration())
        assertEquals(KioskRole.GATE, registering.homeRole())
        assertFalse(registering.showsPickup())
    }

    @Test
    fun guardHomeIsAlwaysGuardRole() {
        val guard = KioskUiState(
            signedIn = true,
            meRole = "guard",
            screen = KioskScreen.HOME,
        )
        assertTrue(guard.showsGuardPatrol())
        assertEquals(KioskRole.GUARD, guard.homeRole())
    }
