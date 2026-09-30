package com.satcop.smartvisitor.kiosk.data.face

import com.satcop.smartvisitor.kiosk.data.api.LoginErrors
import com.satcop.smartvisitor.kiosk.data.geo.GeoFenceCodes
import com.satcop.smartvisitor.kiosk.data.model.ApiException

/** Face-verify error text: the SERVER message wins (code/message from the error envelope), never a generic "face failed". */
object FaceErrors {
    const val MISMATCH_DEFAULT = "Face did not match. Try again in good light, or sign in again."
    const val SESSION_EXPIRED = "Your session expired. Go back and sign in again."

    fun message(t: Throwable): String {
        val e = LoginErrors.classify(t)
        val server = e.message.trim()
        return when {
            GeoFenceCodes.isRestricted(e.code, e.message) ->
                server.takeIf { it.isNotBlank() && !it.contains(GeoFenceCodes.GEO_FENCE_RESTRICTED) }
                    ?: GeoFenceCodes.DEFAULT_MESSAGE
            e.code == "FACE_MISMATCH" -> server.ifBlank { MISMATCH_DEFAULT }
            e.code == "UNAUTHORIZED" || e.httpStatus == 401 && e.code != "FACE_MISMATCH" -> SESSION_EXPIRED
            e.httpStatus in 400..499 && server.isNotBlank() && !e.code.startsWith("HTTP_") -> server
            else -> LoginErrors.message(e)
        }
    }
}
