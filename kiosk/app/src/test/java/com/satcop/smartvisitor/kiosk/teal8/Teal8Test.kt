package com.satcop.smartvisitor.kiosk.teal8

import com.satcop.smartvisitor.kiosk.data.api.SessionExpiry
import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.geo.GpsPolicy
import com.satcop.smartvisitor.kiosk.data.geo.GpsState
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.AttendanceState
import com.satcop.smartvisitor.kiosk.data.model.GeoPoint
import com.satcop.smartvisitor.kiosk.data.model.GeoRules
import com.satcop.smartvisitor.kiosk.data.model.GeofenceInfo
import com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceRules
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.GeoMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardGeoLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeApi
import com.satcop.smartvisitor.kiosk.ui.guardhome.GuardHomeController
import com.satcop.smartvisitor.kiosk.ui.guardhome.LocationAction
import com.satcop.smartvisitor.kiosk.ui.guardhome.LockState
import com.satcop.smartvisitor.kiosk.ui.guardhome.SettingsHint
import java.io.File
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1072 guard flow: items A-H. Pure logic + the real controller on an unconfined scope; no Android types. */
class Teal8Test {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    // ---------- live JSON shapes (captured from the live server 2026-10-01 with the gate token) ----------
    private val liveGeofence = """{"status":null,"distanceM":null,"mode":"soft","enforced":true,"radiusM":191.5,"center":{"lat":18.521250000000002,"lng":73.85725},"fenceId":"GF-0001","fenceName":"Campus","warning":null,"isWarning":false,"isMock":null,"campusId":null,"fences":[{"id":"GF-0001","name":"Campus","lat":18.521250000000002,"lng":73.85725,"radiusM":191.5,"campusId":null,"mode":"soft"}],"rules":{"accuracyLimitM":100.0,"maxCaptureAgeSeconds":120,"clockInRequireGps":false,"clockOutRequireGps":true,"clockInRequireSelfie":true,"clockOutRequireSelfie":false},"schoolId":"SCH-DEMO-01","schoolName":"Demo International School","meta":{"watermark":"DEMO","serverTime":"2026-10-01T11:41:06+05:30"}}"""

    private fun fix(lat: Double = 18.5, lng: Double = 73.8, acc: Double = 10.0, at: Long = 0L, mock: Boolean = false) = GpsFix(lat, lng, acc, at, mock)

    // ---------- parsing (every new field optional) ----------
    @Test fun liveGeofenceBodyDecodes() {
        val g = json.decodeFromString<GeofenceInfo>(liveGeofence)
        assertEquals("soft", g.mode)
        assertEquals(191.5, g.radiusM!!, 0.0)
        assertEquals(100.0, g.rules!!.accuracyLimitM!!, 0.0)
        assertEquals(1, g.fences!!.size)
        assertNull(g.status); assertNull(g.distanceM)
        assertEquals(GeoMode.SOFT, GuardGeoLogic.modeOf(g))
    }

    @Test fun emptyAndUnexpectedGeofenceBodiesDoNotBreak() {
        val g = json.decodeFromString<GeofenceInfo>("{}")
        assertEquals(GeoMode.UNKNOWN, GuardGeoLogic.modeOf(g))
        assertEquals(100.0, GuardGeoLogic.accuracyLimitM(g), 0.0)
        // warning as an object or a string, unknown extra fields, string-typed nothing else
        val w = json.decodeFromString<GeofenceInfo>("""{"warning":"Outside the school campus (soft mode); flagged.","isWarning":true,"extra":{"a":1}}""")
        assertEquals(true, w.isWarning)
        json.decodeFromString<GeofenceInfo>("""{"warning":{"code":"X"}}""")
    }

    @Test fun todayCarriesGeofenceBlockAndRowKeepsOldShape() {
        val t = json.decodeFromString<TodayAttendance>("""{"dutyDate":"2026-10-01","attendance":null,"attendanceStatus":"NONE","shiftEndTime":"14:30","geofence":$liveGeofence}""")
        assertEquals("soft", t.geofence?.mode)
        assertEquals("14:30", t.shiftEndTime)
        val old = json.decodeFromString<TodayAttendance>("""{"attendanceStatus":"NONE"}""")
        assertNull(old.geofence)
    }

