package com.satcop.smartvisitor.kiosk.teal11

import com.satcop.smartvisitor.kiosk.ui.FaceLockOrder
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Teal11Test {
    private fun src(p: String) = File("src/main/java/com/satcop/smartvisitor/kiosk/$p").readText()

    @Test fun lockShowsFirstForHalfOpenSession() {
        assertTrue(FaceLockOrder.showLock(sessionSignedIn = true, faceVerified = false, cameraDown = true))
    }

    @Test fun cameraUpHidesLock() {
        assertFalse(FaceLockOrder.showLock(true, false, false))
    }

    @Test fun verifiedOrSignedOutNeverShowsLock() {
        assertFalse(FaceLockOrder.showLock(true, true, true))
        assertFalse(FaceLockOrder.showLock(false, false, true))
    }

    @Test fun loginGoesToLockNotCamera() {
        val vm = src("ui/KioskViewModel.kt")
        val login = vm.substringAfter("fun login()").substringBefore("fun logout()")
        assertTrue(login.contains("facePhase = FaceLoginPhase.HUB"))
        assertFalse(login.contains("facePhase = FaceLoginPhase.CAPTURE_VERIFY"))
        assertFalse(login.contains("Password accepted"))
        // no data call before face-verify: login branch for guards never calls loadAfterLogin before the needsFace return
        assertTrue(login.indexOf("loadAfterLogin(me)") < login.indexOf("Face gate: password alone"))
    }

    @Test fun lockButtonOpensCameraAndBackReturnsToLock() {
        val app = src("ui/KioskApp.kt")
        assertTrue(app.contains("onClockIn = viewModel::startFaceVerify"))
        val vm = src("ui/KioskViewModel.kt")
        val back = vm.substringAfter("fun onSystemBack()").substringBefore("if (!s.signedIn")
        assertTrue(back.contains("cancelFaceCapture()"))
        assertTrue(back.contains("return true"))
        assertTrue(vm.substringAfter("fun cancelFaceCapture()").substringBefore("}").contains("FaceLoginPhase.HUB"))
    }

    @Test fun successHandsRowStraightToCheckedInCardWithoutLockFlash() {
        val vm = src("ui/KioskViewModel.kt")
        val ok = vm.substringAfter("1075: stay on the camera").substringBefore("return@launch")
        assertTrue(ok.contains("clockInRow = ci.getOrNull()"))
        assertTrue(ok.contains("loadAfterLogin(live.user)"))
        val g = src("ui/guardhome/ClockFlowScreens.kt")
        assertTrue(g.contains("initialCheckInRow != null && result == null"))
    }
}
