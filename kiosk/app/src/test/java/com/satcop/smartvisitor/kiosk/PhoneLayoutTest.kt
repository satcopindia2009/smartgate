package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneLayoutTest {
    @Test
    fun liveApiStaysOnReplacingSpywareTunnel() {
        assertEquals(
            "https://valley-questions-poultry-kid.trycloudflare.com/v1",
            ApiConfig.BASE_URL,
        )
        assertFalse(ApiConfig.BASE_URL.contains("weed-pumps"))
        assertFalse(ApiConfig.BASE_URL.contains("pensions-usb"))
        assertFalse(ApiConfig.BASE_URL.contains("gate123"))
    }

    @Test
    fun mediaUrlUsesApiBaseAndKey() {
        assertEquals(
            "https://valley-questions-poultry-kid.trycloudflare.com/v1/media/media/live_photo/abc",
            ApiConfig.mediaUrl("media/live_photo/abc"),
        )
    }

    @Test
    fun compactBreakpointStaysPhonePortrait() {
        assertEquals(600, com.satcop.smartvisitor.kiosk.ui.CompactWidthBreakpoint.value.toInt())
    }

    @Test
    fun kioskSourcesHaveNoHostWebTunnel() {
        val files = listOf(
            "com/satcop/smartvisitor/kiosk/ui/KioskRole.kt",
            "com/satcop/smartvisitor/kiosk/ui/KioskApp.kt",
            "com/satcop/smartvisitor/kiosk/ui/KioskLayout.kt",
            "com/satcop/smartvisitor/kiosk/ui/HostHomeScreen.kt",
            "com/satcop/smartvisitor/kiosk/data/api/ApiConfig.kt",
        )
        files.forEach { path ->
            val stream = javaClass.classLoader?.getResourceAsStream(path)
            if (stream != null) {
                val body = stream.bufferedReader().use { it.readText() }
                assertFalse(path, body.contains("england-content-resulting-heavily"))
                assertFalse(path, body.contains("pensions-usb"))
            }
        }
        assertFalse(ApiConfig.BASE_URL.contains("england-content-resulting-heavily"))
        assertTrue(ApiConfig.BASE_URL.startsWith("https://valley-questions-poultry-kid.trycloudflare.com"))
    }
}
