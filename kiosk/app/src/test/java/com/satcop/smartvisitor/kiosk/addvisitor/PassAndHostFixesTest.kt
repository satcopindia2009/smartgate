package com.satcop.smartvisitor.kiosk.addvisitor

import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.model.AfterHoursCopy
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import com.satcop.smartvisitor.kiosk.data.model.HostApproveCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PassAndHostFixesTest {
    // The backend issues passId/qrToken only at approve, so right after create a pending visit has neither.
    @Test fun qrPayloadFallsBackToVisitIdRightAfterCreate() {
        assertEquals("V-20261001-221", AddVisitorLogic.qrPayload(null, null, "V-20261001-221"))
        assertEquals("V-1", AddVisitorLogic.qrPayload("", "  ", "V-1"))
    }

    @Test fun qrPayloadPrefersTokenThenPassThenVisit() {
        assertEquals("tok", AddVisitorLogic.qrPayload("tok", "P-4F21", "V-1"))
        assertEquals("P-4F21", AddVisitorLogic.qrPayload(null, "P-4F21", "V-1"))
        assertNull(AddVisitorLogic.qrPayload(null, null, null))
    }

    @Test fun passScreenNeverGatesQrOnStatusOrBitmap() {
        val src = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/steps/OutcomeStep.kt").readText()
        assertTrue(src.contains("AddVisitorLogic.qrPayload("))
        assertFalse(src.contains("status in setOf(\"approved\""))
        // caption is outside the bitmap-null check
        val cap = src.indexOf("Visitor pass QR · display only")
        val nullCheck = src.indexOf("if (bmp != null)")
        assertTrue(cap > src.indexOf("}", nullCheck))
    }

    @Test fun afterHoursApproveShowsServerReasonSentence() {
        val e = ApiException(AfterHoursCopy.CODE, "After hours / holiday — Security Head approval required", 403)
        assertEquals(AfterHoursCopy.HOST_NO_OP, HostApproveCopy.forError(e))
    }

    @Test fun otherApproveErrorsAreVisibleAndPlain() {
        val m = HostApproveCopy.forError(ApiException("INVALID_STATE", "Visit is not pending", 409))
        assertNotNull(m); assertTrue(m.isNotBlank()); assertFalse(m.contains("INVALID_STATE"))
        assertTrue(HostApproveCopy.forError(java.io.IOException("x")).isNotBlank())
    }
}
