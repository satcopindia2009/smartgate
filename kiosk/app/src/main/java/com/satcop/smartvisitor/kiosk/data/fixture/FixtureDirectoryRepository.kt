package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.InsideListResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.School
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse
import com.satcop.smartvisitor.kiosk.data.repository.DirectoryRepository

class FixtureDirectoryRepository : DirectoryRepository {
    override suspend fun me(): MeResponse = DemoFixtures.me

    override suspend fun school(): School = DemoFixtures.school

    override suspend fun listStaff(active: Boolean): StaffListResponse =
        DemoFixtures.staffListResponse(active)

    override suspend fun listGates(): GateListResponse = DemoFixtures.gateListResponse()

    override suspend fun listInside(): InsideListResponse = DemoFixtures.insideListResponse()

    override fun demoStory(): DemoStory = DemoFixtures.demoStory
}
