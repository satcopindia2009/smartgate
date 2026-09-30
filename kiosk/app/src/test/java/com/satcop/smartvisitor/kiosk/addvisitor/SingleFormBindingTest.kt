package com.satcop.smartvisitor.kiosk.addvisitor

import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.ContractLookup
import com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome
import com.satcop.smartvisitor.kiosk.data.model.ProfileLookupResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AvNotice
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Single-form layout ruling + OPEN_VISIT_EXISTS / openVisit binding (D15, AC-AV7). */
class SingleFormBindingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Test fun lookupOpenVisitMapsToOpenVisitOutcome() {
        val r = json.decodeFromString<ProfileLookupResponse>(
            """{"found":true,"profileId":"PRF-9","kind":"visitor","name":"Q","activeVisit":null,
               "openVisit":{"visitId":"V-20260930-128","status":"approved","displayStatus":"Approved","hostId":"H03","hostName":"Anita","purpose":"PTM","since":"10:15"}}""",
        )
        val o = ContractLookup.map(r) as LookupOutcome.OpenVisit
        assertEquals("V-20260930-128", o.open.visitId)
        assertTrue(o.open.isApproved)
        assertEquals("Anita", o.open.hostName)
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), o)
        assertTrue(s.notice is AvNotice.Open)
    }

    @Test fun pendingOpenVisitIsNotApproved() {
        val o = AddVisitorLogic.openVisitFromDetails(mapOf("visitId" to "V-1", "status" to "pending", "hostName" to "Mr Rao"))!!
        assertFalse(o.isApproved)
        assertNull(AddVisitorLogic.openVisitFromDetails(emptyMap()))
    }

    @Test fun idTypeNameSentOnlyWhenPresent() {
        val base = VisitCreate(visitorName = "A", mobile = "9800011101", purpose = "p", hostId = "H03", livePhotoKey = "k", idType = "Aadhaar", gateId = "G01")
        assertFalse(json.encodeToJsonElement(VisitCreate.serializer(), base).jsonObject.containsKey("idTypeName"))
        val other = base.copy(idType = "Other", idTypeName = "Ration card")
        assertEquals("\"Ration card\"", json.encodeToJsonElement(VisitCreate.serializer(), other).jsonObject["idTypeName"].toString())
    }

    @Test fun formValidationCoversIdAndBothPhotos() {
        val e = RegistrationValidator.validateForm(RegistrationDraft(visitorName = "A", mobile = "9800011101", purpose = "p", hostId = "H03"))
        assertEquals("ID number is required.", e[FieldKeys.ID])
        assertEquals("Take a photo of the ID.", e[FieldKeys.ID_IMAGE])
        assertEquals("Take a photo of the visitor.", e[FieldKeys.LIVE_PHOTO])
    }

    @Test fun otherIdTypeNeedsName() {
        val d = RegistrationDraft(
            visitorName = "A", mobile = "9800011101", purpose = "p", hostId = "H03", idType = "Other", idNumber = "R123",
            livePhotoCaptured = true, idImageCaptured = true,
        )
        assertNotNull(RegistrationValidator.validateForm(d)[FieldKeys.ID_TYPE_NAME])
        assertTrue(RegistrationValidator.validateForm(d.copy(idTypeName = "Ration card")).isEmpty())
    }

    @Test fun mobileIsMasked() {
        assertEquals("XXXXXX1117", AddVisitorLogic.maskedMobileForForm("98000 11117"))
        assertEquals("", AddVisitorLogic.maskedMobileForForm("12"))
    }

    @Test fun noConsentScreenCopyLeft() {
        assertTrue(AddVisitorLogic.CONSENT_NOTICE_EN.isNotBlank())
    }
}
