package com.satcop.smartvisitor.kiosk.data.api

/**
 * Ephemeral Cloudflare tunnel to the Day-1 FastAPI mock.
 * Typed login stores the JWT; if later calls 502, HybridKioskRepository
 * falls back to fixtures — login itself is still required.
 */
object ApiConfig {
    const val BASE_URL = "https://pensions-usb-loops-direction.trycloudflare.com/v1"
    const val CONNECT_TIMEOUT_MS = 4_000L
    const val CALL_TIMEOUT_MS = 8_000L
}
