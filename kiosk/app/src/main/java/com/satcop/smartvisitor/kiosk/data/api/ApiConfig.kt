package com.satcop.smartvisitor.kiosk.data.api

/**
 * Ephemeral Cloudflare tunnel to the Day-1 FastAPI mock.
 * Typed login stores the JWT; if later calls 502, HybridKioskRepository
 * falls back to fixtures — login itself is still required.
 */
object ApiConfig {
    const val BASE_URL = "https://unnecessary-bid-catch-accuracy.trycloudflare.com/v1"
    const val CONNECT_TIMEOUT_MS = 12_000L
    const val CALL_TIMEOUT_MS = 20_000L

    /** Keys look like media/live_photo/… → GET {apiBase}/media/{key} with Bearer. */
    fun mediaUrl(key: String): String {
        val trimmed = key.trim().trimStart('/')
        return "$BASE_URL/media/$trimmed"
    }
}
