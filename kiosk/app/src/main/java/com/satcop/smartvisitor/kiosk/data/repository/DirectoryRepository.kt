package com.satcop.smartvisitor.kiosk.data.repository

import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.School
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse

/**
 * Directory surface used by Wave 1. Live Retrofit client can replace the
 * fixture implementation later without changing field names.
 */
interface DirectoryRepository {
    suspend fun me(): MeResponse
    suspend fun school(): School
    suspend fun listStaff(active: Boolean = true): StaffListResponse
    suspend fun listGates(): GateListResponse
    suspend fun listInside(): InsideListResponse
    fun demoStory(): DemoStory
}
