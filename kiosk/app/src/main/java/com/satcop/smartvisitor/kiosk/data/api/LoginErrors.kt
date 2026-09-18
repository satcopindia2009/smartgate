package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException

object LoginErrors {
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val INVALID_MESSAGE = "Invalid username or password"
    const val TUNNEL_MESSAGE =
        "API tunnel down (Cloudflare 1033/530). Retry in a minute — not a bad password. Base: valley-questions-poultry-kid"

    fun message(error: ApiException): String {
        if (error.code == INVALID_CREDENTIALS) return INVALID_MESSAGE
        if (isTunnel(error.code, error.message, error.httpStatus)) return TUNNEL_MESSAGE
        return error.message
    }

    fun message(error: Throwable): String = when (error) {
        is ApiException -> message(error)
        else -> {
            val raw = error.message.orEmpty()
            if (isTunnel("", raw, 0)) TUNNEL_MESSAGE
            else raw.takeIf { it.isNotBlank() } ?: "Could not reach live API"
        }
    }

    private fun isTunnel(code: String, message: String, http: Int): Boolean {
        val blob = "$code $message".lowercase()
        return http in setOf(502, 503, 504, 520, 521, 522, 523, 524, 525, 526, 530) ||
            "1033" in blob ||
            "cloudflare" in blob ||
            "error code: 1033" in blob ||
            code == "TUNNEL_DOWN" ||
            code == "HTTP_530"
    }
}
