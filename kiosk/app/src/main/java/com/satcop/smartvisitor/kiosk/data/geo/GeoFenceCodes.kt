package com.satcop.smartvisitor.kiosk.data.geo

/** Backend error code for campus geo HARD block (AC-GF2). Must appear verbatim in dex. */
object GeoFenceCodes {
    const val GEO_FENCE_RESTRICTED = "GEO_FENCE_RESTRICTED"
    const val DEFAULT_MESSAGE = "You are outside the school campus. Please move inside the campus and try again."

    fun isRestricted(code: String?, message: String?): Boolean {
        val c = code.orEmpty()
        val m = message.orEmpty()
        return c == GEO_FENCE_RESTRICTED ||
            c.contains(GEO_FENCE_RESTRICTED, ignoreCase = true) ||
            m.contains(GEO_FENCE_RESTRICTED, ignoreCase = true)
    }

    /** User-visible toast — includes exact code for QA/ops. */
    fun toastMessage(serverMessage: String? = null): String =
        serverMessage?.takeIf { it.isNotBlank() && !it.contains(GEO_FENCE_RESTRICTED) }
            ?: DEFAULT_MESSAGE
}
