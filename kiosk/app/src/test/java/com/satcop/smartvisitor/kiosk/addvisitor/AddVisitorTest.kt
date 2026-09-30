package com.satcop.smartvisitor.kiosk.addvisitor

import com.satcop.smartvisitor.kiosk.data.addvisitor.ActiveVisit
import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.FakeAddVisitorLookup
import com.satcop.smartvisitor.kiosk.data.addvisitor.KnownProfile
import com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome
import com.satcop.smartvisitor.kiosk.data.addvisitor.MaskedDisplay
import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind
import com.satcop.smartvisitor.kiosk.data.addvisitor.SavedIdRef
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AvNotice
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AvStage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddVisitorTest {
    private val hosts = setOf("H1", "H2")
    private val vendor = KnownProfile(
        kind = ProfileKind.VENDOR, name = "Ravi Kumar", company = "Sharma Traders",
        idRef = SavedIdRef("Aadhaar", "1234"), lastHostId = "H2", lastPurpose = "Stationery", visitorTypeApi = "Vendor",
    )
    private val visitor = KnownProfile(
        kind = ProfileKind.VISITOR, name = "Asha Rao", idRef = SavedIdRef("DL", "8890"),
        lastHostId = "H1", lastPurpose = "PTM", visitorTypeApi = "Parent",
    )

    // ---- screen 1: number only ----
    @Test fun startsOnNumberOnlyStage() {
        val s = AddVisitorReducer.fresh()
        assertEquals(AvStage.NUMBER, s.stage)
        assertEquals("", s.mobileInput)
    }

    @Test fun mobileValidation() {
        assertFalse(AddVisitorLogic.canLookup(""))
        assertFalse(AddVisitorLogic.canLookup("98220"))
        assertFalse(AddVisitorLogic.canLookup("1234567890")) // first digit not 6-9
        assertTrue(AddVisitorLogic.canLookup("9822011122"))
        assertTrue(AddVisitorLogic.canLookup("+91 98220 11122"))
        assertNull(AddVisitorLogic.mobileError("98"))
        assertEquals("Enter a valid 10-digit mobile number.", AddVisitorLogic.mobileError("1234567890"))
        assertEquals("Enter a valid 10-digit mobile number.", AddVisitorLogic.mobileError("5"))
    }

    @Test fun invalidNumberNeverStartsLookup() {
        val s = AddVisitorReducer.typeMobile(AddVisitorReducer.fresh(), "12345")
        val (next, ten) = AddVisitorReducer.tenDigitsOrError(s)
        assertNull(ten)
        assertEquals("Enter a valid 10-digit mobile number.", next.mobileError)
        assertFalse(next.busy)
    }

    @Test fun validNumberStartsLookupWithTenDigits() {
        val s = AddVisitorReducer.typeMobile(AddVisitorReducer.fresh(), "+91 98220 11122")
        val (next, ten) = AddVisitorReducer.tenDigitsOrError(s)
        assertEquals("9822011122", ten)
        assertTrue(next.busy)
    }

    // ---- server outcomes ----
    @Test fun notFoundOpensBlankVisitorFormWithSelector() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(busy = true), LookupOutcome.NotFound)
        assertEquals(AvStage.FORM, s.stage)
        assertEquals(ProfileKind.VISITOR, s.kind)
        assertFalse(s.returning)
        assertTrue(AddVisitorLogic.showsTypeSelector(s.returning))
    }

    @Test fun existingVendorSkipsSelectorAndOpensVendorForm() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), LookupOutcome.Found(vendor))
        assertEquals(ProfileKind.VENDOR, s.kind)
        assertTrue(s.returning)
        assertFalse(AddVisitorLogic.showsTypeSelector(s.returning))
        // guard cannot flip the kind of a known profile client-side
        assertEquals(ProfileKind.VENDOR, AddVisitorReducer.chooseKind(s, ProfileKind.VISITOR).kind)
    }

    @Test fun newNumberCanChooseVendor() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), LookupOutcome.NotFound)
        assertEquals(ProfileKind.VENDOR, AddVisitorReducer.chooseKind(s, ProfileKind.VENDOR).kind)
    }

    @Test fun blockedShowsRedNoticeAndNoForm() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(busy = true), LookupOutcome.Blocked)
        assertEquals(AvStage.NUMBER, s.stage)
        assertTrue(s.notice is AvNotice.Blocked)
        assertEquals("Entry not allowed. Call the security head.", AddVisitorLogic.BLOCKED)
    }

    @Test fun alreadyInsideShowsBannerWithCheckout() {
        val a = ActiveVisit("V1", "inside", "10:42", "Main Gate")
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), LookupOutcome.AlreadyInside(a))
        assertEquals(AvStage.NUMBER, s.stage)
        assertTrue((s.notice as AvNotice.Inside).active.isInside)
        assertFalse(ActiveVisit("V2", "pending").isInside)
    }

    @Test fun lookupFailureHasNoBlankFormFallback() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), LookupOutcome.Failed("Can't check right now. Try again."))
        assertEquals(AvStage.NUMBER, s.stage)
        assertEquals("Can't check right now. Try again.", (s.notice as AvNotice.Failed).message)
    }

    // ---- forms differ ----
    @Test fun vendorFormHasNoScheduleVisitorFormDoes() {
        assertFalse(AddVisitorLogic.showsSchedule(ProfileKind.VENDOR))
        assertTrue(AddVisitorLogic.showsSchedule(ProfileKind.VISITOR))
        assertEquals("Host / Department", AddVisitorLogic.hostLabel(ProfileKind.VENDOR))
    }

    @Test fun titles() {
        assertEquals("Returning visitor", AddVisitorLogic.formTitle(ProfileKind.VISITOR, true))
        assertEquals("New vendor", AddVisitorLogic.formTitle(ProfileKind.VENDOR, false))
    }

    @Test fun vendorRequiresCompanyVisitorDoesNot() {
        val base = RegistrationDraft(visitorName = "A", mobile = "9822011122", purpose = "x", hostId = "H1")
        assertTrue(FieldKeys.COMPANY in RegistrationValidator.validateStep2(base.copy(profileKind = ProfileKind.VENDOR)))
        assertTrue(RegistrationValidator.validateStep2(base.copy(profileKind = ProfileKind.VISITOR)).isEmpty())
        assertTrue(RegistrationValidator.validateStep2(base.copy(profileKind = ProfileKind.VENDOR, company = "ACME")).isEmpty())
    }

    // ---- prefill (server data only, editable, fresh captures) ----
    @Test fun foundPrefillFillsFromServerAndResetsCaptures() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), LookupOutcome.Found(visitor))
        val d = AddVisitorReducer.draftFor(RegistrationDraft(livePhotoCaptured = true, idImageCaptured = true), s, "9822011122", hosts)
        assertEquals("Asha Rao", d.visitorName)
        assertEquals("PTM", d.purpose)
        assertEquals("H1", d.hostId)
        assertEquals("+91 98220 11122", d.mobile)
        assertEquals("DL ••••8890", d.savedId!!.display)
        assertTrue(d.useSavedId)
        assertEquals("", d.idNumber) // never a value from the server
        assertFalse(d.livePhotoCaptured) // fresh photo every visit
        assertFalse(d.idImageCaptured)
        assertNull(d.scheduledAtMs)
    }

    @Test fun unknownHostFromServerIsNotSelected() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), LookupOutcome.Found(visitor.copy(lastHostId = "GONE")))
        assertNull(AddVisitorReducer.draftFor(RegistrationDraft(), s, "9822011122", hosts).hostId)
    }

    @Test fun vendorApiTypeMapping() {
        assertEquals("Vendor", AddVisitorLogic.apiVisitorType(ProfileKind.VENDOR, null))
        assertEquals("Parent", AddVisitorLogic.apiVisitorType(ProfileKind.VISITOR, "Parent"))
        assertEquals("Guest", AddVisitorLogic.apiVisitorType(ProfileKind.VISITOR, null))
        assertEquals("Guest", AddVisitorLogic.apiVisitorType(ProfileKind.VISITOR, "Vendor"))
    }

    // ---- ID: always required, masked never sent ----
    @Test fun idPhotoAlwaysRequired() {
        val d = RegistrationDraft(livePhotoCaptured = true, idNumber = "ABCD1234", idImageCaptured = false)
        val e = RegistrationValidator.validateStep3(d)
        assertEquals("Take a photo of the ID.", e[FieldKeys.ID_IMAGE])
        assertTrue(RegistrationValidator.validateStep3(d.copy(idImageCaptured = true)).isEmpty())
    }

    @Test fun savedIdSatisfiesNumButNotPhoto() {
        val d = RegistrationDraft(livePhotoCaptured = true, savedId = SavedIdRef("Aadhaar", "1234"), useSavedId = true)
        val e = RegistrationValidator.validateStep3(d)
        assertFalse(FieldKeys.ID in e)
        assertTrue(FieldKeys.ID_IMAGE in e)
    }

    @Test fun noSavedIdAndNoNumberIsError() {
        val e = RegistrationValidator.validateStep3(RegistrationDraft(livePhotoCaptured = true, idImageCaptured = true))
        assertEquals("ID number is required.", e[FieldKeys.ID])
    }

    @Test fun maskedValueIsNeverSentAndNeverAccepted() {
        assertNull(AddVisitorLogic.idNumberToSend(useSavedId = true, typed = "ABCD1234"))
        assertNull(AddVisitorLogic.idNumberToSend(useSavedId = false, typed = "••••1234"))
        assertNull(AddVisitorLogic.idNumberToSend(useSavedId = false, typed = "XXXXXXXX1234"))
        assertNull(AddVisitorLogic.idNumberToSend(useSavedId = false, typed = "  "))
        assertEquals("ABCD1234", AddVisitorLogic.idNumberToSend(useSavedId = false, typed = " ABCD1234 "))
        val masked = RegistrationDraft(livePhotoCaptured = true, idImageCaptured = true, idNumber = "••••1234")
        assertTrue(FieldKeys.ID in RegistrationValidator.validateStep3(masked))
    }

    // ---- masking display ----
    @Test fun maskedDisplay() {
        assertEquals("Aadhaar ••••1234", MaskedDisplay.idRef("Aadhaar", "1234"))
        assertEquals("Aadhaar ••••1234", MaskedDisplay.idRef("Aadhaar", "••••1234"))
        assertEquals("DL ••••", MaskedDisplay.idRef("DL", ""))
        assertEquals("••••••1234", MaskedDisplay.mobile("XXXXXX1234"))
        assertEquals("••••••1122", MaskedDisplay.mobile("9822011122"))
        assertEquals("", MaskedDisplay.mobile(null))
        assertTrue(MaskedDisplay.looksMasked("XXXXXX1234"))
        assertTrue(MaskedDisplay.looksMasked("••••1234"))
        assertFalse(MaskedDisplay.looksMasked("9822011122"))
    }

    // ---- server-side only: fakes ----
    @Test fun lookupIsOnePerContinueAndNoCache() = runBlocking {
        val fake = FakeAddVisitorLookup(mapOf("9822011122" to LookupOutcome.Found(visitor)))
        fake.lookup("9822011122"); fake.lookup("9822011122")
        assertEquals(2, fake.calls.size) // every Continue asks the server again
    }

    @Test fun backReturnsToNumberAndKeepsNothingButTheTypedNumber() {
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(mobileInput = "9822011122"), LookupOutcome.Found(visitor))
        val b = AddVisitorReducer.backToNumber(s)
        assertEquals(AvStage.NUMBER, b.stage)
        assertNull(b.known)
        assertEquals("9822011122", b.mobileInput)
    }
}
