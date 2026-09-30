package com.satcop.smartvisitor.kiosk.ui

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Teal5Test {
    private val root = "src/main/java/com/satcop/smartvisitor/kiosk/"
    private fun src(p: String) = File(root + p).readText()

    @Test fun verifyFaceButtonIsFullWidthAndSingleLine() {
        val s = src("ui/face/FaceCaptureScreen.kt")
        assertTrue(s.contains("Modifier.fillMaxWidth().heightIn(min = 52.dp)"))
        assertFalse("pill must not share a weighted row", s.contains("Modifier.weight(1f).heightIn(min = 52.dp)"))
        assertEquals(2, Regex("maxLines = 1, softWrap = false").findAll(s).count())
    }

    @Test fun clockIsNotInTheBigUiState() {
        val vm = src("ui/KioskViewModel.kt")
        assertFalse(vm.contains("val clockLabel"))
        assertFalse(vm.contains("copy(clockLabel"))
        assertTrue(vm.contains("val clock: StateFlow<String>"))
        // only ClockText collects it
        assertTrue(src("ui/KioskApp.kt").contains("fun ClockText("))
        assertFalse(src("ui/KioskApp.kt").contains("state.clockLabel"))
    }

    @Test fun heavyDependenciesAreLazy() {
        assertTrue(src("data/api/LiveVisitorApi.kt").contains("private val client by lazy"))
        assertTrue(src("guardpatrol/data/LiveGuardPatrolApi.kt").contains("private val client by lazy"))
        val f = src("data/face/LocalFaceTemplateStore.kt")
        assertTrue(f.contains("private val prefs by lazy"))
        assertTrue(f.contains("private val dir by lazy"))
        // face hub reads the template store off the main thread
        val vm = src("ui/KioskViewModel.kt")
        val open = vm.substring(vm.indexOf("fun openFaceLogin"), vm.indexOf("fun closeFaceLogin"))
        assertTrue(open.contains("withContext(Dispatchers.IO)"))
    }

    @Test fun loginStringsEnAndHi() {
        assertEquals("Login", LoginStrings.EN.title)
        assertEquals("लॉगिन", LoginStrings.HI.title)
        assertEquals("पासवर्ड भूल गए?", LoginStrings.of(true).forgot)
        assertEquals("Forgot password?", LoginStrings.of(false).forgot)
        // every field differs between languages
        val en = LoginStrings.EN; val hi = LoginStrings.HI
        listOf(en.title to hi.title, en.subtitle to hi.subtitle, en.userIdLabel to hi.userIdLabel, en.userIdHint to hi.userIdHint,
            en.passwordLabel to hi.passwordLabel, en.passwordHint to hi.passwordHint, en.forgot to hi.forgot, en.login to hi.login,
            en.signingIn to hi.signingIn, en.faceLogin to hi.faceLogin).forEach { assertNotEquals(it.first, it.second) }
    }
}
