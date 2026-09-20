package com.satcop.smartvisitor.kiosk.data.repository

import com.satcop.smartvisitor.kiosk.data.model.AuthorizedPickup
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.CampusHoursRow
import com.satcop.smartvisitor.kiosk.data.model.CourierCreate
import com.satcop.smartvisitor.kiosk.data.model.CourierEvent
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.GuardHistoryEvent
import com.satcop.smartvisitor.kiosk.data.model.HostNotification
import com.satcop.smartvisitor.kiosk.data.model.LostFoundCreate
import com.satcop.smartvisitor.kiosk.data.model.LostFoundItem
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.PickupCreate
import com.satcop.smartvisitor.kiosk.data.model.PickupOut
import com.satcop.smartvisitor.kiosk.data.model.StudentOut
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.model.VisitorPrefill

interface KioskRepository : DirectoryRepository {
    val dataSource: DataSource
    suspend fun login(username: String, password: String): MeResponse
    suspend fun logout()
    suspend fun warmup()
    suspend fun uploadMedia(
        bytes: ByteArray,
        filename: String,
        contentType: String,
        kind: String,
        consentAt: String? = null,
        consentVersion: String? = null,
    ): MediaUploadResponse

    suspend fun matchBlacklist(
        mobile: String?,
        idType: String?,
        idNumber: String?,
    ): BlacklistEntry?

    suspend fun createVisit(body: VisitCreate): VisitOut
    suspend fun getVisit(id: String): VisitOut
    suspend fun listPendingVisits(hostId: String?): List<VisitOut>
    suspend fun approveVisit(id: String): VisitOut
    suspend fun rejectVisit(id: String, reason: String): VisitOut
    suspend fun meetingDone(id: String): VisitOut
    suspend fun listHostActiveVisits(hostId: String?): List<VisitOut>
    suspend fun listNotifications(limit: Int = 50): List<HostNotification>
    suspend fun listHours(): List<CampusHoursRow>
    suspend fun loadMediaBytes(key: String): ByteArray?
    suspend fun listStudents(q: String?): List<StudentOut>
    suspend fun listAuthorizedPickup(studentId: String): List<AuthorizedPickup>
    suspend fun startPickup(body: PickupCreate): PickupOut
    suspend fun consentPickup(id: String, version: String): PickupOut
    suspend fun releasePickup(id: String, photoRef: String): PickupOut
    suspend fun scanPass(
        passId: String? = null,
        token: String? = null,
        action: String,
        gateId: String? = null,
    ): VisitOut
    suspend fun demoApprove(visitId: String): VisitOut
    suspend fun storyPass(): VisitOut

    // Guard ASAP — living: lookup-by-mobile, gate/history, couriers
    suspend fun lookupVisitorByMobile(mobile: String): VisitorPrefill?
    suspend fun listGuardHistory(
        todayOnly: Boolean = true,
        datePrefix: String? = null,
        kind: String? = null,
        status: String? = null,
        gateId: String? = null,
    ): List<GuardHistoryEvent>
    suspend fun getGuardHistory(id: String): GuardHistoryEvent?
    suspend fun receiveCourier(body: CourierCreate): CourierEvent
    suspend fun handOverCourier(id: String): CourierEvent
    suspend fun listCouriers(): List<CourierEvent>
    suspend fun createLostFound(body: LostFoundCreate): LostFoundItem
    suspend fun checkoutInsideVisit(visitId: String, gateId: String?): VisitOut
}
