package com.satcop.smartvisitor.kiosk.data.api

import java.time.OffsetDateTime

/**
 * Token cut-off handling (App Flow 3.8). Guard tokens end at the next 00:00 IST (`sessionExpiresAt`);
 * others after `expiresIn` seconds. The client uses it to sign the user out cleanly instead of showing
 * errors, but the server remains the authority (401 TOKEN_EXPIRED is handled too).
 */
object SessionExpiry {
    /** Epoch ms at which the session ends, or null when the server gave nothing usable. */
    fun expiresAtMs(sessionExpiresAt: String?, expiresInSeconds: Int?, nowMs: Long): Long? {
        val parsed = sessionExpiresAt?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
        if (parsed != null) return parsed
        return expiresInSeconds?.takeIf { it > 0 }?.let { nowMs + it * 1000L }
    }

    fun isExpired(expiresAtMs: Long?, nowMs: Long): Boolean = expiresAtMs != null && nowMs >= expiresAtMs

    /** 401 that means "this token is over": expired/invalid, or unauthorized while a token was sent. */
    fun isSessionEnd(httpStatus: Int, code: String, hadToken: Boolean): Boolean {
        if (httpStatus != 401) return false
        val c = code.uppercase()
        if (c == "TOKEN_EXPIRED" || c == "TOKEN_INVALID") return true
        return hadToken && c == "UNAUTHORIZED"
    }
}