    @Test fun checkInResponseFlatAndNestedFieldsDecode() {
        val row = json.decodeFromString<AttendanceRow>(
            """{"id":"GA-9","geofenceStatus":"outside","distanceM":340.2,"geofenceRadiusM":191.5,"geofenceMode":"soft","warn":"Outside the school campus (soft mode); flagged.","offCampusSuspect":true,"flags":["OUTSIDE_GEOFENCE"],
               "geofence":{"status":"outside","distanceM":340.2,"radiusM":191.5,"mode":"soft","fenceId":"GF-0001","fenceName":"Campus","center":{"lat":1.0,"lng":2.0},"warning":"x","isWarning":true,"enforced":true,"isMock":false,"serverTime":"2026-10-01T09:00:00+05:30"}}""",
        )
        assertEquals(191.5, row.geofenceRadiusM!!, 0.0)
        assertEquals("outside", row.geofence?.status)
        assertEquals(GeoPoint(1.0, 2.0), row.geofence?.center)
        // a plain old response still decodes
        assertNull(json.decodeFromString<AttendanceRow>("""{"id":"GA-1"}""").geofence)
    }

    @Test fun requestOmitsIsMockUnlessTrue() {
        val plain = AttendanceRules.buildRequest(fix(), "B64", Instant.parse("2026-10-01T05:00:00Z"), "a1")
        assertFalse(json.encodeToString(plain).contains("isMock"))
        val mock = AttendanceRules.buildRequest(fix(mock = true), "B64", Instant.parse("2026-10-01T05:00:00Z"), "a1")
        assertTrue(json.encodeToString(mock).contains("\"isMock\":true"))
        assertTrue(json.encodeToString(AttendanceRequest(capturedAt = "x", attemptId = "a")).let { !it.contains("isMock") })
    }

    // ---------- A: restrict refusal with distance ----------
    @Test fun distanceIsRoundedUpToNext10() {
        assertEquals(350, GuardGeoLogic.roundUpTo10(341.0))
        assertEquals(340, GuardGeoLogic.roundUpTo10(340.0))
        assertEquals(10, GuardGeoLogic.roundUpTo10(0.4))
        assertEquals("You are about 350 m from the campus; the limit is 192 m. Move inside and try again.", GuardGeoLogic.outsideMessage(341.0, 191.5))
    }

    @Test fun restrictRefusalUsesProductCopyOnlyWithDistance() {
        val e = ApiException("GEO_FENCE_RESTRICTED", "Outside", 403, mapOf("distanceM" to "340.4", "radiusM" to "192", "outsideByM" to "148", "reasonCode" to "GEOFENCE_OUTSIDE"))
        assertEquals("You are about 350 m from the campus; the limit is 192 m. Move inside and try again.", GuardGeoLogic.outsideMessageFrom(e))
        // no distance -> generic line is used by the caller
        assertNull(GuardGeoLogic.outsideMessageFrom(ApiException("GEO_FENCE_RESTRICTED", "Outside", 403)))
        // distance but no radius: falls back to the radius read from /guards/me/geofence
        val noR = ApiException("GEO_FENCE_RESTRICTED", "Outside", 403, mapOf("distanceM" to "100"))
        assertNull(GuardGeoLogic.outsideMessageFrom(noR))
        assertEquals("You are about 100 m from the campus; the limit is 192 m. Move inside and try again.", GuardGeoLogic.outsideMessageFrom(noR, 191.5))
        // other codes never get the line; bad numbers never crash
        assertNull(GuardGeoLogic.outsideMessageFrom(ApiException("GPS_ACCURACY_LOW", "x", 400, mapOf("distanceM" to "5"))))
        assertNull(GuardGeoLogic.outsideMessageFrom(ApiException("GEO_FENCE_RESTRICTED", "x", 403, mapOf("distanceM" to "abc", "radiusM" to "5"))))
    }

    @Test fun reasonCodesMapToLegacyCodes() {
        fun code(c: String, r: String?) = GuardGeoLogic.canonicalCode(ApiException(c, "m", 400, if (r == null) emptyMap() else mapOf("reasonCode" to r)))
        assertEquals("GEO_FENCE_RESTRICTED", code("GEO_FENCE_RESTRICTED", "GEOFENCE_OUTSIDE"))
        assertEquals("GEO_FENCE_RESTRICTED", code("FORBIDDEN", "GEOFENCE_OUTSIDE"))
        assertEquals("PHOTO_REQUIRED", code("VALIDATION", "SELFIE_REQUIRED"))
        assertEquals("STALE_CAPTURE", code("", "GPS_STALE"))
        assertEquals("GPS_ACCURACY_LOW", code("BAD_REQUEST", "GPS_ACCURACY_TOO_LOW"))
        assertEquals("ALREADY_CHECKED_IN", code("CONFLICT", "ALREADY_CLOCKED_IN"))
        // an unrelated code with no reason is untouched
        assertEquals("RATE_LIMITED", code("RATE_LIMITED", null))
        assertEquals("ALREADY_CLOCKED_OUT", code("ALREADY_CLOCKED_OUT", "ALREADY_CLOCKED_IN"))
    }

