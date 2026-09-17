package com.satcop.smartvisitor.kiosk.data.repository

import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchRequest
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut

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
    ): MediaUploadResponse

    suspend fun matchBlacklist(
        mobile: String?,
        idType: String?,
        idNumber: String?,
    ): BlacklistEntry?

    suspend fun createVisit(body: VisitCreate): VisitOut

    suspend fun getVisit(id: String): VisitOut

    suspend fun scanPass(
        passId: String? = null,
        token: String? = null,
        action: String,
        gateId: String? = null,
    ): VisitOut

    /** Fixture-only host approve. Never POSTs /visits/{id}/approve as the gate role. */
    suspend fun demoApprove(visitId: String): VisitOut

    suspend fun storyPass(): VisitOut
}
