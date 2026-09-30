package com.satcop.smartvisitor.kiosk.qa

import android.graphics.Bitmap

/**
 * QA-ONLY (src/debugqa, applicationId ...kiosk.debugqa). Emulators have no front camera, so every camera capture
 * (guard selfie, face verification, visitor live photo, ID photo, incident/courier/lost-found photos) can be replaced
 * with a generated test-pattern picture. This is a LOCAL camera stand-in only: the SERVER still decides face match,
 * geofence, attendance state and permissions, so no server check is skipped.
 */
object QaHooks {
    @Volatile
    var enabled: Boolean = true

    val fakeCamera: Boolean get() = enabled
    const val bannerText: String = "TEST CAMERA (QA build)"

    fun frame(label: String): Bitmap? = if (enabled) DebugQaTestPattern.create(label) else null
}
