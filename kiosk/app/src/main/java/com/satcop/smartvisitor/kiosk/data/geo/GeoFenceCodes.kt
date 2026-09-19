package com.satcop.smartvisitor.kiosk.data.geo

/** Backend error code for campus geo HARD block (AC-GF2). Must appear verbatim in dex. */
object GeoFenceCodes {
    const val GEO_FENCE_RESTRICTED = "GEO_FENCE_RESTRICTED"

    fun isRestricted(code: String?, message: String?): Boolean {
        val c = code.orEmpty()
        val m = message.orEmpty()
        return c == GEO_FENCE_RESTRICTED ||
            c.contains(GEO_FENCE_RESTRICTED, ignoreCase = true) ||
            m.contains(GEO_FENCE_RESTRICTED, ignoreCase = true)
    }

    /** User-visible toast — includes exact code for QA/ops. */
    fun toastMessage(): String =
        "$GEO_FENCE_RESTRICTED · Outside campus geo-fence — move inside campus"
}
