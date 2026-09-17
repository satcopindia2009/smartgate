package com.satcop.smartvisitor.kiosk.data.api

/**
 * Ephemeral Cloudflare tunnel to the Day-1 FastAPI mock.
 * If it 502s, HybridKioskRepository falls back to fixtures — do not block the kiosk.
 */
object ApiConfig {
    const val BASE_URL = "https://replacing-spyware-yes-due.trycloudflare.com/v1"
    const val GATE_USERNAME = "gate"
    const val GATE_PASSWORD = "gate123"
    const val HOST_USERNAME = "host"
    const val HOST_PASSWORD = "host123"
    const val CONNECT_TIMEOUT_MS = 4_000L
    const val CALL_TIMEOUT_MS = 8_000L
}
