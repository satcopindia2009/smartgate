package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.model.CourierCreate
import com.satcop.smartvisitor.kiosk.data.model.CourierEvent
import com.satcop.smartvisitor.kiosk.data.model.CourierStatus
import com.satcop.smartvisitor.kiosk.data.model.GuardHistoryEvent
import com.satcop.smartvisitor.kiosk.data.model.HistoryKind
import com.satcop.smartvisitor.kiosk.data.model.LostFoundCreate
import com.satcop.smartvisitor.kiosk.data.model.LostFoundItem
import com.satcop.smartvisitor.kiosk.data.model.VisitorPrefill
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Offline fallback when living lookup/history/couriers unreachable. */
object GuardAsapFixtures {
    private val seq = AtomicInteger(100)
    private val history = CopyOnWriteArrayList<GuardHistoryEvent>()
    private val couriers = CopyOnWriteArrayList<CourierEvent>()
    private val lostFound = CopyOnWriteArrayList<LostFoundItem>()

    private val directory: Map<String, VisitorPrefill> = mapOf(
        "9822011122" to VisitorPrefill(
            mobile = "9822011122",
            visitorName = "Priya Sharma",
            lastPurpose = "PTM follow-up, Class 4B",
            lastVisitorType = "Parent",
            lastHostId = DemoFixtures.HOST_ANITA_ID,
            photoKey = "media/live_photo/priya",
        ),
        "9876501234" to VisitorPrefill(
            mobile = "9876501234",
            visitorName = "Arjun Kale",
            company = "Kale Stationery Pvt Ltd",
            lastPurpose = "Stationery delivery",
            lastVisitorType = "Vendor",
            lastHostId = "H04",
        ),
        "9876500001" to VisitorPrefill(
            mobile = "9876500001",
            visitorName = "Vikram More",
            lastPurpose = "Meet accounts",
            lastVisitorType = "Guest",
            blacklisted = true,
            blacklistSeverity = "Block",
            blacklistReason = "Prior incident — unauthorized campus entry",
        ),
        "9876500002" to VisitorPrefill(
            mobile = "9876500002",
            visitorName = "Neha Salunkhe",
            company = "Neha Supplies",
            lastPurpose = "Stationery delivery",
            lastVisitorType = "Vendor",
            blacklisted = true,
            blacklistSeverity = "Alert",
            blacklistReason = "Repeat after-hours vendor",
        ),
    )

    init { seed() }

    private fun seed() {
        if (history.isNotEmpty()) return
        val today = "2026-09-18"
        history.addAll(
            listOf(
                GuardHistoryEvent("GH-001", HistoryKind.VISITOR, "Priya Sharma", "Parent · PTM", "inside", DemoFixtures.GATE_MAIN_ID, "9822011122", null, null, "${today}T09:15:00+05:30", DemoFixtures.STORY_VISIT_ID),
                GuardHistoryEvent("GH-002", HistoryKind.VENDOR, "Arjun Kale", "Vendor", "inside", "G-STAFF", "9876501234", "Kale Stationery", null, "${today}T10:02:00+05:30", "V-021"),
                GuardHistoryEvent("GH-003", HistoryKind.CHECKOUT, "Checkout · Meera Banerjee", "completed", "completed", DemoFixtures.GATE_MAIN_ID, null, null, null, "${today}T11:40:00+05:30", "V-OUT"),
                GuardHistoryEvent("GH-004", HistoryKind.COURIER, "BlueDart → Anita Joshi", "Received", CourierStatus.RECEIVED, DemoFixtures.GATE_MAIN_ID, null, "BlueDart", null, "${today}T10:15:00+05:30", "CR-001", recipientName = "Anita Joshi"),
            ),
        )
        couriers.add(
            CourierEvent(
                id = "CR-SEED-01",
                schoolId = DemoFixtures.SCHOOL_ID,
                gateId = DemoFixtures.GATE_MAIN_ID,
                courierCompany = "BlueDart",
                recipientName = "Anita Joshi",
                status = CourierStatus.RECEIVED,
                receivedAt = "${today}T10:15:00+05:30",
                guardUserId = "U-GATE",
                meta = DemoFixtures.meta,
            ),
        )
    }

    fun lookupByMobile(raw: String): VisitorPrefill? =
        MobileIndia.tenDigit(raw)?.let { directory[it] }

