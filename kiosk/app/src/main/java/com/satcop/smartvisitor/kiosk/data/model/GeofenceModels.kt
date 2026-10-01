package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/*
 * Geofence blocks (Backend 2026-10-01, LIVE): GET /guards/me/geofence, the `geofence` block of GET /attendance/me/today,
 * and the nested `geofence` of check-in / clock-out. EVERY field is optional: the app must keep working when any is absent.
 */

@Serializable
data class GeoPoint(val lat: Double? = null, val lng: Double? = null)

@Serializable
data class GeoFenceDef(
    val id: String? = null,
    val name: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val radiusM: Double? = null,
    val campusId: String? = null,
    val mode: String? = null,
)

@Serializable
data class GeoRules(
    val accuracyLimitM: Double? = null,
    val maxCaptureAgeSeconds: Int? = null,
    val clockInRequireGps: Boolean? = null,
    val clockOutRequireGps: Boolean? = null,
    val clockInRequireSelfie: Boolean? = null,
    val clockOutRequireSelfie: Boolean? = null,
)

@Serializable
data class GeofenceInfo(
    /** inside | outside | no_gps | poor_accuracy; null until today's check-in. */
    val status: String? = null,
    val distanceM: Double? = null,
    /** off | soft | restrict. */
    val mode: String? = null,
    val enforced: Boolean? = null,
    val radiusM: Double? = null,
    val center: GeoPoint? = null,
    val fenceId: String? = null,
    val fenceName: String? = null,
    val campusId: String? = null,
    /** Free text or absent; kept as JSON so an unexpected type never breaks decoding. */
    val warning: JsonElement? = null,
    val isWarning: Boolean? = null,
    val isMock: Boolean? = null,
    val fences: List<GeoFenceDef>? = null,
    val rules: GeoRules? = null,
    val serverTime: String? = null,
)
