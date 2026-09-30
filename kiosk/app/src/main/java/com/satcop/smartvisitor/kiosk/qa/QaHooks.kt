package com.satcop.smartvisitor.kiosk.qa

import android.graphics.Bitmap

/**
 * Seam for the QA-only build. In every normal build [provider] stays null, so nothing here does anything.
 * The ONLY implementation lives in src/debugqa (separate applicationId .debugqa) and registers itself at start-up
 * from that source set's own manifest; it is not part of the release/debug/demo variants.
 * It never bypasses SERVER checks: it only replaces the local camera with a test-pattern JPEG.
 */
interface QaProvider {
    /** Test-pattern picture standing in for a camera frame (emulators have no front camera). */
    fun testPattern(label: String): Bitmap
}

object QaHooks {
    @Volatile
    var provider: QaProvider? = null

    /** True only inside the QA-only build. */
    val fakeCamera: Boolean get() = provider != null

    fun frame(label: String): Bitmap? = provider?.testPattern(label)
}
