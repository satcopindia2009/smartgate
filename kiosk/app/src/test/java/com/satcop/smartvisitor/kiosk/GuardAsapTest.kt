package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.fixture.GuardAsapFixtures
import com.satcop.smartvisitor.kiosk.data.model.CourierCreate
import com.satcop.smartvisitor.kiosk.data.model.GateHistoryRow
import com.satcop.smartvisitor.kiosk.data.model.VisitorLookupResponse
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardAsapTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun lookup_response_maps_to_prefill() {
        val raw = """{"found":true,"visitorName":"Priya Sharma","livePhotoUrl":"/v1/media/x","company":null,"lastPurpose":"PTM","lastVisitorType":"Parent","lastVisitId":"V-1","blacklistHit":false,"blacklistSeverity":null,"meta":{"watermark":"DEMO"}}"""
        val resp = json.decodeFromString<VisitorLookupResponse>(raw)
        val prefill = resp.toPrefill("9822011122")
        assertNotNull(prefill)
        assertEquals("Priya Sharma", prefill!!.visitorName)
        assertEquals("Parent", prefill.lastVisitorType)
        assertFalse(prefill.blacklisted)
    }

    @Test
    fun gate_history_row_maps_courier() {
        val raw = """{"type":"courier","id":"CR-1","status":"Received","gateId":"G-MAIN","timestamp":"2026-09-18T10:15:00+05:30","courierCompany":"BlueDart","recipientName":"Anita","guardUserId":"U-GATE"}"""
        val row = json.decodeFromString<GateHistoryRow>(raw)
        val ev = row.asEvent()
        assertEquals("courier", ev.kind)
        assertTrue(ev.title.contains("BlueDart"))
        assertEquals("Received", ev.status)
    }

    @Test
    fun fixture_lookup_and_courier_on_history() {
        val ten = MobileIndia.tenDigit("+91 98220 11122")
        assertEquals("9822011122", ten)
        val hit = GuardAsapFixtures.lookupByMobile(ten!!)
        assertEquals("Priya Sharma", hit?.visitorName)
        assertNull(GuardAsapFixtures.lookupByMobile("9000000000"))
        val c = GuardAsapFixtures.receiveCourier(
            CourierCreate(gateId = "G-MAIN", courierCompany = "TestCo", recipientName = "Desk"),
            "U-GATE",
        )
        assertEquals("Received", c.status)
        val hist = GuardAsapFixtures.listHistory(true, null, "courier", null, null)
        assertTrue(hist.any { it.relatedId == c.id })
    }
}
