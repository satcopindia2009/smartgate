package com.satcop.smartvisitor.kiosk.ui.apple

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Host visit detail: masked mobile display + dial intent (board 31, content ruling 1). Pure helpers, unit tested. */
object HostCall {
    /** "XXXXXX1234": last 4 digits only, whatever form the API used (full number or already masked). */
    fun maskedMobile(raw: String?): String {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ""
            digits.length < 4 -> "XXXXXX"
            else -> "XXXXXX" + digits.takeLast(4)
        }
    }

    /** Number to dial, or null when the API gave only a masked value (then there is nothing to call). */
    fun dialNumber(raw: String?): String? {
        val v = raw?.trim().orEmpty()
        if (v.isEmpty() || '•' in v || '*' in v || '●' in v || Regex("[Xx]{2,}").containsMatchIn(v)) return null
        val digits = v.filter { it.isDigit() }
        return when {
            digits.length == 10 -> "+91$digits"
            digits.length == 12 && digits.startsWith("91") -> "+$digits"
            digits.length >= 7 -> (if (v.startsWith("+")) "+" else "") + digits
            else -> null
        }
    }

    /** ACTION_DIAL opens the dialer only; no CALL_PHONE permission needed. */
    fun dialIntent(number: String): Intent =
        Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun dial(context: Context, number: String) {
        runCatching { context.startActivity(dialIntent(number)) }
    }
}
