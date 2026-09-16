package com.satcop.smartvisitor.kiosk.data.repository

import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.BlacklistMatchRequest
import com.satcop.smartvisitor.kiosk.data.model.DataSource
import com.satcop.smartvisitor.kiosk.data.model.MediaUploadResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut

interface KioskRepository : DirectoryRepository {
    val dataSource: DataSource
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
}
