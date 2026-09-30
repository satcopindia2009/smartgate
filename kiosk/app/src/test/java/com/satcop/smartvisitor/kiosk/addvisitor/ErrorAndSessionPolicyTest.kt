package com.satcop.smartvisitor.kiosk.addvisitor

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorAndSessionPolicyTest {
    private fun e(code: String, http: Int = 400) = ApiException(code, "raw $code", http)

    @Test fun gpsRequiredHasPlainCopy() {
        assertEquals(
            "Your location is required to clock out. Please turn on GPS and try again.",
            ErrorCopy.forApi(e("GPS_REQUIRED")),
        )
        assertEquals(ErrorCopy.GPS_REQUIRED, ErrorCopy.forApi(e("gps_required")))
        assertFalse(ErrorCopy.isTechnical(ErrorCopy.GPS_REQUIRED))
    }

    @Test fun addVisitorCodesHavePlainCopy() {
        assertEquals("This visitor is already inside.", ErrorCopy.forApi(e("ALREADY_INSIDE", 409)))
        assertEquals("Entry not allowed. Call the security head.", ErrorCopy.forApi(e("BLACKLISTED", 403)))
        assertEquals("Enter a valid 10-digit mobile number.", ErrorCopy.forApi(e("INVALID_MOBILE")))
        assertEquals("A vendor visit cannot have a scheduled time.", ErrorCopy.forApi(e("VENDOR_NO_SCHEDULE")))
        assertTrue(ErrorCopy.forApi(e("PROFILE_TYPE_CONFLICT", 409)).contains("vendor"))
    }

    // ---- D13 (AC-QC-D13): no session/token is persisted for ANY role, including Host ----
    private fun main(rel: String) = File("src/main/java/com/satcop/smartvisitor/kiosk/$rel").readText()

    @Test fun authSessionIsMemoryOnly() {
        val t = main("data/api/AuthSession.kt")
        assertFalse(t.contains("SharedPreferences"))
        assertFalse(t.contains("Context"))
        assertFalse(t.contains("DataStore"))
        assertTrue(t.contains("NEVER persisted"))
    }

    @Test fun noSourceStoresATokenInPrefsOrFiles() {
        val root = File("src/main/java/com/satcop/smartvisitor/kiosk")
        val offenders = root.walkTopDown().filter { it.extension == "kt" }.filter { f ->
            val t = f.readText()
            val stores = t.contains("SharedPreferences") || t.contains("SharedPrefsStringStore") ||
                t.contains("EncryptedSharedPreferences") || t.contains("DataStore")
            stores && Regex("(?i)(accessToken|access_token|bearer|jwt)").containsMatchIn(
                t.lines().filter { it.contains(".put") || it.contains("putString") || it.contains("edit()") }.joinToString("\n"),
            )
        }.map { it.name }.toList()
        assertTrue("token written to storage in: $offenders", offenders.isEmpty())
    }

    @Test fun hostFeedStoreHoldsOnlyACursorNotAToken() {
        val vm = main("ui/KioskViewModel.kt")
        assertTrue(vm.contains("SharedPrefsStringStore(context, \"satcop_host_feed\")"))
        val tracker = main("ui/notify/HostFeedTracker.kt")
        assertTrue(tracker.contains("store.put(key(hostKey), newCursor)"))
        assertFalse(tracker.contains("accessToken"))
    }

    @Test fun coldStartBeginsSignedOut() {
        val vm = main("ui/KioskViewModel.kt")
        assertTrue(vm.contains("val signedIn: Boolean = false"))
        assertTrue(vm.contains("GateStage.SIGNED_OUT"))
        assertFalse(main("MainActivity.kt").contains("accessToken"))
    }

    @Test fun manifestDisablesBackup() {
        assertTrue(File("src/main/AndroidManifest.xml").readText().contains("android:allowBackup=\"false\""))
    }
}