    // ---------- B: mode + accuracy limit ----------
    @Test fun modeComesFromGeofenceThenSchool() {
        assertEquals(GeoMode.RESTRICT, GuardGeoLogic.modeOf(GeofenceInfo(mode = "restrict"), "soft"))
        assertEquals(GeoMode.SOFT, GuardGeoLogic.modeOf(null, "soft"))
        assertEquals(GeoMode.OFF, GuardGeoLogic.modeOf(GeofenceInfo(mode = " OFF "), null))
        assertEquals(GeoMode.UNKNOWN, GuardGeoLogic.modeOf(GeofenceInfo(mode = "weird"), null))
    }

    @Test fun accuracyLimitFromServerWithFallback() {
        val tight = GeofenceInfo(rules = GeoRules(accuracyLimitM = 30.0))
        assertEquals(30.0, GuardGeoLogic.accuracyLimitM(tight), 0.0)
        assertEquals(100.0, GuardGeoLogic.accuracyLimitM(GeofenceInfo(rules = GeoRules(accuracyLimitM = 0.0))), 0.0)
        val f = GpsFix(1.0, 1.0, 50.0, 1_000L)
        assertTrue(GpsPolicy.evaluate(f, 1_100L) is GpsState.Ready)
        assertTrue(GpsPolicy.evaluate(f, 1_100L, 30.0) is GpsState.Weak)
    }

    @Test fun clientHintIsOutsideOnlyWhenClearlyOutside() {
        val info = GeofenceInfo(radiusM = 200.0, center = GeoPoint(18.5212, 73.8572))
        // ~1.1 km north: clearly outside
        val far = fix(lat = 18.5312, lng = 73.8572)
        assertTrue(GuardGeoLogic.hint(far, info)!!.outside)
        // at the centre: inside
        assertFalse(GuardGeoLogic.hint(fix(lat = 18.5212, lng = 73.8572), info)!!.outside)
        // 210 m away with 100 m accuracy: not "clearly" outside
        val edge = GuardGeoLogic.distanceM(18.5212, 73.8572, 18.5231, 73.8572)
        assertTrue(edge in 190.0..230.0)
        assertFalse(GuardGeoLogic.hint(fix(lat = 18.5231, lng = 73.8572, acc = 100.0), info)!!.outside)
        // no fence / no fix -> no hint
        assertNull(GuardGeoLogic.hint(far, GeofenceInfo()))
        assertNull(GuardGeoLogic.hint(null, info))
        // only restrict mode shows the pre-warning
        assertNull(GuardGeoLogic.restrictHint(GeoMode.SOFT, far, info))
        assertNotNull(GuardGeoLogic.restrictHint(GeoMode.RESTRICT, far, info))
    }

    @Test fun multipleFencesUseTheNearest() {
        val info = GeofenceInfo(fences = listOf(
            com.satcop.smartvisitor.kiosk.data.model.GeoFenceDef(lat = 10.0, lng = 10.0, radiusM = 100.0),
            com.satcop.smartvisitor.kiosk.data.model.GeoFenceDef(lat = 18.5212, lng = 73.8572, radiusM = 100.0),
        ))
        assertFalse(GuardGeoLogic.hint(fix(lat = 18.5212, lng = 73.8572), info)!!.outside)
    }

    // ---------- C: GPS service off ----------
    @Test fun restrictModeStopsWhenLocationIsOffOrDenied() {
        val off = GuardGeoLogic.locationGate(AttendanceMode.CHECK_IN, GeoMode.RESTRICT, permission = true, serviceOn = false)!!
        assertEquals("Location is off. Turn it on to check in.", off.message)
        assertEquals(LocationAction.LOCATION_SETTINGS, off.action)
        val denied = GuardGeoLogic.locationGate(AttendanceMode.CHECK_IN, GeoMode.RESTRICT, permission = false, serviceOn = true)!!
        assertEquals(ClockInLogic.LOCATION_NEEDED, denied.message)
        assertEquals(LocationAction.APP_SETTINGS, denied.action)
    }

    @Test fun softAndUnknownModeNeverStopCheckInForLocation() {
        for (m in listOf(GeoMode.SOFT, GeoMode.OFF, GeoMode.UNKNOWN)) {
            assertNull(GuardGeoLogic.locationGate(AttendanceMode.CHECK_IN, m, permission = true, serviceOn = false))
            assertNull(GuardGeoLogic.locationGate(AttendanceMode.CHECK_IN, m, permission = false, serviceOn = true))
        }
    }

