package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.fixture.HybridKioskRepository
import com.satcop.smartvisitor.kiosk.data.fixture.LocalVisitStore
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.PassScanRequest
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wave4ScanTest {
    private val json = Json { encodeDefaults = true }

    private fun priyaBody() = VisitCreate(
        visitorName = "Priya Sharma",
        mobile = "9822011122",
        visitorType = "Parent",
        purpose = "PTM follow-up, Class 4B",
        hostId = "H03",
        livePhotoKey = "media/live_photo/priya",
        idType = "Aadhaar",
        idNumber = "XXXX1234",
        gateId = "G-MAIN",
    )

    @Test
    fun passScanJsonUsesContractFieldNames() {
        val body = PassScanRequest(
            passId = "P-4F21",
            token = null,
            action = "check_in",
            gateId = "G-MAIN",
        )
        val keys = json.parseToJsonElement(json.encodeToString(body)).jsonObject.keys
        assertTrue(keys.containsAll(listOf("passId", "token", "action", "gateId")))
        assertEquals("check_in", json.parseToJsonElement(json.encodeToString(body)).jsonObject["action"]?.toString()?.trim('"'))
    }

    @Test
    fun localStoreCreateApproveCheckInCheckOut() {
        val store = LocalVisitStore()
        val pending = store.createPending(priyaBody())
        assertEquals("pending", pending.status)
        val approved = store.demoApprove(pending.id)
        assertEquals("approved", approved.status)
        assertNotNull(approved.passId)
        assertNotNull(approved.qrToken)
        assertTrue(approved.passId!!.startsWith("P-"))
        val inside = store.scan(approved.passId, null, "check_in", "G-MAIN")
        assertEquals("inside", inside.status)
        assertNotNull(inside.timeIn)
        val done = store.scan(approved.passId, approved.qrToken, "check_out", null)
        assertEquals("completed", done.status)
        assertNotNull(done.timeOut)
    }

    @Test
    fun localScanRequiresApprovedThenInside() {
        val store = LocalVisitStore()
        val pending = store.createPending(priyaBody())
        try {
            store.scan("no-such", null, "check_in", "G-MAIN")
            org.junit.Assert.fail("expected NOT_FOUND")
        } catch (e: ApiException) {
            assertEquals("NOT_FOUND", e.code)
        }
        val approved = store.demoApprove(pending.id)
        try {
            store.scan(approved.passId, null, "check_out", null)
            org.junit.Assert.fail("expected INVALID_STATE")
        } catch (e: ApiException) {
            assertEquals("INVALID_STATE", e.code)
        }
    }

    @Test
    fun storyPassIsPriyaMainGateP4F21() {
        val store = LocalVisitStore()
        val story = store.get(DemoFixtures.STORY_VISIT_ID)
        assertNotNull(story)
        assertEquals("Priya Sharma", story!!.visitorName)
        assertEquals("P-4F21", story.passId)
        assertEquals("inside", story.status)
        assertEquals("H03", story.hostId)
        assertEquals("G-MAIN", story.gateId)
        assertEquals(DemoFixtures.STORY_QR_TOKEN, story.qrToken)
        val out = store.scan("P-4F21", null, "check_out", null)
        assertEquals("completed", out.status)
    }

    @Test
    fun hybridFixturesPathIssuesPassThenScan() = runBlocking {
        val repo = HybridKioskRepository()
        val visit = repo.createVisit(priyaBody())
        assertEquals("pending", visit.status)
        val approved = repo.demoApprove(visit.id)
        assertEquals("approved", approved.status)
        assertNotNull(approved.passId)
        val inside = repo.scanPass(passId = approved.passId, action = "check_in", gateId = "G-MAIN")
        assertEquals("inside", inside.status)
        val done = repo.scanPass(passId = approved.passId, action = "check_out")
        assertEquals("completed", done.status)
    }

    @Test
    fun hybridFixturesBlockVikram() = runBlocking {
        val repo = HybridKioskRepository()
        try {
            repo.createVisit(
                VisitCreate(
                    visitorName = "Vikram More",
                    mobile = "9876500001",
                    visitorType = "Guest",
                    purpose = "Meet accounts",
                    hostId = "H04",
                    livePhotoKey = "media/live_photo/vikram",
                    idType = "Aadhaar",
                    idNumber = "XXXX-XXXX-3321",
                    gateId = "G-MAIN",
                ),
            )
            org.junit.Assert.fail("expected BLACKLIST_BLOCK")
        } catch (e: ApiException) {
            assertEquals("BLACKLIST_BLOCK", e.code)
        }
    }

    @Test
    fun hybridStoryPassMatchesDemo() = runBlocking {
        val story = HybridKioskRepository().storyPass()
        assertEquals("P-4F21", story.passId)
        assertEquals("V-20260916-014", story.id)
        assertEquals("Priya Sharma", story.visitorName)
        assertEquals("inside", story.status)
    }
}
