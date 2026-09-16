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
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HybridKioskRepository(
    private val live: LiveVisitorApi = LiveVisitorApi(),
    private val fixtures: FixtureDirectoryRepository = FixtureDirectoryRepository(),
    private val local: LocalVisitStore = LocalVisitStore(),
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
            try {
                live.uploadMedia(bytes, filename, contentType, kind)
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
                localMedia(kind)
            }
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
            try {
                live.matchBlacklist(
                    BlacklistMatchRequest(mobile = mobile, idType = idType, idNumber = idNumber),
                ).hit
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
                LocalBlacklist.match(mobile, idType, idNumber)
            }
        } else {
            LocalBlacklist.match(mobile, idType, idNumber)
        }
    }

    override suspend fun createVisit(body: VisitCreate): VisitOut = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                live.createVisit(body).also { local.put(it) }
            } catch (e: ApiException) {
                if (e.code == "BLACKLIST_BLOCK") throw e
                if (shouldFallback(e)) markFixtures()
                local.createPending(body)
            } catch (e: Exception) {
                markFixtures()
                local.createPending(body)
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
            local.createPending(body, hit)
        }
    }

    override suspend fun getVisit(id: String): VisitOut = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                live.getVisit(id).also { local.put(it) }
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
                local.get(id) ?: throw asApi(e, "Visit fetch failed")
            }
        } else {
            local.get(id) ?: throw ApiException("NOT_FOUND", "Visit $id not found", 404)
        }
    }

    override suspend fun scanPass(
        passId: String?,
        token: String?,
        action: String,
        gateId: String?,
    ): VisitOut = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                live.scanPass(passId = passId, token = token, action = action, gateId = gateId)
                    .also { local.put(it) }
            } catch (e: Exception) {
                if (shouldFallback(e)) {
                    markFixtures()
                    local.scan(passId, token, action, gateId)
                } else {
                    throw asApi(e, "Scan failed")
                }
            }
        } else {
            local.scan(passId, token, action, gateId)
        }
    }

    override suspend fun demoApprove(visitId: String): VisitOut = withContext(Dispatchers.IO) {
        local.demoApprove(visitId)
    }

    override suspend fun storyPass(): VisitOut = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            runCatching { live.getVisit(DemoFixtures.STORY_VISIT_ID) }.getOrNull()
                ?.also { local.put(it) }
                ?: runCatching { live.getPass(DemoFixtures.STORY_PASS_ID).toVisit() }.getOrNull()
                    ?.also { local.put(it) }
                ?: local.get(DemoFixtures.STORY_VISIT_ID)
                ?: local.storyInside().also { local.put(it) }
        } else {
            local.get(DemoFixtures.STORY_VISIT_ID) ?: local.storyInside().also { local.put(it) }
        }
    }

    private fun markFixtures() {
        dataSource = DataSource.FIXTURES
    }

    private fun shouldFallback(e: Exception): Boolean {
        if (e is ApiException) {
            return e.httpStatus == 0 || e.httpStatus >= 500
        }
        return true
    }

    private fun asApi(e: Exception, fallback: String): ApiException =
        e as? ApiException ?: ApiException("UNAVAILABLE", e.message ?: fallback, 0)

    private suspend fun <T> liveOrFixture(block: () -> T): T? = withContext(Dispatchers.IO) {
        if (dataSource != DataSource.LIVE) return@withContext null
        try {
            block()
        } catch (e: Exception) {
            if (shouldFallback(e)) markFixtures()
            null
        }
    }

    private fun localMedia(kind: String) = MediaUploadResponse(
        key = "media/$kind/local-${UUID.randomUUID()}",
        url = null,
        meta = DemoFixtures.meta,
    )
}