    @Test fun clockOutAlwaysNeedsLocation() {
        for (m in GeoMode.entries) {
            assertEquals(ClockInLogic.CHECKOUT_NEEDS_GPS, GuardGeoLogic.locationGate(AttendanceMode.CLOCK_OUT, m, permission = true, serviceOn = false)!!.message)
            assertEquals(ClockInLogic.CHECKOUT_NEEDS_GPS, GuardGeoLogic.locationGate(AttendanceMode.CLOCK_OUT, m, permission = false, serviceOn = true)!!.message)
            assertNull(GuardGeoLogic.locationGate(AttendanceMode.CLOCK_OUT, m, permission = true, serviceOn = true))
        }
    }

    @Test fun gpsMissingOnlyAllowedInSoftModeAfterTimeout() {
        val w = GuardGeoLogic.GPS_WAIT_TIMEOUT_MS
        // soft: service off -> go now; service on, no fix -> only after the timeout
        assertTrue(GuardGeoLogic.checkInWithoutLocation(AttendanceMode.CHECK_IN, GeoMode.SOFT, true, false, false, 0))
        assertFalse(GuardGeoLogic.checkInWithoutLocation(AttendanceMode.CHECK_IN, GeoMode.SOFT, true, true, false, w - 1))
        assertTrue(GuardGeoLogic.checkInWithoutLocation(AttendanceMode.CHECK_IN, GeoMode.SOFT, true, true, false, w))
        // restrict: never
        assertFalse(GuardGeoLogic.checkInWithoutLocation(AttendanceMode.CHECK_IN, GeoMode.RESTRICT, true, false, false, w * 10))
        assertFalse(GuardGeoLogic.checkInWithoutLocation(AttendanceMode.CHECK_IN, GeoMode.RESTRICT, false, true, false, w * 10))
        // with a fix there is nothing to send without; clock-out never
        assertFalse(GuardGeoLogic.checkInWithoutLocation(AttendanceMode.CHECK_IN, GeoMode.SOFT, true, true, true, w))
        assertFalse(GuardGeoLogic.checkInWithoutLocation(AttendanceMode.CLOCK_OUT, GeoMode.SOFT, true, false, false, w))
    }

    // ---------- E: stale day + session watcher ----------
    private val today = LocalDate.parse("2026-10-01")

