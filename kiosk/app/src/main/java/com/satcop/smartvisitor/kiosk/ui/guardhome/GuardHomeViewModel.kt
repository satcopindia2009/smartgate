package com.satcop.smartvisitor.kiosk.ui.guardhome

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRequest
import com.satcop.smartvisitor.kiosk.data.model.AttendanceRow
import com.satcop.smartvisitor.kiosk.data.model.GuardTodaySummary
import com.satcop.smartvisitor.kiosk.data.model.TodayAttendance
import com.satcop.smartvisitor.kiosk.data.model.VisitOut

/** Live adapter: LiveVisitorApi already refuses data calls before face verification. */
class LiveGuardHomeApi(private val live: LiveVisitorApi = LiveVisitorApi()) : GuardHomeApi {
    override fun attendanceToday(): TodayAttendance = live.attendanceToday()
    override fun todaySummary(): GuardTodaySummary = live.guardTodaySummary()
    override fun guardGeofence(): com.satcop.smartvisitor.kiosk.data.model.GeofenceInfo? = live.guardGeofence()
    override fun schoolGeoMode(): String? = live.schoolMe().geoFenceMode
    override fun guardSessionCutoff(): String? = live.schoolMe().guardSessionCutoff
    override fun checkIn(req: AttendanceRequest): AttendanceRow = live.attendanceCheckIn(req)
    override fun clockOut(req: AttendanceRequest): AttendanceRow = live.attendanceClockOut(req)
    override fun visitsBetween(dateFrom: String, dateTo: String, q: String?): List<VisitOut> =
        live.listVisitsBetween(dateFrom, dateTo, q).data
}

/** One per signed-in guard session (keyed by sessionEpoch in KioskApp) — never shared across users. */
class GuardHomeViewModel : ViewModel() {
    val controller = GuardHomeController(
        scope = viewModelScope,
        api = LiveGuardHomeApi(),
        nowElapsedMs = { SystemClock.elapsedRealtime() },
    )

    // The Today tab refreshes on every ON_RESUME (including first composition), so no init fetch.
}
