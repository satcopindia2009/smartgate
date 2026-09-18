package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AuthorizedPickup
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchRequest
import com.satcop.smartvisitor.kiosk.data.model.CampusHoursRow
import com.satcop.smartvisitor.kiosk.data.model.CourierCreate
import com.satcop.smartvisitor.kiosk.data.model.CourierEvent
import com.satcop.smartvisitor.kiosk.data.model.GuardHistoryEvent
import com.satcop.smartvisitor.kiosk.data.model.LostFoundCreate
import com.satcop.smartvisitor.kiosk.data.model.LostFoundItem
import com.satcop.smartvisitor.kiosk.data.model.VisitorPrefill
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.HostNotification
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.Meta
import com.satcop.smartvisitor.kiosk.data.model.PickupCreate
import com.satcop.smartvisitor.kiosk.data.model.PickupOut
import com.satcop.smartvisitor.kiosk.data.model.School
import com.satcop.smartvisitor.kiosk.data.model.SchoolIds
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse
import com.satcop.smartvisitor.kiosk.data.model.StudentOut
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

    @Volatile
    private var signedIn: MeResponse? = null

    override suspend fun login(username: String, password: String): MeResponse =
        withContext(Dispatchers.IO) {
            try {
                val resp = live.login(username.trim(), password)
                val user = resp.user ?: live.me()
                signedIn = user
                dataSource = DataSource.LIVE
                user
            } catch (e: ApiException) {
                live.logout()
                signedIn = null
                dataSource = DataSource.FIXTURES
                throw e
            } catch (e: Exception) {
                live.logout()
                signedIn = null
                dataSource = DataSource.FIXTURES
                throw ApiException("UNAVAILABLE", e.message ?: "Live login failed", 0)
            }
        }

    override suspend fun logout() = withContext(Dispatchers.IO) {
        live.logout()
        signedIn = null
        dataSource = DataSource.FIXTURES
    }

    override suspend fun warmup() = withContext(Dispatchers.IO) {
        if (live.accessToken.isNullOrBlank()) {
            dataSource = DataSource.FIXTURES
            return@withContext
        }
        dataSource = try {
            signedIn = live.me()
            DataSource.LIVE
        } catch (e: Exception) {
            if (shouldFallback(e)) DataSource.FIXTURES else DataSource.LIVE
        }
    }

    override suspend fun me(): MeResponse =
        liveOrFixture { live.me() } ?: signedIn ?: fixtures.me()

    override suspend fun school(): School {
        val me = signedIn
        if (SchoolIds.hidesDemoStory(me?.schoolId)) {
            return School(
                id = SchoolIds.PRANAY,
                name = "Pranay School",
                timezone = "Asia/Kolkata",
                overdueHoursDefault = 4,
                config = DemoFixtures.school.config,
            )
        }
        if (me != null && me.schoolId != DemoFixtures.SCHOOL_ID) {
            return DemoFixtures.school.copy(id = me.schoolId, name = me.schoolId)
        }
        return fixtures.school()
    }

    override suspend fun listStaff(active: Boolean): StaffListResponse =
        liveOrFixture { live.listStaff(active) }
            ?: if (allowDemoFixtures()) fixtures.listStaff(active) else StaffListResponse(emptyList(), DemoFixtures.meta)

    override suspend fun listGates(): GateListResponse =
        liveOrFixture { live.listGates() }
            ?: if (allowDemoFixtures()) fixtures.listGates() else GateListResponse(emptyList(), DemoFixtures.meta)

    override suspend fun listInside(): InsideListResponse =
        liveOrFixture { live.listInside() }
            ?: if (allowDemoFixtures()) fixtures.listInside() else emptyInside()

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
        if (SchoolIds.hidesDemoStory(signedIn?.schoolId)) {
            throw ApiException("NOT_FOUND", "Demo story is off for this school", 404)
        }
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

    override suspend fun listPendingVisits(hostId: String?): List<VisitOut> =
        withContext(Dispatchers.IO) {
            if (dataSource == DataSource.LIVE) {
                try {
                    return@withContext live.listPendingVisits(hostId).data
                } catch (e: Exception) {
                    if (shouldFallback(e)) markFixtures()
                    if (!allowDemoFixtures()) return@withContext emptyList()
                    throw asApi(e, "Pending fetch failed")
                }
            }
            if (!allowDemoFixtures()) emptyList() else emptyList()
        }

    override suspend fun approveVisit(id: String): VisitOut = withContext(Dispatchers.IO) {
        try {
            live.approveVisit(id).also { local.put(it) }
        } catch (e: Exception) {
            throw asApi(e, "Approve failed")
        }
    }

    override suspend fun rejectVisit(id: String, reason: String): VisitOut =
        withContext(Dispatchers.IO) {
            try {
                live.rejectVisit(id, reason).also { local.put(it) }
            } catch (e: Exception) {
                throw asApi(e, "Reject failed")
            }
        }

    override suspend fun meetingDone(id: String): VisitOut = withContext(Dispatchers.IO) {
        try {
            live.meetingDone(id).also { local.put(it) }
        } catch (e: Exception) {
            throw asApi(e, "Meeting-done failed")
        }
    }

    override suspend fun listHostActiveVisits(hostId: String?): List<VisitOut> =
        withContext(Dispatchers.IO) {
            if (dataSource != DataSource.LIVE) return@withContext emptyList()
            try {
                val approved = live.listVisits("approved", hostId).data
                val inside = live.listVisits("inside", hostId).data
                (approved + inside)
                    .filter { it.meetingDoneAt.isNullOrBlank() }
                    .distinctBy { it.id }
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
                emptyList()
            }
        }

    override suspend fun listNotifications(limit: Int): List<HostNotification> =
        withContext(Dispatchers.IO) {
            if (dataSource != DataSource.LIVE) return@withContext emptyList()
            try {
                live.listNotifications(limit).data
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
                emptyList()
            }
        }

    override suspend fun listHours(): List<CampusHoursRow> = withContext(Dispatchers.IO) {
        if (dataSource != DataSource.LIVE) return@withContext emptyList()
        try {
            live.listHours().data
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun loadMediaBytes(key: String): ByteArray? = withContext(Dispatchers.IO) {
        if (key.isBlank() || dataSource != DataSource.LIVE) return@withContext null
        try {
            live.getMediaBytes(key)
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun listStudents(q: String?): List<StudentOut> = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                return@withContext live.listStudents(q).data
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
                if (!allowDemoFixtures()) return@withContext emptyList()
            }
        }
        if (allowDemoFixtures()) PickupFixtures.search(q) else emptyList()
    }

    override suspend fun listAuthorizedPickup(studentId: String): List<AuthorizedPickup> =
        withContext(Dispatchers.IO) {
            if (dataSource == DataSource.LIVE) {
                try {
                    return@withContext live.listAuthorizedPickup(studentId).data
                } catch (e: Exception) {
                    if (shouldFallback(e)) markFixtures()
                    if (!allowDemoFixtures()) return@withContext emptyList()
                }
            }
            if (allowDemoFixtures()) PickupFixtures.authorizedFor(studentId) else emptyList()
        }

    override suspend fun startPickup(body: PickupCreate): PickupOut = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                return@withContext live.startPickup(body)
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                if (!shouldFallback(e) || !allowDemoFixtures()) throw asApi(e, "Pickup failed")
                markFixtures()
            }
        }
        if (!allowDemoFixtures()) {
            throw ApiException("UNAVAILABLE", "Pickup API unreachable", 0)
        }
        PickupFixtures.start(
            studentId = body.studentId,
            gateId = body.gateId,
            pickupReason = body.pickupReason,
            collectorId = body.collectorPickupPersonId,
            collectorName = body.collectorName,
            collectorMobile = body.collectorMobile,
            reasonOther = body.reasonOther,
        )
    }

    override suspend fun consentPickup(id: String, version: String): PickupOut =
        withContext(Dispatchers.IO) {
            if (dataSource == DataSource.LIVE) {
                try {
                    return@withContext live.consentPickup(id, version)
                } catch (e: ApiException) {
                    throw e
                } catch (e: Exception) {
                    throw asApi(e, "Consent failed")
                }
            }
            throw ApiException("UNAVAILABLE", "Consent requires live pickup", 0)
        }

    override suspend fun releasePickup(id: String, photoRef: String): PickupOut =
        withContext(Dispatchers.IO) {
            if (dataSource == DataSource.LIVE) {
                try {
                    return@withContext live.releasePickup(id, photoRef)
                } catch (e: ApiException) {
                    throw e
                } catch (e: Exception) {
                    throw asApi(e, "Release failed")
                }
            }
            throw ApiException("UNAVAILABLE", "Release requires live pickup", 0)
        }


    override suspend fun lookupVisitorByMobile(mobile: String): VisitorPrefill? =
        withContext(Dispatchers.IO) {
            if (dataSource == DataSource.LIVE) {
                try {
                    val resp = live.lookupVisitorByMobile(mobile)
                    return@withContext resp.toPrefill(mobile)
                } catch (e: Exception) {
                    if (e is ApiException && e.httpStatus in setOf(401, 403)) throw e
                    if (shouldFallback(e)) markFixtures()
                }
            }
            GuardAsapFixtures.lookupByMobile(mobile)
        }

    override suspend fun listGuardHistory(
        todayOnly: Boolean,
        datePrefix: String?,
        kind: String?,
        status: String?,
        gateId: String?,
    ): List<GuardHistoryEvent> = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                val day = datePrefix ?: if (todayOnly) {
                    java.time.LocalDate.now(java.time.ZoneId.of("Asia/Calcutta")).toString()
                } else null
                return@withContext live.listGateHistory(
                    dateFrom = day,
                    dateTo = day,
                    kind = kind,
                    status = status,
                    gateId = gateId,
                ).data.map { it.asEvent() }
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
            }
        }
        GuardAsapFixtures.listHistory(todayOnly, datePrefix, kind, status, gateId)
    }

    override suspend fun getGuardHistory(id: String): GuardHistoryEvent? =
        withContext(Dispatchers.IO) {
            listGuardHistory(todayOnly = false).firstOrNull { it.id == id }
                ?: GuardAsapFixtures.getHistory(id)
        }

    override suspend fun receiveCourier(body: CourierCreate): CourierEvent =
        withContext(Dispatchers.IO) {
            if (body.courierCompany.isBlank() || body.recipientName.isBlank() || body.gateId.isBlank()) {
                throw ApiException("VALIDATION", "Company, recipient, and gate are required", 400)
            }
            if (dataSource == DataSource.LIVE) {
                try {
                    return@withContext live.receiveCourier(body)
                } catch (e: ApiException) {
                    if (e.httpStatus in setOf(400, 401, 403, 422)) throw e
                    if (!shouldFallback(e)) throw e
                    markFixtures()
                } catch (e: Exception) {
                    if (!shouldFallback(e)) throw asApi(e, "Courier create failed")
                    markFixtures()
                }
            }
            GuardAsapFixtures.receiveCourier(body, signedIn?.id)
        }

    override suspend fun handOverCourier(id: String): CourierEvent =
        withContext(Dispatchers.IO) {
            if (dataSource == DataSource.LIVE) {
                try {
                    return@withContext live.handOverCourier(id)
                } catch (e: ApiException) {
                    if (e.httpStatus in setOf(400, 401, 403, 404, 409, 422)) throw e
                    if (!shouldFallback(e)) throw e
                    markFixtures()
                } catch (e: Exception) {
                    if (!shouldFallback(e)) throw asApi(e, "Handover failed")
                    markFixtures()
                }
            }
            try {
                GuardAsapFixtures.handOverCourier(id)
            } catch (e: IllegalArgumentException) {
                throw ApiException("NOT_FOUND", e.message ?: "Courier not found", 404)
            }
        }

    override suspend fun listCouriers(): List<CourierEvent> = withContext(Dispatchers.IO) {
        if (dataSource == DataSource.LIVE) {
            try {
                return@withContext live.listCouriers().data
            } catch (e: Exception) {
                if (shouldFallback(e)) markFixtures()
            }
        }
        GuardAsapFixtures.listCouriers()
    }

    override suspend fun createLostFound(body: LostFoundCreate): LostFoundItem =
        withContext(Dispatchers.IO) {
            if (body.description.isBlank() || body.locationFound.isBlank() || body.finderName.isBlank()) {
                throw ApiException("VALIDATION", "Description, location, and finder are required", 400)
            }
            // Living LF create may not exist yet — fixture OK
            GuardAsapFixtures.createLostFound(body, body.gateId)
        }

    override suspend fun checkoutInsideVisit(visitId: String, gateId: String?): VisitOut =
        withContext(Dispatchers.IO) {
            if (dataSource == DataSource.LIVE) {
                try {
                    val updated = live.checkoutVisit(visitId, gateId).also { local.put(it) }
                    GuardAsapFixtures.recordCheckout(
                        visitId = updated.id,
                        visitorName = updated.visitorName ?: visitId,
                        gateId = updated.gateId ?: gateId,
                        mobile = updated.mobile,
                    )
                    return@withContext updated
                } catch (e: ApiException) {
                    if (e.httpStatus == 409 || e.code == "INVALID_STATE") throw e
                    if (!shouldFallback(e)) throw e
                    markFixtures()
                } catch (e: Exception) {
                    if (!shouldFallback(e)) throw asApi(e, "Checkout failed")
                    markFixtures()
                }
            }
            val updated = local.checkoutByVisitId(visitId, gateId)
            GuardAsapFixtures.recordCheckout(updated.id, updated.visitorName ?: visitId, updated.gateId ?: gateId, updated.mobile)
            updated
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

    private fun allowDemoFixtures(): Boolean = !SchoolIds.hidesDemoStory(signedIn?.schoolId)

    private fun emptyInside() = InsideListResponse(data = emptyList(), meta = Meta(watermark = "DEMO"))
}
