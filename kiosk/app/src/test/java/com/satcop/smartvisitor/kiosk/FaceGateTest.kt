package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.api.AuthSession
import com.satcop.smartvisitor.kiosk.data.model.MeResponse
import com.satcop.smartvisitor.kiosk.ui.FaceGateMachine
import com.satcop.smartvisitor.kiosk.ui.GateStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceGateTest {
    private val me = MeResponse(id = "U-GUARD", schoolId = "SCH", role = "guard", displayName = "Guard")

    @Test
    fun coldStartIsSignedOutAndShowsNoData() {
        val s = FaceGateMachine.onColdStart()
        assertEquals(GateStage.SIGNED_OUT, s)
        assertFalse(FaceGateMachine.canShowData(s))
    }

    @Test
    fun passwordLoginAloneCannotReachHome() {
        val s = FaceGateMachine.onPasswordLogin(FaceGateMachine.initial)
        assertEquals(GateStage.FACE_PENDING, s)
        assertFalse(FaceGateMachine.canShowData(s))
    }

    @Test
    fun failedOrUnverifiedFaceResultKeepsGateClosed() {
        val pending = FaceGateMachine.onPasswordLogin(GateStage.SIGNED_OUT)
        assertEquals(GateStage.FACE_PENDING, FaceGateMachine.onFaceResult(pending, serverVerified = false))
        assertFalse(FaceGateMachine.canShowData(FaceGateMachine.onFaceResult(pending, false)))
        assertEquals(GateStage.SIGNED_OUT, FaceGateMachine.onFaceResult(GateStage.SIGNED_OUT, false))
    }

    @Test
    fun onlyServerVerifiedFaceOpensHome() {
        val pending = FaceGateMachine.onPasswordLogin(GateStage.SIGNED_OUT)
        val ok = FaceGateMachine.onFaceResult(pending, serverVerified = true)
        assertEquals(GateStage.VERIFIED, ok)
        assertTrue(FaceGateMachine.canShowData(ok))
    }

    @Test
    fun backFromFaceScreenDropsSessionAndDoesNotReveal() {
        val pending = FaceGateMachine.onPasswordLogin(GateStage.SIGNED_OUT)
        val afterBack = FaceGateMachine.onBack(pending)
        assertEquals(GateStage.SIGNED_OUT, afterBack)
        assertFalse(FaceGateMachine.canShowData(afterBack))
        // repeated back never escalates
        assertEquals(GateStage.SIGNED_OUT, FaceGateMachine.onBack(afterBack))
    }

    @Test
    fun logoutAndServerFaceRequiredReclose() {
        val v = GateStage.VERIFIED
        assertEquals(GateStage.SIGNED_OUT, FaceGateMachine.onLogout(v))
        val forced = FaceGateMachine.onServerFaceRequired(v)
        assertEquals(GateStage.FACE_PENDING, forced)
        assertFalse(FaceGateMachine.canShowData(forced))
        assertEquals(GateStage.SIGNED_OUT, FaceGateMachine.onServerFaceRequired(GateStage.SIGNED_OUT))
    }

    @Test
    fun sessionPasswordTokenIsNeverDataAccess() {
        val s = AuthSession()
        s.accept("pw-token", me) // default faceVerified=false
        assertTrue(s.isSignedIn)
        assertFalse(s.dataAccessAllowed)
        s.accept("face-token", me, faceVerified = true)
        assertTrue(s.dataAccessAllowed)
    }

    @Test
    fun serverFaceRequiredDowngradesSessionAndLogoutClears() {
        val s = AuthSession()
        s.accept("face-token", me, faceVerified = true)
        s.markFaceRequired()
        assertFalse(s.dataAccessAllowed)
        s.accept("face-token", me, faceVerified = true)
        s.clear()
        assertNull(s.accessToken)
        assertFalse(s.faceVerified)
        assertFalse(s.dataAccessAllowed)
    }
}
