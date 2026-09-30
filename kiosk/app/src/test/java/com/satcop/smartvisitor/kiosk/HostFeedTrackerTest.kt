package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.model.HostFeedItem
import com.satcop.smartvisitor.kiosk.data.store.MemoryStringStore
import com.satcop.smartvisitor.kiosk.ui.notify.HostFeedTracker
import com.satcop.smartvisitor.kiosk.ui.notify.PollBackoff
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HostFeedTrackerTest {
    private fun item(id: String, type: String = "visit.pending", actions: List<String> = listOf("approve", "reject", "view")) =
        HostFeedItem(id = id, type = type, visitId = "V-$id", visitorName = "N$id", actions = actions)

    @Test
    fun dedupesByIdAndAlertsOldestFirst() {
        val t = HostFeedTracker(MemoryStringStore())
        val first = t.ingest("H03", listOf(item("NTF-2"), item("NTF-1")), "NTF-2")
        assertEquals(listOf("NTF-1", "NTF-2"), first.map { it.id })
        val again = t.ingest("H03", listOf(item("NTF-2"), item("NTF-1"), item("NTF-3")), "NTF-3")
        assertEquals(listOf("NTF-3"), again.map { it.id })
    }

    @Test
    fun cursorPersistsAndIsUsedAsSince() {
        val store = MemoryStringStore()
        HostFeedTracker(store).ingest("H03", listOf(item("NTF-9")), "NTF-9")
        val restarted = HostFeedTracker(store)
        assertEquals("NTF-9", restarted.lastId("H03"))
        // after a restart the same id may come back once; with a stored cursor it is still alerted only once per process
        val a = restarted.ingest("H03", listOf(item("NTF-10")), "NTF-10")
        assertEquals(listOf("NTF-10"), a.map { it.id })
        assertEquals("NTF-10", restarted.lastId("H03"))
    }

    @Test
    fun ignoresNonPendingAndNoApproveAction() {
        val t = HostFeedTracker(MemoryStringStore())
        val a = t.ingest(
            "H03",
            listOf(item("NTF-1", type = "visit.approved", actions = listOf("view")), item("NTF-2", actions = listOf("view"))),
            "NTF-2",
        )
        assertTrue(a.isEmpty())
    }

    @Test
    fun staleBacklogOnFirstRunDoesNotAlert() {
        val t = HostFeedTracker(MemoryStringStore(), freshWindowMs = 60_000)
        val old = item("NTF-1").copy(createdAt = "old")
        val a = t.ingest("H03", listOf(old), "NTF-1", nowMs = 1_000_000, parseTime = { 0L })
        assertTrue(a.isEmpty())
    }

    @Test
    fun decodesContractPayloadIgnoringLegacyExtras() {
        val raw = """{"data":[{"id":"NTF-0112","type":"visit.pending","visitId":"V-1","visitorName":"Smoke Visitor",
          "visitorPhotoUrl":null,"purpose":"Smoke test","gateLabel":"Main Gate","hostId":"H03",
          "createdAt":"2026-09-30T14:41:47.643+05:30","readAt":null,"actions":["approve","reject","view"],
          "event":"visit.pending","title":"x","payload":{"a":1}}],
          "meta":{"watermark":"DEMO","count":1,"lastId":"NTF-0112","unreadCount":1,"serverTime":"z"}}"""
        val r = Json { ignoreUnknownKeys = true }.decodeFromString(
            com.satcop.smartvisitor.kiosk.data.model.HostFeedResponse.serializer(), raw,
        )
        assertEquals("NTF-0112", r.meta?.lastId)
        assertTrue(r.data.first().isPendingVisit)
        assertEquals("Main Gate", r.data.first().gateLabel)
    }

    @Test
    fun backoffGrowsOnErrorsAndResets() {
        val b = PollBackoff()
        assertEquals(3_000L, b.nextDelayMs(true))
        b.onFailure(); assertEquals(6_000L, b.nextDelayMs(true))
        b.onFailure(); assertEquals(12_000L, b.nextDelayMs(true))
        repeat(10) { b.onFailure() }
        assertEquals(30_000L, b.nextDelayMs(true))
        b.onSuccess(); assertEquals(3_000L, b.nextDelayMs(true))
        assertEquals(20_000L, b.nextDelayMs(false))
    }
}
