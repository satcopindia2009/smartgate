package com.satcop.smartvisitor.kiosk.binding

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.api.LiveOtpRepository
import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.LoginResponse
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitCreate
import com.satcop.smartvisitor.kiosk.data.otp.OtpApiException
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceRules
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.LockState
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

/** Clock-in addendum + OTP contract (2026-09-30, LIVE). Payloads are the live responses (trimmed). */
class ClockInOtpBindingTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val signed = "https://api.example/v1/media/media/guard_attendance/54ee?t=eyJ.abc.def"

    private val checkInJson = """{"id":"GA-0034","recordId":"GA-0034","action":"Check In","actionAt":"2026-09-30T20:17:31+05:30","actionTimeDisplay":"08:17 pm",
      "gateId":"G-MAIN","gateName":"Main Gate","gateSource":"assignment","schoolName":"Demo International School","selfieUploaded":true,"checkInSelfieUploaded":true,
      "guardPhotoUrl":"$signed","missedClockOut":false,"flags":[],"displayStatus":"In Progress","geofenceStatus":"inside",
      "currentStatus":{"state":"on_duty","label":"On duty","text":"Checked in at 08:17 pm · Main Gate","chipColor":"green","checkedInAtDisplay":"08:17 pm","nextAction":"check_out","actionLabel":"Proceed to Check Out","shiftComplete":false,"note":null}}"""

    @Test fun resultUsesServerFieldsAsReturned() {
        val row = json.decodeFromString<AttendanceRow>(checkInJson)
        val r = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row)
        assertEquals("Check In", r.action); assertEquals("08:17 pm", r.time); assertEquals("Main Gate", r.gate)
        assertEquals("GA-0034", r.recordId); assertTrue(r.selfieUploaded); assertNull(r.flaggedNote)
        assertEquals(signed, r.guardPhotoUrl) // verbatim, ?t= intact
    }

    @Test fun clockOutResult() {
        val row = json.decodeFromString<AttendanceRow>("""{"id":"GA-0036","recordId":"GA-0036","action":"Check Out","actionAt":"2026-09-30T21:00:22+05:30","actionTimeDisplay":"09:00 pm","gateName":"Main Gate","selfieUploaded":true,"checkInSelfieUploaded":true,"displayStatus":"Completed","currentStatus":{"state":"checked_out","label":"Checked out","text":"Checked in 09:00 pm · Checked out 09:00 pm","shiftComplete":true,"note":"Your shift for today is complete."}}""")
        val r = ClockInLogic.resultFrom(AttendanceMode.CLOCK_OUT, row)
        assertEquals("Check Out", r.action); assertEquals("09:00 pm", r.time)
        assertEquals("Checked out", row.currentStatus?.label); assertTrue(row.currentStatus!!.shiftComplete)
    }

    @Test fun selfieNotUploadedFlagIsShownAsReturned() {
        val row = json.decodeFromString<AttendanceRow>("""{"id":"GA-1","selfieUploaded":false,"gateName":"—"}""")
        val r = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row)
        assertFalse(r.selfieUploaded); assertEquals("—", r.gate)
    }

    @Test fun todayBlockDrivesStatusCardAndLock() {
        val off = json.decodeFromString<TodayAttendance>("""{"dutyDate":"2026-09-30","schoolName":"Demo International School","guardName":"Guard G1","guardPhotoUrl":null,"selfieUploaded":false,"attendanceStatus":"NONE","canCheckIn":true,"gateName":"Main Gate","gateId":"G-MAIN","currentStatus":{"state":"off_duty","label":"Off duty","text":"Not checked in yet","nextAction":"check_in","actionLabel":"Proceed to Check In","shiftComplete":false}}""")
        assertEquals("Demo International School", off.schoolName); assertEquals("Guard G1", off.guardName); assertEquals("Main Gate", off.gateName)
        val c = ClockInLogic.statusCard(off)
        assertEquals("Off duty", c.chip); assertEquals("Not checked in yet", c.detail); assertTrue(c.canProceed)
        assertEquals(LockState.LOCKED, ClockInLogic.lockState(off, true))
        val on = json.decodeFromString<TodayAttendance>("""{"attendanceStatus":"PRESENT","canClockOut":true,"currentStatus":{"state":"on_duty","label":"On duty","text":"Checked in at 09:00 pm · Main Gate","nextAction":"check_out","shiftComplete":false}}""")
        assertEquals("Checked in at 09:00 pm · Main Gate", ClockInLogic.statusCard(on).detail); assertEquals(LockState.UNLOCKED, ClockInLogic.lockState(on, true))
        val done = json.decodeFromString<TodayAttendance>("""{"attendanceStatus":"CLOCKED_OUT","currentStatus":{"state":"checked_out","label":"Checked out","text":"Checked in 09:12 am · Checked out 05:31 pm","nextAction":null,"shiftComplete":true,"note":"Your shift for today is complete."}}""")
        val d = ClockInLogic.statusCard(done)
        assertFalse(d.canProceed); assertEquals("Your shift for today is complete.", d.note); assertEquals(LockState.SHIFT_COMPLETE, ClockInLogic.lockState(done, true))
    }

    @Test fun missedClockOutFlagDecodes() {
        val row = json.decodeFromString<AttendanceRow>("""{"id":"GA-9","missedClockOut":true,"flags":["MISSED_CLOCK_OUT"],"displayStatus":"Missed clock-out"}""")
        assertTrue(row.missedClockOut); assertEquals(listOf("MISSED_CLOCK_OUT"), row.flags)
    }

    @Test fun loginAndMeCarryGuardNameSchoolNameProfilePhoto() {
        val login = json.decodeFromString<LoginResponse>("""{"accessToken":"t","guardName":"Guard G1","schoolName":"Demo International School","profilePhotoUrl":"$signed","user":{"id":"U","schoolId":"S","role":"guard","displayName":"Guard G1","guardName":"Guard G1","schoolName":"Demo International School","profilePhotoUrl":null,"mobileVerified":false,"verifiedAt":null}}""")
        assertEquals("Demo International School", login.schoolName); assertEquals(signed, login.profilePhotoUrl)
        assertEquals(false, login.user?.mobileVerified); assertEquals("Guard G1", login.user?.guardName)
        val me = json.decodeFromString<MeResponse>("""{"id":"U-HOST","schoolId":"S","role":"host","displayName":"Anita","schoolName":"Demo International School","profilePhotoUrl":null,"mobileVerified":true,"verifiedAt":"2026-09-30T20:52:03+05:30","otpNoticeVersion":"otp_notice_en_hi_v1"}""")
        assertEquals(true, me.mobileVerified); assertEquals("otp_notice_en_hi_v1", me.otpNoticeVersion)
    }

    // ---- errors ----
    @Test fun tokenExpiredIsTheSessionExpiredPath() {
        assertEquals("Your session has expired. Please sign in again.", ErrorCopy.forApi(ApiException("TOKEN_EXPIRED", "x", 401)))
        assertTrue(ErrorCopy.isSessionEnd(ApiException("TOKEN_EXPIRED", "x", 401)))
    }

    @Test fun faceAndLocationMessagesAreServerPlainText() {
        assertEquals("Face verification required. Please verify your face to continue.", ErrorCopy.forApi(ApiException("FACE_REQUIRED", "Face verification required. Please verify your face to continue.", 403)))
        assertEquals("Location access is needed to check in.", ErrorCopy.forApi(ApiException("LOCATION_REQUIRED", "Location access is needed to check in.", 403)))
        assertEquals("Your location is required to clock out. Please turn on GPS and try again.", ErrorCopy.forApi(ApiException("GPS_REQUIRED", "x", 400)))
        assertTrue(ClockInLogic.checkInRefusedWithoutLocation("LOCATION_REQUIRED", true))
        assertTrue(ClockInLogic.locationProblem("LOCATION_REQUIRED"))
    }

    @Test fun neverSendHalfALatLngPair() {
        val full = AttendanceRules.buildRequest(GpsFix(18.5, 73.8, 12.0, 0L), "b64", java.time.Instant.parse("2026-09-30T10:00:00Z"), "a")
        assertEquals(18.5, full.lat!!, 0.0); assertEquals(73.8, full.lng!!, 0.0)
        val bad = AttendanceRules.buildRequest(GpsFix(200.0, 73.8, 12.0, 0L), "b64", java.time.Instant.parse("2026-09-30T10:00:00Z"), "a")
        assertNull(bad.lat); assertNull(bad.lng) // out of range -> whole pair dropped (gpsMissing), never lat alone
        val none = AttendanceRules.buildRequest(null, "b64", java.time.Instant.parse("2026-09-30T10:00:00Z"), "a")
        assertNull(none.lat); assertNull(none.lng); assertTrue(none.gpsMissing)
    }

    // ---- OTP parsing (live samples) ----
    @Test fun sendResponse() {
        val s = LiveOtpRepository.parseSent("""{"otpId":"otp_f3a0","purpose":"staff_verify","maskedMobile":"••••••1150","channel":"sms","expiresInSec":300,"expiresAt":"x","resendAfterSec":60,"resendsLeft":3,"otpNoticeVersion":"otp_notice_en_hi_v1","meta":{"mock":true,"demo":true,"mode":"mock"}}""")
        assertEquals("otp_f3a0", s.otpId); assertEquals("••••••1150", s.maskedMobile); assertEquals(60, s.resendAfterSec)
        assertEquals(300, s.expiresInSec); assertEquals(3, s.resendsLeft); assertTrue(s.mock)
    }

    @Test fun verifyResponses() {
        val st = LiveOtpRepository.parseVerified("""{"verified":true,"verifiedAt":"2026-09-30T20:52:03+05:30","purpose":"staff_verify","otpId":"o"}""")
        assertNull(st.visitorVerifyId); assertNull(st.resetToken)
        val v = LiveOtpRepository.parseVerified("""{"verified":true,"verifiedAt":"t","purpose":"visitor_verify","otpId":"o","visitorVerifyId":"o","otpNoticeVersion":"v1"}""")
        assertEquals("o", v.visitorVerifyId)
        val p = LiveOtpRepository.parseVerified("""{"verified":true,"purpose":"password_reset","resetToken":"rt","resetTokenExpiresInSec":600}""")
        assertEquals("rt", p.resetToken)
    }

    @Test fun statusShowSendAgainIsServerDriven() {
        assertTrue(LiveOtpRepository.parseStatus("""{"otpId":"o","status":"active","deliveryStatus":"sent","delivered":false,"secondsSinceSend":61,"showSendAgain":true,"expiresInSec":238}""").showSendAgain)
        assertFalse(LiveOtpRepository.parseStatus("""{"otpId":"o","status":"active","showSendAgain":false}""").showSendAgain)
    }

    @Test fun errorMappingKeepsServerTextAndDetails() {
        val inv = LiveOtpRepository.mapError(ApiException("OTP_INVALID", "That code is not right. 4 tries left.", 400, mapOf("triesLeft" to "4"))) as OtpApiException
        assertEquals(4, inv.triesLeft); assertEquals("That code is not right. 4 tries left.", inv.serverMessage)
        val lock = LiveOtpRepository.mapError(ApiException("OTP_LOCKED", "Too many attempts. Try again in 30 minutes.", 429, mapOf("retryAfterSec" to "1800"))) as OtpApiException
        assertEquals(1800, lock.retryAfterSec)
        val wait = LiveOtpRepository.mapError(ApiException("OTP_RESEND_WAIT", "Resend code in 59 s.", 429, mapOf("retryAfterSec" to "59"))) as OtpApiException
        assertEquals(59, wait.retryAfterSec)
        for (c in listOf("OTP_EXPIRED", "OTP_USED", "OTP_RESEND_LIMIT", "OTP_DAILY_LIMIT", "OTP_SEND_FAILED", "OTP_REQUIRED", "NO_MOBILE", "INVALID_MOBILE", "RESET_TOKEN_INVALID", "BLACKLISTED")) {
            val e = LiveOtpRepository.mapError(ApiException(c, "plain server text", if (c == "OTP_SEND_FAILED") 503 else 400))
            assertTrue(c, e is OtpApiException); assertEquals("plain server text", (e as OtpApiException).serverMessage)
        }
        // session / face errors are not OTP errors and keep their own paths
        assertTrue(LiveOtpRepository.mapError(ApiException("TOKEN_EXPIRED", "x", 401)) is ApiException)
        assertTrue(LiveOtpRepository.mapError(ApiException("FACE_REQUIRED", "x", 403)) is ApiException)
    }

    @Test fun schoolSettingsParse() {
        val s = LiveOtpRepository.parseSettings("""{"id":"S","otpMode":"mock","otpChannel":"sms","visitorOtpEnabled":true,"visitorOtpAllowSkip":false,"otpProviderStatus":"mock"}""")
        assertTrue(s.visitorOtpEnabled); assertFalse(s.visitorOtpAllowSkip)
        val off = LiveOtpRepository.parseSettings("""{"id":"S"}""")
        assertFalse(off.visitorOtpEnabled) // absent = OTP off
    }

    @Test fun visitCarriesOtpIdOnlyWhenVerified() {
        val base = VisitCreate(visitorName = "A", mobile = "9800011151", purpose = "p", hostId = "H03", livePhotoKey = "k", idType = "Aadhaar", gateId = "G-MAIN")
        val enc = Json { encodeDefaults = true }
        assertFalse(enc.encodeToString(VisitCreate.serializer(), base).contains("otpId"))
        assertTrue(enc.encodeToString(VisitCreate.serializer(), base.copy(otpId = "otp_1")).contains("\"otpId\":\"otp_1\""))
    }
}
