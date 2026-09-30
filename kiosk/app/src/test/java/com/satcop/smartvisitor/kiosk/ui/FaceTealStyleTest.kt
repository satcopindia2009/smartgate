package com.satcop.smartvisitor.kiosk.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Board 04 restyle is visual only: behaviour hooks and copy must stay. */
class FaceTealStyleTest {
    private val root = "src/main/java/com/satcop/smartvisitor/kiosk/ui/face/"
    private val cap get() = File(root + "FaceCaptureScreen.kt").readText()

    @Test fun boardCopyAndSecurityNote() {
        assertTrue(cap.contains("Verify your face"))
        assertTrue(cap.contains("Face verification required. Please verify your face to continue."))
        assertTrue(cap.contains("Your photo is used only to confirm it is you and is stored securely for attendance."))
        assertTrue(cap.contains("FaceCaptureCopy.LOCATION_HINT_EN"))
        assertTrue(cap.contains("FaceCaptureCopy.LOCATION_HINT_HI"))
    }

    @Test fun behaviourHooksUnchanged() {
        assertTrue(cap.contains("onCaptured(FaceImage.prepareJpeg(previewBmp!!))"))
        assertTrue(cap.contains("QaHooks.frame(\"Face\")"))
        assertTrue(cap.contains("\"Verify face\""))
        assertTrue(cap.contains("\"Use for enroll\""))
        assertTrue(cap.contains("onCancel"))
    }

    @Test fun darkPaletteNotLightCard() {
        assertTrue(cap.contains("AppleDark"))
        assertFalse(cap.contains("KioskColors."))
    }

    @Test fun networkStillOffMainThread() {
        val vm = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/KioskViewModel.kt").readText()
        assertTrue(vm.contains("io { liveApi.faceEnroll("))
        assertTrue(vm.contains("liveApi.faceVerify("))
    }
}
