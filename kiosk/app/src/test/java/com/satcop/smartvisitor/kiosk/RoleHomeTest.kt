package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.ui.DemoHubLinks
import com.satcop.smartvisitor.kiosk.ui.HostWebLinks
import com.satcop.smartvisitor.kiosk.ui.KioskRole
import com.satcop.smartvisitor.kiosk.ui.KioskUiState
import com.satcop.smartvisitor.kiosk.ui.homeRole
import com.satcop.smartvisitor.kiosk.ui.showsGateRegistration
import com.satcop.smartvisitor.kiosk.ui.showsHostApprove
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
        assertTrue(host.showsHostApprove())
        assertFalse(host.showsGateRegistration())
        assertFalse(host.showsUnsupportedRole())
        assertTrue(admin.showsUnsupportedRole())
        assertFalse(admin.showsGateRegistration())
        assertFalse(admin.showsHostApprove())
        assertEquals(KioskRole.HOST, host.homeRole())
    }

    @Test
    fun hostWebOpensEnglandTunnelNotGateDemoHub() {
        assertEquals(
            "https://england-content-resulting-heavily.trycloudflare.com",
            HostWebLinks.BASE,
        )
        assertEquals(
            "https://england-content-resulting-heavily.trycloudflare.com#pending",
            HostWebLinks.PENDING,
        )
        assertEquals(
            "https://england-content-resulting-heavily.trycloudflare.com#afterhours",
            HostWebLinks.AFTER_HOURS,
        )
        val gateUrls = DemoHubLinks.all.map { it.url }
        assertFalse(gateUrls.contains(HostWebLinks.BASE))
        assertFalse(gateUrls.contains(HostWebLinks.PENDING))
    }
}