    fun listHistory(todayOnly: Boolean, datePrefix: String?, kind: String?, status: String?, gateId: String?): List<GuardHistoryEvent> {
        val day = datePrefix ?: if (todayOnly) "2026-09-18" else null
        return history.asSequence()
            .filter { day == null || it.occurredAt.startsWith(day) }
            .filter { kind.isNullOrBlank() || kind == "all" || it.kind.equals(kind, true) }
            .filter { status.isNullOrBlank() || status == "all" || it.status.equals(status, true) }
            .filter { gateId.isNullOrBlank() || it.gateId == gateId }
            .sortedByDescending { it.occurredAt }
            .toList()
    }

    fun getHistory(id: String) = history.firstOrNull { it.id == id }

    fun receiveCourier(body: CourierCreate, guardUserId: String?): CourierEvent {
        val id = "CE-${seq.incrementAndGet()}"
        val ts = LocalVisitStore.nowIst()
        val event = CourierEvent(
            id = id,
            schoolId = DemoFixtures.SCHOOL_ID,
            gateId = body.gateId,
            courierCompany = body.courierCompany.trim(),
            courierPersonName = body.courierPersonName?.trim()?.ifBlank { null },
            courierMobile = body.courierMobile,
            recipientName = body.recipientName.trim(),
            recipientDeptOrHost = body.recipientDeptOrHost,
            packageNote = body.packageNote,
            photoKey = body.photoKey,
            trackingNumber = body.trackingNumber,
            packageType = body.packageType,
            collectedBy = body.collectedBy,
            status = CourierStatus.RECEIVED,
            receivedAt = ts,
            guardUserId = guardUserId,
            meta = DemoFixtures.meta,
        )
        couriers.add(0, event)
        history.add(0, GuardHistoryEvent("GH-${seq.incrementAndGet()}", HistoryKind.COURIER, "${event.courierCompany} → ${event.recipientName}", "Received", event.status, event.gateId, event.courierMobile, event.courierCompany, null, ts, event.id, recipientName = event.recipientName))
        return event
    }

    fun handOverCourier(id: String): CourierEvent {
        val idx = couriers.indexOfFirst { it.id == id }
        require(idx >= 0) { "Courier $id not found" }
        val cur = couriers[idx]
        if (cur.status == CourierStatus.HANDED_OVER) return cur
        val ts = LocalVisitStore.nowIst()
        val updated = cur.copy(status = CourierStatus.HANDED_OVER, handedOverAt = ts)
        couriers[idx] = updated
        history.add(0, GuardHistoryEvent("GH-${seq.incrementAndGet()}", HistoryKind.COURIER, "${updated.courierCompany} → ${updated.recipientName}", "HandedOver", CourierStatus.HANDED_OVER, updated.gateId, null, updated.courierCompany, null, ts, updated.id))
        return updated
    }

    fun listCouriers() = couriers.toList()
    fun createLostFound(body: LostFoundCreate, gateId: String?): LostFoundItem {
        val id = "LF-${seq.incrementAndGet()}"
        val ts = LocalVisitStore.nowIst()
        val item = LostFoundItem(
            id = id,
            schoolId = DemoFixtures.SCHOOL_ID,
            description = body.description.trim(),
            photoKey = body.photoKey?.takeIf { it.isNotBlank() } ?: "media/lost_found/placeholder",
            foundLocation = body.locationFound.trim(),
            foundGateId = gateId ?: body.gateId,
            foundZone = body.foundZone,
            foundAt = body.foundAt.ifBlank { ts },
            foundByUserId = null,
            status = "Open",
            createdAt = ts,
            meta = DemoFixtures.meta,
            finderName = body.finderName.trim(),
            finderMobile = body.finderMobile,
            notes = body.notes,
        )
        lostFound.add(0, item)
        history.add(0, GuardHistoryEvent("GH-${seq.incrementAndGet()}", HistoryKind.LOST_FOUND, "LF · ${item.description.take(36)}", "Open · ${item.foundLocation}", "Open", item.foundGateId, null, null, null, ts, item.id))
        return item
    }

    fun listLostFound() = lostFound.toList()

    fun recordCheckout(visitId: String, visitorName: String, gateId: String?, mobile: String?) {
        val ts = LocalVisitStore.nowIst()
        history.add(0, GuardHistoryEvent("GH-${seq.incrementAndGet()}", HistoryKind.CHECKOUT, "Checkout · $visitorName", "completed", "completed", gateId, mobile, null, null, ts, visitId))
    }
}
