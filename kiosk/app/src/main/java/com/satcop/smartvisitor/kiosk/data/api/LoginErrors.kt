package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException

/** Login-screen error sentences. Delegates to [ErrorCopy]; never shows a host name or tunnel detail. */
object LoginErrors {
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val INVALID_MESSAGE = ErrorCopy.INVALID_CREDENTIALS

    /** Shown when the API tunnel/origin is down. Deliberately contains no host name. */
    const val TUNNEL_MESSAGE = ErrorCopy.TEMPORARILY_DOWN

    fun message(error: ApiException): String = ErrorCopy.forApi(error)

    fun message(error: Throwable): String = ErrorCopy.forThrowable(error)
}
