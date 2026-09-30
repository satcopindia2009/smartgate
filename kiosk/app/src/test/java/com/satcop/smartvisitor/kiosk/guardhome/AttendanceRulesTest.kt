package com.satcop.smartvisitor.kiosk.guardhome

import com.satcop.smartvisitor.kiosk.data.geo.GpsFix
import com.satcop.smartvisitor.kiosk.data.geo.GpsPolicy
import com.satcop.smartvisitor.kiosk.data.geo.GpsState
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceRules
import com.satcop.smartvisitor.kiosk.ui.guardhome.Readiness
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceRulesTest {
    private val now = 1_000_000L
    private fun fix(ageMs: Long = 1000, acc: Double = 12.0) = GpsFix(18.52, 73.85, acc, now - ageMs)
    private fun ready(
        mode: AttendanceMode = AttendanceMode.CHECK_IN,
        photo: Boolean = true,
        photoAt: Long? = now - 5000,
        fix: GpsFix? = fix(),
        failed: Long? = null,
        perm: Boolean = true,
    ) = AttendanceRules.readiness(mode, photo, photoAt, fix, failed, now, perm)

    @Test
    fun goodReadingAndPhotoIsReady() {
        assertTrue(ready() is Readiness.Ready)
    }

    @Test
    fun checkInNeedsPhotoButClockOutDoesNot() {
        assertEquals(AttendanceRules.NEED_PHOTO, (ready(photo = false, photoAt = null) as Readiness.Blocked).message)
        assertTrue(ready(mode = AttendanceMode.CLOCK_OUT, photo = false, photoAt = null) is Readiness.Ready)
    }

    @Test
    fun oldPhotoMustBeRetaken() {
        val r = ready(photoAt = now - 95_000) as Readiness.Blocked
        assertTrue(r.needsRetakePhoto)
    }

    @Test
    fun missingPermissionIsPlain() {
        assertEquals(AttendanceRules.NO_PERMISSION, (ready(perm = false) as Readiness.Blocked).message)
    }

    @Test
    fun noFixYetBlocksAndNeverInventsCoordinates() {
        assertTrue(ready(fix = null) is Readiness.Blocked)
    }

    @Test
    fun staleFixIsNotSent() {
        assertEquals(AttendanceRules.WAIT_FOR_FRESH, (ready(fix = fix(ageMs = 30_000)) as Readiness.Blocked).message)
    }

    @Test
    fun weakAccuracyBlockedWithServerCopy() {
        assertEquals(
            "Unable to get an accurate location. Please enable GPS and try again.",
            (ready(fix = fix(acc = 250.0)) as Readiness.Blocked).message,
        )
    }

    @Test
    fun failedReadingIsNeverReusedButNewerOneIs() {
        val old = fix(ageMs = 5000)
        assertTrue(ready(fix = old, failed = old.elapsedMs) is Readiness.Blocked)
        val newer = fix(ageMs = 1000)
        assertTrue(ready(fix = newer, failed = old.elapsedMs) is Readiness.Ready)
    }

    @Test
    fun requestCarriesPhotoLocationAccuracyDeviceTimeAndAttempt() {
        val json = Json { encodeDefaults = true }
        val req = AttendanceRules.buildRequest(fix(), "B64", Instant.parse("2026-09-30T11:00:00Z"), "att-1")
        val body = json.encodeToString(req)
        assertTrue(body.contains("\"imageBase64\":\"B64\""))
        assertTrue(body.contains("\"lat\":18.52"))
        assertTrue(body.contains("\"lng\":73.85"))
        assertTrue(body.contains("\"accuracyM\":12.0"))
        assertTrue(body.contains("\"capturedAt\":\"2026-09-30T11:00:00Z\""))
        assertTrue(body.contains("\"attemptId\":\"att-1\""))
        assertTrue(body.contains("\"gpsMissing\":false"))
        // client never sends a status / inside-fence flag / check times
        assertFalse(body.contains("insideGeofence"))
        assertFalse(body.contains("status"))
        assertFalse(body.contains("checkInAt"))
    }

    @Test
    fun clockOutRequestWithoutPhotoOmitsImage() {
        val body = Json { encodeDefaults = true }.encodeToString(
            AttendanceRules.buildRequest(fix(), null, Instant.parse("2026-09-30T11:00:00Z"), "att-2"),
        )
        assertFalse(body.contains("imageBase64"))
    }

    @Test
    fun errorCodeClassification() {
        listOf("GEO_FENCE_RESTRICTED", "GPS_ACCURACY_LOW", "STALE_CAPTURE").forEach {
            assertTrue(it, AttendanceRules.needsFreshReading(it))
        }
        assertFalse(AttendanceRules.needsFreshReading("ALREADY_CLOCKED_OUT"))
        listOf("ALREADY_CLOCKED_OUT", "ALREADY_CHECKED_IN", "NO_ACTIVE_ATTENDANCE").forEach {
            assertTrue(it, AttendanceRules.stateAlreadyMoved(it))
        }
    }

    // --- GpsPolicy ---
    @Test
    fun gpsStates() {
        assertEquals(GpsState.Searching, GpsPolicy.evaluate(null, now))
        assertTrue(GpsPolicy.evaluate(fix(), now) is GpsState.Ready)
        assertTrue(GpsPolicy.evaluate(fix(acc = 100.1), now) is GpsState.Weak)
        assertTrue(GpsPolicy.evaluate(fix(acc = 100.0), now) is GpsState.Ready)
        assertTrue(GpsPolicy.evaluate(fix(ageMs = 21_000), now) is GpsState.Stale)
    }

    @Test
    fun preferKeepsNewerAndDropsOlder() {
        val a = fix(ageMs = 8000, acc = 5.0)
        val b = fix(ageMs = 1000, acc = 30.0)
        // newer but less accurate within 10 s keeps the accurate one; beyond 10 s the newer wins
        assertEquals(a, GpsPolicy.prefer(a, b))
        val c = fix(ageMs = 0, acc = 30.0).copy(elapsedMs = a.elapsedMs + 11_000)
        assertEquals(c, GpsPolicy.prefer(a, c))
        assertNull(null as GpsFix?)
        assertEquals(a, GpsPolicy.prefer(null, a))
        val older = a.copy(elapsedMs = a.elapsedMs - 1)
        assertEquals(a, GpsPolicy.prefer(a, older))
    }

    @Test
    fun statusLineIsPlain() {
        val line = GpsPolicy.statusLine(GpsPolicy.evaluate(fix(acc = 12.0), now), now)
        assertEquals("Location ready (accurate to about 12 m)", line)
        assertFalse(GpsPolicy.statusLine(GpsState.Searching, now).contains("null"))
    }
}
