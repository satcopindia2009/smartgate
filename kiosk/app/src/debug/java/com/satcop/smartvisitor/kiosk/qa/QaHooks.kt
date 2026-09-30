package com.satcop.smartvisitor.kiosk.qa

import android.graphics.Bitmap
import com.satcop.smartvisitor.kiosk.data.otp.OtpRepository

/**
 * NO-OP seam. This is the ONLY version of QaHooks that exists in the normal debug and release builds:
 * there is no fake camera and nothing can turn one on. The real implementation lives solely in the separate
 * QA-only source set and is never on the release source path.
 */
object QaHooks {
    const val fakeCamera: Boolean = false
    const val bannerText: String = ""

    /** OTP entry points shown before the contract is bound: never in this build. */
    const val otpEntryPoints: Boolean = false

    fun otpRepository(): OtpRepository? = null

    @Suppress("UNUSED_PARAMETER")
    fun frame(label: String): Bitmap? = null
}
