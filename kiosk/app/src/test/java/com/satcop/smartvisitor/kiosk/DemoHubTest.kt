package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import com.satcop.smartvisitor.kiosk.data.fixture.DemoHubLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoHubTest {
    @Test
    fun apiBaseIsPensionsUsbNeverWeedPumps() {
        assertEquals(
            "https://pensions-usb-loops-direction.trycloudflare.com/v1",
            ApiConfig.BASE_URL,
        )
        assertFalse(ApiConfig.BASE_URL.contains("weed-pumps"))
    }

    @Test
    fun webPreviewUrlsMatchHandoff() {
        assertEquals(
            "https://simon-configure-hiking-cms.trycloudflare.com",
            DemoHubLinks.AFTER_HOURS_GATE,
        )
        assertEquals(
            "https://england-content-resulting-heavily.trycloudflare.com/#afterhours",
            DemoHubLinks.HOST_APPROVE_AFTERHOURS,
        )
        assertEquals(
            "https://ethernet-prairie-carefully-furnishings.trycloudflare.com/?passId=P-7K88",
            DemoHubLinks.VISITOR_QR_P7K88,
        )
        assertEquals(
            "https://votes-carlos-charter-damaged.trycloudflare.com",
            DemoHubLinks.ESCORT_ZONES,
        )
    }

    @Test
    fun hubLabelsAreClearAndIncludeInAppGate() {
        val titles = DemoHubLinks.items.map { it.title }
        assertEquals("Visitor gate (this app)", titles.first())
        assertTrue(titles.contains("After-hours gate"))
        assertTrue(titles.contains("Host approve (After-hours tab)"))
        assertTrue(titles.contains("Visitor QR P-7K88"))
        assertTrue(titles.contains("Escort / zones"))
        assertTrue(DemoHubLinks.items.first().inApp)
        assertEquals(4, DemoHubLinks.webPreviews.size)
        assertTrue(DemoHubLinks.webPreviews.all { it.url!!.startsWith("https://") })
        assertTrue(DemoHubLinks.TUNNEL_NOTE.contains("FIXTURES"))
        assertTrue(DemoHubLinks.TUNNEL_NOTE.contains("502"))
    }
}
