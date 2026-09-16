package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.fixture.LocalBlacklist
import com.satcop.smartvisitor.kiosk.data.model.IdType
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Wave2FlowTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun step3RequiresLivePhotoAndIdNumberOrImage() {
        val empty = RegistrationDraft()
        val errors = RegistrationValidator.validateStep3(empty)
        assertTrue(FieldKeys.LIVE_PHOTO in errors)
        assertTrue(FieldKeys.ID in errors)
    }

    @Test
    fun step3AcceptsIdNumberWithoutImage() {
        val draft = RegistrationDraft(
            livePhotoCaptured = true,
            idType = IdType.Aadhaar.apiValue,
            idNumber = "XXXX1234",
        )
        assertTrue(RegistrationValidator.validateStep3(draft).isEmpty())
    }

    @Test
    fun step3AcceptsIdImageWithoutNumber() {
        val draft = RegistrationDraft(
            livePhotoCaptured = true,
            idType = IdType.DL.apiValue,
            idImageCaptured = true,
        )
        assertTrue(RegistrationValidator.validateStep3(draft).isEmpty())
    }

    @Test
    fun localBlacklistBlockOnVikramMobile() {
        val hit = LocalBlacklist.match("9876500001", null, null)
        assertNotNull(hit)
        assertEquals("Block", hit!!.severity)
        assertEquals("BL-01", hit.id)
    }

    @Test
    fun localBlacklistAlertOnNehaMobile() {
        val hit = LocalBlacklist.match("+91 98765 00002", "DL", "MH12-XXXX-8890")
        assertNotNull(hit)
        assertEquals("Alert", hit!!.severity)
        assertEquals("BL-02", hit.id)
    }

    @Test
    fun priyaDoesNotMatchBlacklist() {
        assertNull(LocalBlacklist.match("9822011122", "Aadhaar", "XXXX1234"))
    }

    @Test
    fun visitCreateJsonUsesContractFieldNames() {
        val body = VisitCreate(
            visitorName = "Priya Sharma",
            mobile = "9822011122",
            visitorType = "Parent",
            purpose = "PTM follow-up, Class 4B",
            hostId = "H03",
            livePhotoKey = "media/live_photo/priya",
            idType = "Aadhaar",
            idNumber = "XXXX1234",
            gateId = "G-MAIN",
        )
        val keys = json.parseToJsonElement(json.encodeToString(body)).jsonObject.keys
        assertTrue(keys.containsAll(listOf(
            "visitorName", "mobile", "visitorType", "purpose", "hostId",
            "livePhotoKey", "idType", "idNumber", "gateId", "blacklistOverride",
        )))
    }
}
