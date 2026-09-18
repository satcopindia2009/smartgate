package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory visit/pass map for fixtures and tunnel-down fallback.
 * Lifecycle matches the contract: pending → approved → inside → completed.
 */
class LocalVisitStore {

    private val visits = ConcurrentHashMap<String, VisitOut>()
    private val passToVisit = ConcurrentHashMap<String, String>()
    private val tokenToVisit = ConcurrentHashMap<String, String>()

    init {
        put(storyInside())
    }

    fun put(visit: VisitOut): VisitOut {
        visits[visit.id] = visit
        visit.passId?.let { passToVisit[it] = visit.id }
        visit.qrToken?.let { tokenToVisit[it] = visit.id }
        return visit
    }

    fun get(id: String): VisitOut? = visits[id]

    fun listInside(): List<VisitOut> = visits.values.filter { it.status == "inside" }

    fun checkoutByVisitId(visitId: String, gateId: String?): VisitOut {
        val visit = get(visitId) ?: throw ApiException("NOT_FOUND", "Visit $visitId not found", 404)
        if (visit.status == "completed" || !visit.timeOut.isNullOrBlank()) {
            throw ApiException("INVALID_STATE", "Already checked out", 409)
        }
        if (visit.status != "inside") {
            throw ApiException("INVALID_STATE", "Visit must be inside to check out", 409)
        }
        return put(visit.copy(status = "completed", timeOut = nowIst(), gateId = gateId ?: visit.gateId))
    }

    fun findByPass(passId: String?, token: String?): VisitOut? {
        val visitId = passId?.let { passToVisit[it] } ?: token?.let { tokenToVisit[it] }
        return visitId?.let { visits[it] }
    }

    fun createPending(body: VisitCreate, hit: BlacklistEntry? = null): VisitOut {
        val ts = nowIst()
        return put(
            VisitOut(
                id = "V-LOCAL-${System.currentTimeMillis() % 100000}",
                schoolId = DemoFixtures.SCHOOL_ID,
                visitorName = body.visitorName,
                mobile = body.mobile,
                visitorType = body.visitorType,
                purpose = body.purpose,
                hostId = body.hostId,
                livePhotoKey = body.livePhotoKey,
                idType = body.idType,
                idNumber = body.idNumber,
                idImageKey = body.idImageKey,
                vehicleNumber = body.vehicleNumber,
                accompanyingCount = body.accompanyingCount,
                notes = body.notes,
                signatureKey = body.signatureKey,
                gateId = body.gateId,
                status = "pending",
                blacklistHit = hit != null,
                blacklistId = hit?.id,
                createdAt = ts,
                meta = DemoFixtures.meta,
            ),
        )
    }

    fun demoApprove(visitId: String): VisitOut {
        val visit = get(visitId) ?: throw ApiException("NOT_FOUND", "Visit $visitId not found", 404)
        if (visit.status != "pending") {
            throw ApiException("INVALID_STATE", "Visit must be pending to approve", 409)
        }
        val passId = visit.passId ?: nextPassId()
        val token = visit.qrToken ?: "local-qr-$passId"
        return put(visit.copy(status = "approved", passId = passId, qrToken = token))
    }

    fun scan(passId: String?, token: String?, action: String, gateId: String?): VisitOut {
        if (passId.isNullOrBlank() && token.isNullOrBlank()) {
            throw ApiException("VALIDATION", "token or passId required", 400)
        }
        val visit = findByPass(passId, token)
            ?: throw ApiException("NOT_FOUND", "Pass not found", 404)
        return when (action) {
            "check_in" -> {
                if (visit.status != "approved") {
                    throw ApiException("INVALID_STATE", "Visit must be approved to check in", 409)
                }
                put(visit.copy(status = "inside", timeIn = nowIst(), gateId = gateId ?: visit.gateId))
            }
            "check_out" -> {
                if (visit.status != "inside") {
                    throw ApiException("INVALID_STATE", "Visit must be inside to check out", 409)
                }
                put(visit.copy(status = "completed", timeOut = nowIst()))
            }
            else -> throw ApiException("VALIDATION", "action must be check_in or check_out", 400)
        }
    }

    fun storyInside(): VisitOut = VisitOut(
        id = DemoFixtures.STORY_VISIT_ID,
        schoolId = DemoFixtures.SCHOOL_ID,
        visitorName = DemoFixtures.demoStory.visitorName,
        mobile = "9822011122",
        visitorType = DemoFixtures.demoStory.visitorType,
        purpose = DemoFixtures.demoStory.purpose,
        hostId = DemoFixtures.HOST_ANITA_ID,
        livePhotoKey = "media/live_photo/priya",
        idType = "Aadhaar",
        idNumber = "XXXX1234",
        gateId = DemoFixtures.GATE_MAIN_ID,
        status = "inside",
        passId = DemoFixtures.STORY_PASS_ID,
        qrToken = DemoFixtures.STORY_QR_TOKEN,
        createdAt = "2026-09-16T14:05:00+05:30",
        timeIn = "2026-09-16T14:10:00+05:30",
        meta = DemoFixtures.meta,
    )

    companion object {
        private val PASS_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        fun nowIst(): String = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).toString()

        fun nextPassId(): String =
            "P-" + (1..4).map { PASS_CHARS.random() }.joinToString("")
    }
}
