package com.satcop.smartvisitor.kiosk.data.api

import java.net.URI

/**
 * Media URLs come from the API (visitor photo, ID photo, guardPhotoUrl, ...) and carry a signed ?t= token (30 min).
 * Rules: use the URL exactly as returned (query untouched); a relative "/v1/media/..." path is resolved against the
 * API origin only; the Bearer header is attached only when the host is the API host (never to a third party).
 */
object MediaUrl {
    data class Resolved(val url: String, val attachBearer: Boolean)

    fun resolve(raw: String?, apiBase: String = ApiConfig.BASE_URL): Resolved? {
        val u = raw?.trim().orEmpty()
        if (u.isEmpty()) return null
        val base = runCatching { URI(apiBase) }.getOrNull() ?: return null
        val origin = "${base.scheme}://${base.authority}"
        return when {
            u.startsWith("/") -> Resolved(origin + u, true)
            u.startsWith("http://", true) || u.startsWith("https://", true) -> {
                val host = runCatching { URI(u).authority }.getOrNull() ?: return null
                Resolved(u, host.equals(base.authority, ignoreCase = true))
            }
            else -> null
        }
    }

    /** True when the URL carries a signed token that may have expired (caller re-runs the lookup to get a fresh one). */
    fun hasSignedToken(url: String?): Boolean = url?.contains("?t=") == true || url?.contains("&t=") == true
}
