package com.satcop.smartvisitor.kiosk.addvisitor

import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.ContractLookup
import com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome
import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind
import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.api.ErrorDetails
import com.satcop.smartvisitor.kiosk.data.api.MediaUrl
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.ProfileLookupResponse
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationValidator
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorReducer
import com.satcop.smartvisitor.kiosk.ui.addvisitor.AddVisitorState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Add Visitor bound to the Backend contract (GET /v1/visitors/lookup, POST /v1/visits). Payloads = live samples. */
class ContractBindingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }
    private fun lookup(s: String) = ContractLookup.map(json.decodeFromString<ProfileLookupResponse>(s))

    private val visitorJson = """{"found":true,"profileId":"PRF-00012","kind":"visitor","profileType":"visitor","name":"Asha Rao",
      "photoKey":"media/live_photo/x","photoUrl":"https://api.example/v1/media/live_photo/x?t=abc.def","idType":"Aadhaar","idOnFile":true,
      "idNumberMasked":"••••2346","idReference":"Aadhaar ••••2346","mobileMasked":"XXXXXX1101","lastHostId":"H03","lastPurpose":"PTM",
      "skipTypeSelector":false,"alert":false,"activeVisit":null,"unknownFutureField":1}"""

    @Test fun foundVisitorShowsApiValuesAsIs() {
        val o = lookup(visitorJson) as LookupOutcome.Found
        assertEquals(ProfileKind.VISITOR, o.profile.kind)
        assertEquals("PRF-00012", o.profile.profileId)
        assertEquals("2346", o.profile.idRef?.last4)
        assertEquals("https://api.example/v1/media/live_photo/x?t=abc.def", o.profile.photoUrl)
        assertNull(o.profile.alertMessage)
    }

    @Test fun foundVendorSkipsSelector() {
        val o = lookup("""{"found":true,"profileId":"PRF-2","kind":"vendor","profileType":"vendor","name":"Ravi","company":"Sharma Traders","idOnFile":false,"skipTypeSelector":true}""") as LookupOutcome.Found
        assertEquals(ProfileKind.VENDOR, o.profile.kind)
        assertEquals("Sharma Traders", o.profile.company)
        assertNull(o.profile.idRef)
        val s = AddVisitorReducer.applyOutcome(AddVisitorState(), o)
        assertFalse(AddVisitorLogic.showsTypeSelector(s.returning))
    }

    @Test fun notFound() {
        assertEquals(LookupOutcome.NotFound, lookup("""{"found":false,"visitorTypes":["Guest","Vendor"]}"""))
    }

    @Test fun blockedIsPanelNotForm() {
        assertEquals(LookupOutcome.Blocked, lookup("""{"found":false,"blocked":true,"blockedMessage":"Entry not allowed. Call the security head."}"""))
    }

    @Test fun activeVisitInsideAndPending() {
        val a = lookup("""{"found":true,"kind":"visitor","name":"A","activeVisit":{"id":"V-1","status":"inside","displayStatus":"Inside","since":"2026-09-30T10:42:00+05:30"}}""") as LookupOutcome.AlreadyInside
        assertTrue(a.active.isInside); assertEquals("V-1", a.active.visitId)
        val p = lookup("""{"found":true,"kind":"visitor","name":"A","activeVisit":{"id":"V-2","status":"pending"}}""") as LookupOutcome.AlreadyInside
        assertFalse(p.active.isInside)
    }

    @Test fun alertShowsYellowMessage() {
        val o = lookup("""{"found":true,"kind":"visitor","name":"B","alert":true,"alertMessage":"Alert: call the security head before entry."}""") as LookupOutcome.Found
        assertEquals("Alert: call the security head before entry.", o.profile.alertMessage)
        val d = lookup("""{"found":true,"kind":"visitor","name":"B","alert":true}""") as LookupOutcome.Found
        assertEquals(AddVisitorLogic.ALERT_DEFAULT, d.profile.alertMessage)
    }

    @Test fun approvedAndRejectedToday() {
        val ap = lookup("""{"found":true,"kind":"visitor","name":"B","approvedToday":{"visitId":"V-1","at":"2026-09-30T09:00:00+05:30"}}""") as LookupOutcome.Found
        assertEquals("Approved earlier today", ap.profile.todayNote)
        val rj = lookup("""{"found":true,"kind":"visitor","name":"B","rejectedToday":{"visitId":"V-1","rejectReason":"Not expected"}}""") as LookupOutcome.Found
        assertEquals("Rejected earlier today: Not expected", rj.profile.todayNote)
    }

    @Test fun lookupErrors() {
        assertEquals(LookupOutcome.InvalidNumber, ContractLookup.mapError(ApiException("INVALID_MOBILE", "x", 400)))
        assertEquals(LookupOutcome.Blocked, ContractLookup.mapError(ApiException("BLACKLISTED", "x", 403)))
        val rl = ContractLookup.mapError(ApiException("RATE_LIMITED", "slow", 429)) as LookupOutcome.Failed
        assertEquals("Too many lookups. Try again in a minute.", rl.message)
        val f = ContractLookup.mapError(ApiException("INTERNAL", "boom", 500)) as LookupOutcome.Failed
        assertEquals(ErrorCopy.LOOKUP_FAILED, f.message)
        // session end / FACE_REQUIRED are not swallowed
        assertTrue(runCatching { ContractLookup.mapError(ApiException("TOKEN_EXPIRED", "x", 401)) }.isFailure)
        assertTrue(runCatching { ContractLookup.mapError(ApiException("FACE_REQUIRED", "x", 403)) }.isFailure)
    }

    // ---- media URLs ----
    @Test fun signedUrlKeptVerbatimAndBearerOnlyForApiHost() {
        val base = "https://api.example/v1"
        val abs = MediaUrl.resolve("https://api.example/v1/media/live_photo/x?t=abc.def", base)!!
        assertEquals("https://api.example/v1/media/live_photo/x?t=abc.def", abs.url)
        assertTrue(abs.attachBearer)
        val foreign = MediaUrl.resolve("https://cdn.other.com/a.jpg?t=zzz", base)!!
        assertEquals("https://cdn.other.com/a.jpg?t=zzz", foreign.url)
        assertFalse(foreign.attachBearer)
        val rel = MediaUrl.resolve("/v1/media/id_image/y?t=q", base)!!
        assertEquals("https://api.example/v1/media/id_image/y?t=q", rel.url)
        assertNull(MediaUrl.resolve("media/live_photo/x", base)) // bare keys are never turned into URLs
        assertNull(MediaUrl.resolve("  ", base))
        assertTrue(MediaUrl.hasSignedToken(abs.url))
    }

    // ---- error details ----
    @Test fun errorDetailsFlatten() {
        val el = json.parseToJsonElement("""{"profileId":"PRF-1","profileType":"vendor","activeVisit":{"id":"V-9","status":"pending"},"list":[1]}""").jsonObject
        val m = ErrorDetails.flatten(el)
        assertEquals("PRF-1", m["profileId"]); assertEquals("vendor", m["profileType"])
        assertEquals("V-9", m["activeVisit.id"]); assertEquals("pending", m["activeVisit.status"])
        assertFalse(m.containsKey("list"))
        assertTrue(ErrorDetails.flatten(null).isEmpty())
    }

    // ---- POST /visits body ----
    private fun body(kind: ProfileKind, profileId: String?, confirm: Boolean, idNumber: String?, company: String? = null, sched: String? = null) =
        VisitCreate(
            visitorName = "A", mobile = "9800011101",
            visitorType = AddVisitorLogic.visitorTypeToSend(kind, profileId, confirm),
            purpose = "p", hostId = "H03", livePhotoKey = "k", idType = "Aadhaar", idNumber = idNumber, idImageKey = "i", gateId = "G01",
            profileId = profileId, confirmKindSwitch = if (confirm) true else null, company = company, scheduledAt = sched, attemptId = "att-1",
        )
    private fun enc(v: VisitCreate): JsonObject = json.encodeToJsonElement(VisitCreate.serializer(), v).jsonObject

    @Test fun returningProfileOmitsTypeAndSavedIdAndLinksByProfileId() {
        val j = enc(body(ProfileKind.VISITOR, "PRF-00012", false, AddVisitorLogic.idNumberToSend(true, "••••2346")))
        assertFalse(j.containsKey("visitorType")); assertFalse(j.containsKey("idNumber"))
        assertEquals("\"PRF-00012\"", j["profileId"].toString()); assertEquals("\"att-1\"", j["attemptId"].toString())
        assertFalse(j.containsKey("confirmKindSwitch")); assertFalse(j.containsKey("company")); assertFalse(j.containsKey("scheduledAt"))
    }

    @Test fun maskedValuesNeverSerialised() {
        for (m in listOf("••••2346", "XXXXXX1101", "****1234")) assertNull(AddVisitorLogic.idNumberToSend(false, m))
        assertEquals("ABCD1234", AddVisitorLogic.idNumberToSend(false, " ABCD1234 "))
        val txt = json.encodeToString(VisitCreate.serializer(), body(ProfileKind.VISITOR, "P", false, AddVisitorLogic.idNumberToSend(false, "••••2346")))
        assertFalse(txt.contains("••••")); assertFalse(txt.contains("XXXXXX"))
    }

    @Test fun newNumberSendsExplicitTypeGuestOrVendor() {
        assertEquals("Guest", AddVisitorLogic.visitorTypeToSend(ProfileKind.VISITOR, null, false))
        assertEquals("Vendor", AddVisitorLogic.visitorTypeToSend(ProfileKind.VENDOR, null, false))
    }

    @Test fun confirmKindSwitchSendsTypeAndFlag() {
        assertEquals("Guest", AddVisitorLogic.visitorTypeToSend(ProfileKind.VISITOR, "PRF-2", true))
        val j = enc(body(ProfileKind.VISITOR, "PRF-2", true, "ABCD1234"))
        assertEquals("true", j["confirmKindSwitch"].toString()); assertEquals("\"Guest\"", j["visitorType"].toString())
    }

    @Test fun switchKindReducerFlipsAndMarks() {
        val s0 = AddVisitorState(kind = ProfileKind.VENDOR)
        val (s, d) = AddVisitorReducer.switchKind(s0, RegistrationDraft(profileKind = ProfileKind.VENDOR, scheduledAtMs = null))
        assertEquals(ProfileKind.VISITOR, s.kind); assertTrue(d.confirmKindSwitch); assertEquals(ProfileKind.VISITOR, d.profileKind)
    }

    @Test fun vendorSendsTrimmedCompanyNoScheduleVisitorSchedules() {
        assertEquals("Sharma Traders", AddVisitorLogic.companyToSend(ProfileKind.VENDOR, "  Sharma Traders "))
        assertNull(AddVisitorLogic.companyToSend(ProfileKind.VENDOR, "   "))
        assertNull(AddVisitorLogic.companyToSend(ProfileKind.VISITOR, "Acme"))
        assertNull(AddVisitorLogic.scheduledAtToSend(ProfileKind.VENDOR, 1_790_000_000_000))
        assertNull(AddVisitorLogic.scheduledAtToSend(ProfileKind.VISITOR, null))
        assertTrue(AddVisitorLogic.scheduledAtToSend(ProfileKind.VISITOR, 1_790_000_000_000)!!.endsWith("+05:30"))
    }

    @Test fun vendorCompanyEnforcedClientSide() {
        val d = RegistrationDraft(profileKind = ProfileKind.VENDOR, visitorName = "Ravi", mobile = "9800011110", purpose = "Delivery", hostId = "H03", company = "   ")
        assertTrue(RegistrationValidator.validateStep2(d).containsKey("company"))
        assertFalse(RegistrationValidator.validateStep2(d.copy(company = " Sharma ")).containsKey("company"))
    }

    // ---- create response ----
    @Test fun vendorCreateResponseIsCheckedIn() {
        val v = json.decodeFromString<VisitOut>("""{"id":"V-109","status":"inside","statusCode":"CHECKED_IN","displayStatus":"Checked in","kind":"vendor","passId":"P1","profileCreated":true,"idNumber":"••••2346","mobile":"XXXXXX1110"}""")
        assertTrue(v.isVendor); assertEquals("inside", v.status); assertEquals("CHECKED_IN", v.statusCode)
        val r = json.decodeFromString<VisitOut>("""{"id":"V-1","status":"pending","idempotentReplay":true}""")
        assertEquals(true, r.idempotentReplay)
    }

    // ---- error copy for save errors ----
    @Test fun saveErrorCopy() {
        assertEquals("Check the ID number", ErrorCopy.forApi(ApiException("INVALID_ID_FORMAT", "bad", 400)))
        assertEquals(ErrorCopy.BLACKLISTED, ErrorCopy.forApi(ApiException("BLACKLISTED", "Entry not allowed. Call the security head.", 403)))
        assertEquals(ErrorCopy.VENDOR_NO_SCHEDULE, ErrorCopy.forApi(ApiException("VENDOR_NO_SCHEDULE", "x", 400)))
        assertEquals(ErrorCopy.GENERIC, ErrorCopy.forApi(ApiException("INTERNAL", "boom", 500)))
        assertNotNull(ErrorCopy.ALREADY_INSIDE)
    }
}
