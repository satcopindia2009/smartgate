package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException
import java.io.IOException

/**
 * One place for user-facing error sentences (App Flow 1.6/1.9/3.10). Plain words only:
 * no error codes, stack traces, config flags, host names or URLs ever reach the screen.
 * Copy matches the Backend contract table (2026-09-30 §6).
 */
object ErrorCopy {
    const val INVALID_CREDENTIALS = "Invalid username or password"
    const val ACCOUNT_INACTIVE = "Your account is inactive. Please contact administrator."
    const val FORBIDDEN = "You do not have permission to access this page."
    const val SIGN_IN = "Please sign in to continue."
    const val SESSION_EXPIRED = "Your session has expired. Please sign in again."
    const val SESSION_INVALID = "Your session is not valid. Please sign in again."
    const val OUTSIDE_FENCE = "You are outside the school campus. Please move inside the campus and try again."
    const val GPS_LOW = "Unable to get an accurate location. Please enable GPS and try again."
    const val STALE = "Your location reading is too old. Please try again to get a fresh location."
    const val ALREADY_CLOCKED_OUT = "You have already clocked out for today."
    const val ALREADY_CHECKED_IN = "You are already checked in for today."
    const val NOT_CHECKED_IN = "You have not checked in today. Please check in first."
    const val PHOTO_REQUIRED = "Please take a photo to continue."
    const val FACE_REQUIRED = "Face verification is required to continue."
    const val ADMIN_USE_WEB = "Please use the web dashboard."
    const val UNREACHABLE = "Can't reach the server right now. Check your internet connection and try again."
    const val TEMPORARILY_DOWN = "Server is temporarily unreachable. Retry in a minute — this is not a wrong password."
    const val GENERIC = "Something went wrong. Please try again."

    private val technicalMarkers = listOf(
        "geofencemode", "traceback", "exception", "http://", "https://", "trycloudflare", "cloudflare",
        "tunnel", "error code", "stack", "null pointer", "sqlite", "psycopg", "jwt", "bearer ", "{\"", "<html",
        "details.allowed", "role '", "java.", "kotlin.", "android.", "okhttp", "com.satcop", "at com.",
    )

    /** True when [text] looks like an internal/technical message that must not be shown. */
    fun isTechnical(text: String?): Boolean {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return true
        val lower = t.lowercase()
        return technicalMarkers.any { it in lower } || t.length > 160
    }

    private fun isTunnelStatus(http: Int) = http == 530 || http in 502..504 || http in 520..526

    fun forApi(e: ApiException): String {
        when (e.code.uppercase()) {
            "INVALID_CREDENTIALS" -> return INVALID_CREDENTIALS
            "ACCOUNT_INACTIVE", "USER_INACTIVE" -> return ACCOUNT_INACTIVE
            "FORBIDDEN" -> return FORBIDDEN
            "UNAUTHORIZED" -> return SIGN_IN
            "TOKEN_EXPIRED" -> return SESSION_EXPIRED
            "TOKEN_INVALID" -> return SESSION_INVALID
            "GEO_FENCE_RESTRICTED" -> return OUTSIDE_FENCE
            "GPS_ACCURACY_LOW" -> return GPS_LOW
            "STALE_CAPTURE" -> return STALE
            "ALREADY_CLOCKED_OUT" -> return ALREADY_CLOCKED_OUT
            "ALREADY_CHECKED_IN" -> return ALREADY_CHECKED_IN
            "NO_ACTIVE_ATTENDANCE" -> return NOT_CHECKED_IN
            "PHOTO_REQUIRED" -> return PHOTO_REQUIRED
            "FACE_REQUIRED" -> return FACE_REQUIRED
            "INTERNAL" -> return GENERIC
            "TUNNEL_DOWN" -> return TEMPORARILY_DOWN
            "UNAVAILABLE", "UNAUTHENTICATED" -> return if (e.code.equals("UNAUTHENTICATED", true)) SIGN_IN else UNREACHABLE
        }
        if (isTunnelStatus(e.httpStatus)) return TEMPORARILY_DOWN
        if (e.httpStatus >= 500) return GENERIC
        return if (isTechnical(e.message)) GENERIC else e.message.trim()
    }

    fun forThrowable(error: Throwable): String = when (error) {
        is ApiException -> forApi(error)
        is IOException -> UNREACHABLE
        else -> {
            val raw = error.message
            if (isTechnical(raw)) GENERIC else raw!!.trim()
        }
    }

    /** Session is no longer usable -> the app must go back to Login. */
    fun isSessionEnd(e: ApiException): Boolean =
        e.code.equals("TOKEN_EXPIRED", true) || e.code.equals("TOKEN_INVALID", true)
}
