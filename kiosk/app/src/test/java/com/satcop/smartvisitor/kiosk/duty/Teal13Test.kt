package com.satcop.smartvisitor.kiosk.duty

import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.ui.KioskRole
import com.satcop.smartvisitor.kiosk.ui.guardhome.AttendanceMode
import com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic
import com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic
import com.satcop.smartvisitor.kiosk.data.geo.CaptureStamp
import java.io.File
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Teal13Test {
    private fun src(p: String) = File("src/main/java/com/satcop/smartvisitor/kiosk/$p").readText()

    @Test fun everyDutyGoesThroughTheClockInGateAndRoleCardIsGone() {
        val app = src("ui/KioskApp.kt")
        assertTrue(app.contains("role == KioskRole.GUARD || role == KioskRole.GATE"))
        assertFalse(app.contains("val roleCard"))
        assertTrue(app.contains("GuardClockInGate("))
        assertTrue(app.contains("DutyHome(state, viewModel, guardHome"))
    }

    @Test fun switcherOnlyForBothAndGateFirst() {
        val app = src("ui/KioskApp.kt")
        assertTrue(app.contains("if (areas.size > 1) com.satcop.smartvisitor.kiosk.ui.duty.DutyAreaSwitcher"))
        assertTrue(src("ui/duty/DutyUi.kt").contains("DutyLogic.AREA_GATE to") || src("ui/duty/DutyUi.kt").contains("DutyArea.GATE to DutyLogic.AREA_GATE"))
        assertEquals(com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.GATE,
            com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.areas("guard", com.satcop.smartvisitor.kiosk.ui.duty.DutyInfo(listOf("patrol", "gate"))).defaultArea)
    }

    @Test fun noDutyHomeHasTheSharedToolsAndText() {
        val ui = src("ui/duty/DutyUi.kt")
        for (t in listOf("Report incident", "Lost & Found", "Courier log", "Find visitor", "\"Clock out\"", "\"Sign out\"", "DutyLogic.NO_DUTY_TEXT"))
            assertTrue(t, ui.contains(t))
    }

    @Test fun dutyBannerAndPollingWired() {
        val vm = src("ui/KioskViewModel.kt")
        assertTrue(vm.contains("delay(60_000L)"))
        assertTrue(vm.contains("fun applyDutyUpdate()"))
        assertTrue(vm.contains("fun refreshDutyNow()"))
        assertTrue(vm.contains("DutyLogic.revisionChanged"))
        assertTrue(src("ui/KioskApp.kt").contains("DutyUpdatedBanner(viewModel::applyDutyUpdate)"))
        assertTrue(src("ui/KioskApp.kt").contains("onDutyRefresh = viewModel::refreshDutyNow"))
    }

    @Test fun dutyErrorsAreRouted() {
        val api = src("data/api/LiveVisitorApi.kt")
        assertTrue(api.contains("NO_GATE_DUTY") && api.contains("NOT_CLOCKED_IN") && api.contains("GATE_NOT_ON_DUTY"))
        assertTrue(src("data/api/LiveVisitorApi.kt").contains("header(\"X-Duty-Aware\", \"1\")"))
        assertTrue(src("guardpatrol/data/LiveGuardPatrolApi.kt").contains("header(\"X-Duty-Aware\", \"1\")"))
        assertTrue(src("ui/KioskViewModel.kt").contains("attendanceRecheck"))
        assertTrue(src("ui/guardhome/ClockFlowScreens.kt").contains("LaunchedEffect(attendanceRecheck)"))
    }

    @Test fun noCallsBeforeFaceVerifyStillHolds() {
        val vm = src("ui/KioskViewModel.kt")
        val login = vm.substringAfter("fun login()").substringBefore("fun logout()")
        assertTrue(login.indexOf("loadAfterLogin(me)") < login.indexOf("Face gate: password alone"))
        assertFalse(login.contains("dutyMe"))
        assertFalse(login.contains("loadDutyHome"))
        assertTrue(vm.contains("if (!AppAuth.session.dataAccessAllowed)"))
    }

    @Test fun lockBackAndFaceFlowKept() {
        val vm = src("ui/KioskViewModel.kt")
        val back = vm.substringAfter("fun onSystemBack()").substringBefore("if (!s.signedIn")
        assertTrue(back.contains("cancelFaceCapture()") && back.contains("return true"))
        assertTrue(src("ui/guardhome/ClockFlowScreens.kt").contains("BackHandler(enabled = panel == null && result == null && lock != LockState.UNLOCKED) { }"))
        assertTrue(src("ui/guardhome/FaceClockInLogic.kt").contains("fun requiresClockIn"))
        assertTrue(src("data/api/LiveVisitorApi.kt").contains("FaceClockInLogic.requiresClockIn(parsed.user?.role)"))
        assertTrue(vm.contains("FaceClockInLogic.requiresClockIn(live.user.role)"))
    }

    @Test fun adminAndSecurityHeadStayWebOnly() {
        assertTrue(KioskRole.isWebOnly("admin")); assertTrue(KioskRole.isWebOnly("security_head"))
        assertFalse(com.satcop.smartvisitor.kiosk.ui.guardhome.FaceClockInLogic.requiresClockIn("host"))
    }

    @Test fun gateIdSentOnlyWhenChosen() {
        val stamp = CaptureStamp(capturedAt = "2026-10-01T10:00:00Z", lat = 18.5, lng = 73.8, accuracyM = 10f, gpsMissing = false)
        val a = FaceClockInLogic.buildCheckIn("b64", stamp, Instant.parse("2026-10-01T10:00:00Z"), "att-1")
        assertNull(a.gateId)
        assertEquals("G-PED", FaceClockInLogic.buildCheckIn("b64", stamp, Instant.parse("2026-10-01T10:00:00Z"), "att-1", "G-PED").gateId)
        assertNull(FaceClockInLogic.buildCheckIn("b64", stamp, Instant.parse("2026-10-01T10:00:00Z"), "att-1", " ").gateId)
    }

    @Test fun gateNameComesFromDutyGateNameNeverTheId() {
        val row = AttendanceRow(gateId = "G-MAIN", gateName = "G-MAIN", dutyGateName = "Main Gate")
        assertEquals("Main Gate", ClockInLogic.gateLabel(row))
        val r = ClockInLogic.resultFrom(AttendanceMode.CHECK_IN, row)
        assertEquals("Main Gate", r.gate)
        assertEquals("—", ClockInLogic.gateLabel(AttendanceRow(gateId = "G-MAIN")))
    }

    @Test fun verifyOnlyCopy() {
        val r = FaceClockInLogic.verifiedResult("2026-10-01T17:09:41+05:30", null)
        assertEquals("Face verified", ClockInLogic.resultTitleOf(r))
        assertEquals("Face verified", ClockInLogic.resultHeader(r))
        assertTrue(src("ui/guardhome/ClockFlowScreens.kt").contains("\"Your face is verified. Welcome!\""))
        assertFalse(src("ui/guardhome/ClockInLogic.kt").contains("\"Verified!\""))
    }

    @Test fun roleMappingStillMapsToTheOneShell() {
        assertEquals(KioskRole.GATE, KioskRole.fromJwt("gate"))
        assertEquals(KioskRole.GATE, KioskRole.fromJwt("gate-staff"))
        assertEquals(KioskRole.GUARD, KioskRole.fromJwt("guard"))
        assertEquals(KioskRole.GUARD, KioskRole.fromJwt("security"))
        assertTrue(src("ui/KioskViewModel.kt").contains("KioskRole.GATE, KioskRole.GUARD -> loadDutyHome(me)"))
    }

    @Test fun gateEndpoint403DoesNotBreakTheFlow() {
        val vm = src("ui/KioskViewModel.kt")
        val gate = vm.substringAfter("private suspend fun loadGateHome").substringBefore("private fun applyIdentityHome")
        assertTrue(gate.contains("catch (_: Exception)") && gate.contains("applyIdentityHome(me, warning = true)"))
    }
}
