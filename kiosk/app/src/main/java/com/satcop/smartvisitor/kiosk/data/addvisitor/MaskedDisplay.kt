package com.satcop.smartvisitor.kiosk.data.addvisitor

/**
 * Display helpers for values the API already masks for guard/gate tokens (Q09-priv).
 * Rule: show only what the API returned; never rebuild, guess or send a masked value back.
 */
object MaskedDisplay {
    private const val DOTS = "••••"

    /** "Aadhaar ••••1234" (type + last 4). */
    fun idRef(idType: String, last4: String): String {
        val tail = last4.filter { it.isLetterOrDigit() && it != 'X' && it != 'x' }.takeLast(4)
        val type = idType.trim().ifEmpty { "ID" }
        return if (tail.isEmpty()) "$type $DOTS" else "$type $DOTS$tail"
    }

    /** True when [value] is (or contains) a masked placeholder and therefore must NEVER be sent as an edit. */
    fun looksMasked(value: String?): Boolean {
        val v = value?.trim().orEmpty()
        if (v.isEmpty()) return false
        return '•' in v || '*' in v || '●' in v || Regex("[Xx]{2,}").containsMatchIn(v)
    }

    /**
     * Mobile as the guard may see it: last 4 digits only, whatever form the API used
     * ("XXXXXX1234", "••••••1234", "9822011122" all give "••••••1234").
     */
    fun mobile(raw: String?): String {
        val digits = raw.orEmpty().filter { it.isDigit() }
        return when {
            digits.isEmpty() -> ""
            digits.length < 4 -> DOTS
            else -> "••••••" + digits.takeLast(4)
        }
    }
}
