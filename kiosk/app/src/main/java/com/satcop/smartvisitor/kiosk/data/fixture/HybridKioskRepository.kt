package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchRequest
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.School
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.repository.KioskRepository
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HybridKioskRepository(
    private val live: LiveVisitorApi = LiveVisitorApi(),
    private val fixtures: FixtureDirectoryRepository = FixtureDirectoryRepository(),
) : KioskRepository {

    @Volatile
    override var dataSource: DataSource = DataSource.FIXTURES
        private set

    override suspend fun warmup() = withContext(Dispatchers.IO) {
        dataSource = if (live.loginGate()) DataSource.LIVE else DataSource.FIXTURES
    }

    override suspend fun me(): MeResponse = liveOrFixture { live.me() } ?: fixtures.me()

    override suspend fun school(): School = fixtures.school()

    override suspend fun listStaff(active: Boolean): StaffListResponse =
        liveOrFixture { live.listStaff(active) } ?: fixtures.listStaff(active)

    override suspend fun listGates(): GateListResponse =
        liveOrFixture { live.listGates() } ?: fixtures.listGates()

    override suspend fun listInside(): InsideListResponse =
        liveOrFixture { live.listInside() } ?: fixtures.listInside()

    override fun demoStory(): DemoStory = fixtures.demoStory()

    override suspend fun uploadMedia(
        bytes: ByteArray,
        filename: String,
        contentType: String,
        kind: String,
    ): MediaUploadResponse = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            runCatching { live.uploadMedia(bytes, filename, contentType, kind) }.getOrNull()
                ?: localMedia(kind)
        } else {
            localMedia(kind)
        }
    }

    override suspend fun matchBlacklist(
        mobile: String?,
        idType: String?,
        idNumber: String?,
    ): BlacklistEntry? = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            runCatching {
                live.matchBlacklist(
                    BlacklistMatchRequest(mobile = mobile, idType = idType, idNumber = idNumber),
                ).hit
            }.getOrElse { LocalBlacklist.match(mobile, idType, idNumber) }
        } else {
            LocalBlacklist.match(mobile, idType, idNumber)
        }
    }

    override suspend fun createVisit(body: VisitCreate): VisitOut = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                live.createVisit(body)
            } catch (e: ApiException) {
                if (e.code == "BLACKLIST_BLOCK") throw e
                localVisit(body)
            } catch (_: Exception) {
                localVisit(body)
            }
        } else {
            val hit = LocalBlacklist.match(body.mobile, body.idType, body.idNumber)
            if (hit?.severity == "Block") {
                throw ApiException(
                    "BLACKLIST_BLOCK",
                    "Visitor matches blacklist Block entry; override required",
                    403,
                )
            }
            localVisit(body, hit)
        }
    }

    private suspend fun <T> liveOrFixture(block: () -> T): T? = withContext(Dispatchers.IO) {
        if (dataSource != DataSource.LIVE) return@withContext null
        runCatching { block() }.getOrNull()
    }

    private fun localMedia(kind: String) = MediaUploadResponse(
        key = "media/$kind/local-${UUID.randomUUID()}",
        url = null,
        meta = DemoFixtures.meta,
    )

    private fun localVisit(body: VisitCreate, hit: BlacklistEntry? = null): VisitOut {
        val ts = OffsetDateTime.now(ZoneOffset.ofHoursMinutes(5, 30)).toString()
        return VisitOut(
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
        )
    }
}
