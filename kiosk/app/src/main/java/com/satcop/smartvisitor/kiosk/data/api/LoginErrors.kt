package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.serialization.SerializationException

/** User-facing error text. Never contains host names, stack text or raw HTML. */
object LoginErrors {
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val NETWORK_OFFLINE = "NETWORK_OFFLINE"
    const val TIMEOUT = "TIMEOUT"
    const val TLS = "TLS"
    const val BAD_RESPONSE = "BAD_RESPONSE"
    const val INVALID_MESSAGE = "Invalid username or password"
    const val OFFLINE_MESSAGE = "No internet connection. Check Wi-Fi or mobile data and try again."
    const val TIMEOUT_MESSAGE = "The server is taking too long to respond. Please try again."
    const val TLS_MESSAGE = "Secure connection failed. Check the phone's date and time, then try again."
    const val BAD_RESPONSE_MESSAGE = "Unexpected reply from the server. Please try again."
    const val UNAVAILABLE_MESSAGE = "Could not reach the server. Please try again."
    const val TUNNEL_MESSAGE =
        "Server is temporarily unreachable. Retry in a minute — this is not a wrong password."

    /** Turn any throwable into an [ApiException] with a stable code (keeps type info lost by message-only wrapping). */
    fun classify(t: Throwable): ApiException = when (t) {
        is ApiException -> t
        is UnknownHostException, is ConnectException, is NoRouteToHostException ->
            ApiException(NETWORK_OFFLINE, OFFLINE_MESSAGE, 0)
        is InterruptedIOException -> ApiException(TIMEOUT, TIMEOUT_MESSAGE, 0)
        is SSLException -> ApiException(TLS, TLS_MESSAGE, 0)
        is SerializationException, is IllegalArgumentException ->
            ApiException(BAD_RESPONSE, BAD_RESPONSE_MESSAGE, 0)
        else -> ApiException("UNAVAILABLE", UNAVAILABLE_MESSAGE, 0)
    }

    fun message(error: ApiException): String = when {
        error.code == INVALID_CREDENTIALS -> INVALID_MESSAGE
        error.code == NETWORK_OFFLINE -> OFFLINE_MESSAGE
        error.code == TIMEOUT -> TIMEOUT_MESSAGE
        error.code == TLS -> TLS_MESSAGE
        error.code == BAD_RESPONSE -> BAD_RESPONSE_MESSAGE
        isTunnel(error.code, error.message, error.httpStatus) -> TUNNEL_MESSAGE
        error.code == "UNAVAILABLE" -> UNAVAILABLE_MESSAGE
        else -> error.message.ifBlank { UNAVAILABLE_MESSAGE }
    }

    fun message(error: Throwable): String = message(classify(error))

    private fun isTunnel(code: String, message: String, http: Int): Boolean {
        val blob = "$code $message".lowercase()
        return http in setOf(502, 503, 504, 520, 521, 522, 523, 524, 525, 526, 530) ||
            "1033" in blob ||
            "cloudflare" in blob ||
            code == "TUNNEL_DOWN" ||
            code == "HTTP_530"
    }
}

/** Login field hygiene: phone keyboards capitalise ("Gate") and append spaces. */
object LoginInput {
    fun normalizeUsername(raw: String): String = raw.trim().lowercase(java.util.Locale.ROOT)
}