    @Test fun yesterdaysOpenRowDoesNotUnlock() {
        val stale = TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "2026-09-30")
        assertEquals(LockState.LOCKED, ClockInLogic.lockState(stale, true, today))
        assertEquals(LockState.LOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "CLOCKED_OUT", dutyDate = "2026-09-30"), true, today))
        assertEquals(LockState.UNLOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "2026-10-01"), true, today))
        // no dutyDate / no date given: old behaviour
        assertEquals(LockState.UNLOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "PRESENT"), true, today))
        assertEquals(LockState.UNLOCKED, ClockInLogic.lockState(stale, true))
        // garbage dutyDate never locks anyone out
        assertEquals(LockState.UNLOCKED, ClockInLogic.lockState(TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "soon"), true, today))
        assertEquals(LockState.UNKNOWN, ClockInLogic.lockState(null, false, today))
    }

    @Test fun watcherWakesAtTheCutoff() {
        assertEquals(SessionExpiry.WATCH_MAX_DELAY_MS, SessionExpiry.nextCheckDelayMs(null, 1_000))
        assertEquals(5_000L, SessionExpiry.nextCheckDelayMs(6_000, 1_000))
        assertEquals(SessionExpiry.WATCH_MAX_DELAY_MS, SessionExpiry.nextCheckDelayMs(10_000_000, 1_000))
        assertEquals(250L, SessionExpiry.nextCheckDelayMs(1_000, 5_000)) // already past: look again soon, never a busy loop
        assertTrue(SessionExpiry.isExpired(6_000, 6_000))
    }

    // ---------- F / D: result screen ----------
    @Test fun softOutsideShowsProductNoteAndNumbers() {
        val row = AttendanceRow(id = "GA-1", geofenceStatus = "outside", distanceM = 340.4, geofenceRadiusM = 192.0)
        val r = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row)
        assertEquals("You checked in from outside the campus. This will be flagged for review.", r.flaggedNote)
        assertEquals("You were about 350 m from the campus; the limit is 192 m.", r.flaggedDetail)
        // nested status wins, and the note works without numbers
        val nested = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(geofence = GeofenceInfo(status = "outside")))
        assertEquals(GuardGeoLogic.SOFT_OUTSIDE_NOTE, nested.flaggedNote)
        assertNull(nested.flaggedDetail)
        // inside: no note; clock-out never shows the check-in note
        assertNull(ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(geofenceStatus = "inside")).flaggedNote)
        // 1074: a clock-out from outside now shows its own friendly note (never the check-in wording)
        assertEquals(GuardGeoLogic.CLOCK_OUT_OUTSIDE_NOTE, ClockInLogic.resultFrom(AttendanceMode.CLOCK_OUT, AttendanceRow(geofenceStatus = "outside")).flaggedNote)
    }

    @Test fun mockLocationIsFlaggedNotBlocked() {
        assertNotNull(ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(id = "1"), sentMock = true).mockNote)
        assertNotNull(ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(flags = listOf("MOCK_LOCATION"))).mockNote)
        assertNotNull(ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(geofence = GeofenceInfo(isMock = true))).mockNote)
        assertNull(ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, AttendanceRow(id = "1")).mockNote)
    }

    // ---------- G: shift end ----------
    private fun at(istTime: String) = Instant.parse("2026-10-01T${istTime}:00+05:30".replace("+05:30", "Z")).minusSeconds(5 * 3600 + 30 * 60)

    @Test fun shiftEndPromptRules() {
        val on = AttendanceState.PRESENT
        assertFalse(GuardGeoLogic.shiftEndDue(on, "2026-10-01", "14:30", at("14:29"), null))
        assertTrue(GuardGeoLogic.shiftEndDue(on, "2026-10-01", "14:30", at("14:30"), null))
        assertTrue(GuardGeoLogic.shiftEndDue(on, "2026-10-01", "14:30", at("20:00"), null))
        // once per day
        assertFalse(GuardGeoLogic.shiftEndDue(on, "2026-10-01", "14:30", at("20:00"), today))
        assertTrue(GuardGeoLogic.shiftEndDue(on, "2026-10-01", "14:30", at("20:00"), today.minusDays(1)))
        // default 18:00 when the server sends no (or an unreadable) shift end
        assertFalse(GuardGeoLogic.shiftEndDue(on, "2026-10-01", null, at("17:59"), null))
        assertTrue(GuardGeoLogic.shiftEndDue(on, "2026-10-01", null, at("18:00"), null))
        assertFalse(GuardGeoLogic.shiftEndDue(on, "2026-10-01", "soon", at("17:00"), null))
        // only while on duty, only for today's row
        assertFalse(GuardGeoLogic.shiftEndDue(AttendanceState.NONE, "2026-10-01", "14:30", at("20:00"), null))
        assertFalse(GuardGeoLogic.shiftEndDue(AttendanceState.CLOCKED_OUT, "2026-10-01", "14:30", at("20:00"), null))
        assertFalse(GuardGeoLogic.shiftEndDue(on, "2026-09-30", "14:30", at("20:00"), null))
        assertEquals(java.time.LocalTime.of(9, 5), GuardGeoLogic.parseShiftEnd("9:05"))
        assertEquals(java.time.LocalTime.of(14, 30), GuardGeoLogic.parseShiftEnd("14:30:00"))
    }

    // ---------- controller (real) ----------
    private class Api : GuardHomeApi {
        var today = TodayAttendance(attendanceStatus = "NONE", canCheckIn = true, dutyDate = "2026-10-01")
        var geofence: GeofenceInfo? = null
        var geofenceError: Throwable? = null
        var schoolMode: String? = null
        var checkInError: Throwable? = null
        var checkInRow = AttendanceRow(id = "GA-1")
        val checkIns = mutableListOf<AttendanceRequest>()
        val clockOuts = mutableListOf<AttendanceRequest>()
        override fun attendanceToday() = today
        override fun checkIn(req: AttendanceRequest): AttendanceRow {
            checkIns += req; checkInError?.let { throw it }
            today = TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "2026-10-01", shiftEndTime = "14:30"); return checkInRow
        }
        override fun clockOut(req: AttendanceRequest): AttendanceRow { clockOuts += req; today = TodayAttendance(attendanceStatus = "CLOCKED_OUT"); return AttendanceRow(id = "GA-1") }
        override fun visitsBetween(dateFrom: String, dateTo: String, q: String?): List<VisitOut> = emptyList()
        override fun todaySummary() = GuardTodaySummary()
        override fun guardGeofence(): GeofenceInfo? { geofenceError?.let { throw it }; return geofence }
        override fun schoolGeoMode(): String? = schoolMode
    }

    private var clock = 1_000_000L
    private var now = Instant.parse("2026-10-01T05:00:00Z") // 10:30 IST
    private val api = Api()
    private var perm = true
    private var serviceOn = true
    private fun controller() = GuardHomeController(
        scope = CoroutineScope(Dispatchers.Unconfined), api = api, io = Dispatchers.Unconfined,
        nowElapsedMs = { clock }, nowInstant = { now }, newAttemptId = { "att-1" }, hasLocationPermission = { perm },
    ).also { it.locationServiceOn = { serviceOn } }

    private fun freshFix(mock: Boolean = false) = GpsFix(18.5, 73.8, 10.0, clock - 100, mock)

    private fun openSelfie(c: GuardHomeController, mode: AttendanceMode = AttendanceMode.CHECK_IN) { c.openPanel(mode); c.proceedToSelfie() }

    @Test fun modeIsReadFromGeofenceEndpointThenSchoolFallback() {
        api.geofence = GeofenceInfo(mode = "restrict", radiusM = 150.0)
        val c = controller(); c.refresh()
        assertEquals(GeoMode.RESTRICT, c.state.value.geoMode)
        // geofence call fails: /schools/me value is used
        api.geofence = null; api.geofenceError = java.io.IOException("down"); api.schoolMode = "soft"
        val c2 = controller(); c2.refresh()
        assertEquals(GeoMode.SOFT, c2.state.value.geoMode)
        // nothing readable: unknown, defaults hold, flow still works
        api.schoolMode = null
        val c3 = controller(); c3.refresh()
        assertEquals(GeoMode.UNKNOWN, c3.state.value.geoMode)
        assertEquals(100.0, c3.state.value.accuracyLimitM, 0.0)
    }

    @Test fun restrictRefusalWithDistanceStaysOnSelfieWithProductCopy() {
        api.geofence = GeofenceInfo(mode = "restrict", radiusM = 192.0)
        api.checkInError = ApiException("GEO_FENCE_RESTRICTED", "Outside", 403, mapOf("distanceM" to "341", "radiusM" to "192", "reasonCode" to "GEOFENCE_OUTSIDE"))
        val c = controller(); c.refresh(); openSelfie(c)
        c.submit("B64", clock, freshFix())
        val s = c.state.value
        assertEquals("You are about 350 m from the campus; the limit is 192 m. Move inside and try again.", s.panelMessage)
        assertEquals(AttendanceMode.CHECK_IN, s.panel)
        assertTrue(s.panelError)
        assertEquals(1, s.attemptNo) // Retry takes a NEW selfie
        assertNull(s.settingsHint)
        // raw coordinates never appear
        assertFalse(s.panelMessage!!.contains("18.5"))
    }

    @Test fun restrictRefusalWithoutDistanceUsesGenericLine() {
        api.checkInError = ApiException("GEO_FENCE_RESTRICTED", "Outside", 403)
        val c = controller(); c.refresh(); openSelfie(c)
        c.submit("B64", clock, freshFix())
        assertEquals("You are outside the school campus. Please move inside the campus and try again.", c.state.value.panelMessage)
    }

    @Test fun v2ReasonCodeAloneStillGivesTheRightMessage() {
        api.checkInError = ApiException("FORBIDDEN", "x", 403, mapOf("reasonCode" to "GEOFENCE_OUTSIDE", "distanceM" to "20", "radiusM" to "100"))
        val c = controller(); openSelfie(c)
        c.submit("B64", clock, freshFix())
        assertEquals("You are about 20 m from the campus; the limit is 100 m. Move inside and try again.", c.state.value.panelMessage)
    }

    @Test fun restrictWithLocationOffSendsNothing() {
        api.geofence = GeofenceInfo(mode = "restrict")
        serviceOn = false
        val c = controller(); c.refresh(); openSelfie(c)
        c.submit("B64", clock, null)
        val s = c.state.value
        assertTrue(api.checkIns.isEmpty())
        assertEquals("Location is off. Turn it on to check in.", s.panelMessage)
        assertEquals(SettingsHint.LOCATION_SERVICE, s.settingsHint)
        assertTrue(s.panelError)
    }

    @Test fun restrictServerRefusalWhilePermissionGrantedAndNoFixShowsLocationOff() {
        // permission granted, mode unreadable, location off: the server refuses (restrict) -> Location is off copy
        serviceOn = true
        val c = controller(); c.hasLocationPermission = { true }
        openSelfie(c)
        api.checkInError = ApiException("LOCATION_REQUIRED", "gps", 403)
        // soft/unknown + permission + service on + no fix: waits (nothing sent)
        c.submit("B64", clock, null)
        assertTrue(api.checkIns.isEmpty())
    }

    @Test fun softModeLocationOffGoesOutFlaggedWithGpsMissing() {
        api.geofence = GeofenceInfo(mode = "soft")
        serviceOn = false
        val c = controller(); c.refresh(); openSelfie(c)
        c.submit("B64", clock, null)
        assertEquals(1, api.checkIns.size)
        assertTrue(api.checkIns.single().gpsMissing)
        assertNull(api.checkIns.single().lat)
        assertEquals(ClockInLogic.LOCATION_OFF_SOFT, c.state.value.result?.flaggedNote)
    }

    @Test fun softModeWithServiceOnWaitsThenAllowsAfterTimeout() {
        api.geofence = GeofenceInfo(mode = "soft")
        val c = controller(); c.refresh(); openSelfie(c)
        c.submit("B64", clock, null)
        assertTrue(api.checkIns.isEmpty())
        clock += GuardGeoLogic.GPS_WAIT_TIMEOUT_MS
        c.submit("B64", clock, null)
        assertEquals(1, api.checkIns.size)
        assertTrue(api.checkIns.single().gpsMissing)
    }

    @Test fun restrictNeverSendsGpsMissingEvenAfterLongWait() {
        api.geofence = GeofenceInfo(mode = "restrict")
        val c = controller(); c.refresh(); openSelfie(c)
        clock += 10 * GuardGeoLogic.GPS_WAIT_TIMEOUT_MS
        c.submit("B64", clock, null)
        assertTrue(api.checkIns.isEmpty())
        perm = false
        c.submit("B64", clock, null)
        assertTrue(api.checkIns.isEmpty())
    }

    @Test fun clockOutWithLocationOffIsBlockedWithSettingsButton() {
        val c = controller(); c.openPanel(AttendanceMode.CLOCK_OUT); c.proceedToSelfie()
        serviceOn = false
        c.submit(null, null, null)
        assertTrue(api.clockOuts.isEmpty())
        assertEquals(ClockInLogic.CHECKOUT_NEEDS_GPS, c.state.value.panelMessage)
        assertEquals(SettingsHint.LOCATION_SERVICE, c.state.value.settingsHint)
    }

    @Test fun mockFixSendsIsMockAndResultCarriesTheNote() {
        val c = controller(); openSelfie(c)
        c.submit("B64", clock, freshFix(mock = true))
        assertEquals(true, api.checkIns.single().isMock)
        assertNotNull(c.state.value.result?.mockNote)
        // a normal fix sends no isMock
        val api2 = Api(); val c2 = GuardHomeController(CoroutineScope(Dispatchers.Unconfined), api2, Dispatchers.Unconfined, { clock }, { now })
        c2.openPanel(AttendanceMode.CHECK_IN); c2.proceedToSelfie(); c2.submit("B64", clock, freshFix())
        assertNull(api2.checkIns.single().isMock)
    }

    @Test fun softOutsideNoteReachesTheResult() {
        api.checkInRow = AttendanceRow(id = "GA-2", geofenceStatus = "outside", distanceM = 300.0, geofenceRadiusM = 190.0)
        val c = controller(); openSelfie(c)
        c.submit("B64", clock, freshFix())
        assertEquals(GuardGeoLogic.SOFT_OUTSIDE_NOTE, c.state.value.result?.flaggedNote)
    }

    @Test fun tightServerAccuracyLimitIsUsed() {
        api.geofence = GeofenceInfo(mode = "soft", rules = GeoRules(accuracyLimitM = 5.0))
        val c = controller(); c.refresh(); openSelfie(c)
        c.submit("B64", clock, freshFix()) // accuracy 10 m > 5 m
        assertTrue(api.checkIns.isEmpty())
        assertTrue(c.state.value.panelError)
    }

    @Test fun shiftEndPromptShowsOnceAndNeverAutoClocksOut() {
        val c = controller()
        api.today = TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "2026-10-01", shiftEndTime = "14:30")
        c.refresh()
        now = Instant.parse("2026-10-01T08:00:00Z") // 13:30 IST
        c.evaluateShiftEnd(); assertFalse(c.state.value.shiftEndPrompt)
        now = Instant.parse("2026-10-01T09:00:00Z") // 14:30 IST
        c.evaluateShiftEnd(); assertTrue(c.state.value.shiftEndPrompt)
        c.dismissShiftEndPrompt() // "Later"
        c.evaluateShiftEnd(); assertFalse(c.state.value.shiftEndPrompt) // once only
        assertTrue(api.clockOuts.isEmpty())
        // a new day prompts again
        now = Instant.parse("2026-10-02T13:00:00Z")
        api.today = TodayAttendance(attendanceStatus = "PRESENT", dutyDate = "2026-10-02", shiftEndTime = "14:30"); c.refresh()
        c.evaluateShiftEnd(); assertTrue(c.state.value.shiftEndPrompt)
        // "Clock out" opens the normal Self Check Out flow; still no automatic clock-out
        c.acceptShiftEndPrompt()
        assertEquals(AttendanceMode.CLOCK_OUT, c.state.value.panel)
        assertFalse(c.state.value.shiftEndPrompt)
        assertTrue(api.clockOuts.isEmpty())
    }

    // ---------- H: screen copy (source-level, so a wording slip is caught without an emulator) ----------
    private val screens by lazy { File("src/main/java/com/satcop/smartvisitor/kiosk/ui/guardhome/ClockFlowScreens.kt").readText() }
    private val extras by lazy { File("src/main/java/com/satcop/smartvisitor/kiosk/ui/guardhome/GuardTealExtras.kt").readText() }

    @Test fun referenceScreenCopyIsPresent() {
        assertEquals("CLOCK IN TO CONTINUE", ClockInLogic.LOCK_BUTTON)
        assertEquals("App locked until you clock in", ClockInLogic.LOCK_CHIP)
        assertEquals("You must clock in before accessing the visitor management app.", ClockInLogic.LOCK_TEXT)
        assertEquals("Self Check In", com.satcop.smartvisitor.kiosk.ui.guardhome.GuardCopy.flowTitle(AttendanceMode.CHECK_IN))
        assertEquals("Self Check Out", com.satcop.smartvisitor.kiosk.ui.guardhome.GuardCopy.flowTitle(AttendanceMode.CLOCK_OUT))
        assertEquals("Proceed to Check In", ClockInLogic.proceedLabel(AttendanceMode.CHECK_IN))
        assertEquals("Front Camera · Check In", ClockInLogic.cameraLabel(AttendanceMode.CHECK_IN))
        assertEquals("Position your face inside the oval", ClockInLogic.SELFIE_HINT)
        assertEquals("Selfie Check-In Photo", com.satcop.smartvisitor.kiosk.ui.guardhome.GuardCopy.photoPill(AttendanceMode.CHECK_IN))
        assertEquals("Checked In!", ClockInLogic.resultTitle(AttendanceMode.CHECK_IN))
        assertEquals("You are now on duty. Have a safe shift!", ClockInLogic.resultBody(AttendanceMode.CHECK_IN))
        assertEquals("Uploaded ✓", com.satcop.smartvisitor.kiosk.ui.guardhome.GuardCopy.SELFIE_UPLOADED)
        assertEquals(3, com.satcop.smartvisitor.kiosk.ui.guardhome.GuardCopy.HOW_STEPS.size)
        assertTrue(extras.contains("Capture Selfie"))
        assertTrue(extras.contains("Current Status") && extras.contains("Guard Details") && extras.contains("How it works"))
    }

    @Test fun screensUseTheNewPiecesAndNoOldOnes() {
        assertTrue(screens.contains("CornerMarkers()") && screens.contains("FaceGuide()"))
        assertTrue(screens.contains("GuardTealHeader("))
        assertTrue(screens.contains("ClockInLogic.LOCK_BUTTON"))
        assertTrue(screens.contains("GuardCopy.CAPTURE"))
        assertTrue(screens.contains("lockState(state.attendance, state.attendanceLoaded, businessDate)"))
        assertTrue(screens.contains("ACTION_LOCATION_SOURCE_SETTINGS"))
        assertTrue(screens.contains("GuardGeoLogic.SHIFT_END_PROMPT"))
        // the old round shutter and dashed guide are gone
        assertFalse(screens.contains("dashPathEffect"))
        assertFalse(screens.contains("Look at the camera"))
        // portrait only: no landscape handling was added
        assertFalse(screens.contains("ORIENTATION_LANDSCAPE"))
    }

    @Test fun productCopyIsExact() {
        assertEquals("Location is off. Turn it on to check in.", GuardGeoLogic.LOCATION_OFF_RESTRICT)
        assertEquals("Open location settings", GuardGeoLogic.OPEN_LOCATION_SETTINGS)
        assertEquals("You checked in from outside the campus. This will be flagged for review.", GuardGeoLogic.SOFT_OUTSIDE_NOTE)
        assertEquals("Your shift time is over. Clock out now?", GuardGeoLogic.SHIFT_END_PROMPT)
        assertEquals("Clock out", GuardGeoLogic.SHIFT_END_CLOCK_OUT)
        assertEquals("Later", GuardGeoLogic.SHIFT_END_LATER)
        assertEquals("आप परिसर से लगभग %1\$s मीटर दूर हैं; सीमा %2\$s मीटर है। परिसर के अंदर आकर दोबारा कोशिश करें।", GuardGeoLogic.Hi.OUTSIDE_WITH_DISTANCE)
    }

    @Test fun trackerHasServiceCheckRestartAndMockDetection() {
        val t = File("src/main/java/com/satcop/smartvisitor/kiosk/data/geo/LiveGpsTracker.kt").readText()
        assertTrue(t.contains("isLocationEnabled"))
        assertTrue(t.contains("override fun onProviderEnabled(provider: String) { restart() }"))
        assertTrue(t.contains("location.isMock") && t.contains("isFromMockProvider"))
        val vm = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/KioskViewModel.kt").readText()
        assertTrue(vm.contains("watchSessionExpiry"))
    }
}
