package com.satcop.smartvisitor.kiosk.data.api

import com.satcop.smartvisitor.kiosk.data.model.ApiException

object LoginErrors {
    const val INVALID_CREDENTIALS = "INVALID_CREDENTIALS"
    const val INVALID_MESSAGE = "Invalid username or password"

    fun message(error: ApiException): String =
        if (error.code == INVALID_CREDENTIALS) INVALID_MESSAGE else error.message

    fun message(error: Throwable): String = when (error) {
        is ApiException -> message(error)
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Could not reach live API"
    }
}
