package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.GateListResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.StaffListResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitorType
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wave1FixtureTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun demoStoryMatchesHandoff() {
        val story = DemoFixtures.demoStory
        assertEquals("Priya Sharma", story.visitorName)
        assertEquals("Parent", story.visitorType)
        assertEquals("+91 98220 11122", story.mobile)
        assertEquals("H03", story.hostId)
        assertEquals("Anita Joshi", story.hostName)
        assertEquals("Primary Coordinator", story.hostRoleTitle)
        assertEquals("G-MAIN", story.gateId)
        assertEquals("Main Gate", story.gateName)
        assertTrue(MobileIndia.isValid(story.mobile))
    }

    @Test
    fun staffListUsesContractFieldNames() {
        val raw = readFixture("staff.json")
        val parsed = json.decodeFromString<StaffListResponse>(raw)
        assertEquals("DEMO", parsed.meta?.watermark)
        val anita = parsed.data.first { it.id == "H03" }
        assertEquals("SCH-DEMO-01", anita.schoolId)
        assertEquals("Anita Joshi", anita.name)
        assertEquals("Primary Coordinator", anita.roleTitle)
        assertEquals("9000000003", anita.mobile)
        assertEquals("U-HOST", anita.userId)
        assertTrue(anita.active)
        assertEquals(DemoFixtures.staffListResponse(active = true).data.size, parsed.data.size)
    }

    @Test
    fun authMeUsesContractFieldNames() {
        val me = json.decodeFromString<MeResponse>(readFixture("me.json"))
        assertEquals("U-GATE", me.id)
        assertEquals("SCH-DEMO-01", me.schoolId)
        assertEquals("gate", me.role)
        assertEquals("G01", me.staffId)
        assertTrue(me.gateIds.orEmpty().contains("G-MAIN"))
        assertEquals("DEMO", me.meta?.watermark)
    }

    @Test
    fun gatesUseContractFieldNames() {
        val gates = json.decodeFromString<GateListResponse>(readFixture("gates.json"))
        assertEquals(listOf("G-MAIN", "G-PED", "G-STAFF", "G-BUS"), gates.data.map { it.id })
        assertEquals("Main Gate", gates.data.first { it.id == "G-MAIN" }.name)
        gates.data.forEach { assertEquals("SCH-DEMO-01", it.schoolId) }
    }

    @Test
    fun hostPickerExcludesGuardAndKeepsActiveStaff() {
        val hosts = DemoFixtures.hostsForPicker(activeOnly = true)
        assertTrue(hosts.any { it.id == "H03" && it.name == "Anita Joshi" })
        assertTrue(hosts.none { it.roleTitle == "Guard" })
        assertTrue(hosts.all { it.active })
    }

    @Test
    fun visitorTypesMatchContractEnum() {
        assertEquals(
            listOf("Parent", "Vendor", "Guest", "Official", "Alumni"),
            VisitorType.entries.map { it.apiValue },
        )
    }

    @Test
    fun step2RequiresNameMobilePurposeAndHost() {
        val empty = RegistrationDraft(hostId = null, visitorType = "Parent")
        val errors = RegistrationValidator.validateStep2(empty)
        assertTrue(FieldKeys.VISITOR_NAME in errors)
        assertTrue(FieldKeys.MOBILE in errors)
        assertTrue(FieldKeys.PURPOSE in errors)
        assertTrue(FieldKeys.HOST_ID in errors)
    }

    @Test
    fun step2AcceptsDemoStoryDraft() {
        val draft = RegistrationDraft.fromStory(DemoFixtures.demoStory)
        assertTrue(RegistrationValidator.validateStep2(draft).isEmpty())
        assertEquals("+919822011122", MobileIndia.normalizeE164(draft.mobile))
        assertEquals("+91 98220 11122", MobileIndia.formatDisplay("9822011122"))
    }

    @Test
    fun mobileRejectsInvalidAndAcceptsPlus91() {
        assertFalse(MobileIndia.isValid("123"))
        assertFalse(MobileIndia.isValid("1234567890"))
        assertTrue(MobileIndia.isValid("9822011122"))
        assertTrue(MobileIndia.isValid("+91 98220 11122"))
        assertTrue(MobileIndia.isValid("919822011122"))
    }

    @Test
    fun demoStoryJsonRoundTrip() {
        val story = json.decodeFromString<DemoStory>(readFixture("demo-story.json"))
        assertEquals(DemoFixtures.demoStory, story)
    }

    private fun readFixture(name: String): String {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture fixtures/$name"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}
