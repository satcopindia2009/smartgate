package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import com.satcop.smartvisitor.kiosk.ui.DemoHubLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLayoutTest {
    @Test
    fun liveApiStaysOnPensionsUsbTunnel() {
        assertEquals(
            "https://pensions-usb-loops-direction.trycloudflare.com/v1",
            ApiConfig.BASE_URL,
        )
        assertFalse(ApiConfig.BASE_URL.contains("weed-pumps"))
        assertFalse(ApiConfig.BASE_URL.contains("gate123"))
    }

    @Test
    fun demoHubOpensSeedSurfaces() {
        val urls = DemoHubLinks.all.map { it.url }
        assertEquals(4, urls.size)
        assertTrue(urls.contains("https://simon-configure-hiking-cms.trycloudflare.com"))
        assertTrue(urls.contains("https://england-content-resulting-heavily.trycloudflare.com/#afterhours"))
        assertTrue(urls.contains("https://ethernet-prairie-carefully-furnishings.trycloudflare.com/?passId=P-7K88"))
        assertTrue(urls.contains("https://votes-carlos-charter-damaged.trycloudflare.com"))
    }

    @Test
    fun compactBreakpointStaysPhonePortrait() {
        assertEquals(600, com.satcop.smartvisitor.kiosk.ui.CompactWidthBreakpoint.value.toInt())
    }
}
