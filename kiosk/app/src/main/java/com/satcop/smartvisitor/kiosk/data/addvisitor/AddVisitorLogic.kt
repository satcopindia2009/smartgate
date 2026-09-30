package com.satcop.smartvisitor.kiosk.data.addvisitor

import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia

/** Pure rules for the Add Visitor rework (spec: product/add-visitor-spec-2026-09-30.md). */
object AddVisitorLogic {
    const val INVALID_MOBILE = "Enter a valid 10-digit mobile number."
    const val LOOKUP_FAILED = "Can't check right now. Try again."
    const val BLOCKED = "Entry not allowed. Call the security head."
    const val ID_REQUIRED = "ID number is required."
    const val ID_PHOTO_REQUIRED = "Take a photo of the ID."
    const val ID_HELPER = "Required for every entry."
    const val WAITING_HOST = "Waiting for host approval"
    const val ALERT_DEFAULT = "Alert: call the security head before entry."
    const val SWITCH_TYPE_LINK = "Register as different type"
    const val SWITCH_TYPE_CONFIRM = "Register this number as a different type? The history is kept and the change is recorded."

    /** Continue is enabled only for a valid 10-digit Indian mobile (first digit 6-9). */
    fun canLookup(raw: String): Boolean = MobileIndia.isValid(digitsOnly(raw))

    fun digitsOnly(raw: String): String = raw.filter { it.isDigit() }

    /**
     * Inline error while typing: only once the entry is clearly wrong (first digit not 6-9, or 10+ digits
     * that are not a valid number), so a half-typed number is not shouted at.
     */
    fun mobileError(raw: String): String? {
        val d = digitsOnly(raw)
        if (d.isEmpty()) return null
        val ten = d.removePrefix("91").takeIf { d.length == 12 } ?: d.removePrefix("0").takeIf { d.length == 11 } ?: d
        if (ten.first() !in '6'..'9') return INVALID_MOBILE
        if (d.length >= 10 && !MobileIndia.isValid(d)) return INVALID_MOBILE
        return null
    }

    /** Header text per form. */
    fun formTitle(kind: ProfileKind, returning: Boolean): String = when {
        returning && kind == ProfileKind.VENDOR -> "Returning vendor"
        returning -> "Returning visitor"
        kind == ProfileKind.VENDOR -> "New vendor"
        else -> "New visitor"
    }

    /** Vendor forms never show a scheduled date/time (AC-AV2). */
    fun showsSchedule(kind: ProfileKind): Boolean = kind == ProfileKind.VISITOR

    /** Type selector is only for an unknown number; an existing vendor skips it (form D). */
    fun showsTypeSelector(returning: Boolean): Boolean = !returning

    fun hostLabel(kind: ProfileKind): String =
        if (kind == ProfileKind.VENDOR) "Host / Department" else "Person to meet (host)"

    /**
     * visitorType sent on the visit. INTERIM mapping until the Backend contract names the two values
     * (Visitor -> existing "Guest", Vendor -> "Vendor"); a returning visitor keeps what the server gave.
     */
    fun apiVisitorType(kind: ProfileKind, serverGiven: String?): String = when {
        kind == ProfileKind.VENDOR -> "Vendor"
        !serverGiven.isNullOrBlank() && !serverGiven.equals("Vendor", true) -> serverGiven
        else -> "Guest"
    }

    /**
     * visitorType for POST /visits (contract section 4): OMITTED for a number the server already knows (auto-link to
     * its profile type, no 409); explicit for a new number; explicit only together with confirmKindSwitch when the
     * guard chose "Register as different type".
     */
    fun visitorTypeToSend(kind: ProfileKind, profileId: String?, confirmKindSwitch: Boolean): String? = when {
        profileId != null && !confirmKindSwitch -> null
        kind == ProfileKind.VENDOR -> "Vendor"
        else -> "Guest"
    }

    /** Vendor company is NOT server-required: the form enforces it (trimmed, non-empty). */
    fun companyToSend(kind: ProfileKind, company: String): String? =
        if (kind == ProfileKind.VENDOR) company.trim().ifEmpty { null } else null

    /** scheduledAt only for visitors (a vendor with scheduledAt is refused by the server). */
    fun scheduledAtToSend(kind: ProfileKind, ms: Long?): String? =
        if (kind == ProfileKind.VISITOR && ms != null) {
            java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.of("Asia/Kolkata"))
                .format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        } else null

    /** ID number to send: null when the saved ID is used or the value is a masked placeholder (never send those). */
    fun idNumberToSend(useSavedId: Boolean, typed: String): String? {
        if (useSavedId) return null
        val t = typed.trim()
        if (t.isEmpty() || MaskedDisplay.looksMasked(t)) return null
        return t
    }

    /** Step-3 ID validation: number required unless the saved ID is used; ID photo always required. */
    fun validateId(
        useSavedId: Boolean,
        hasSavedRef: Boolean,
        typed: String,
        idPhotoCaptured: Boolean,
    ): Map<String, String> {
        val e = linkedMapOf<String, String>()
        val savedOk = useSavedId && hasSavedRef
        if (!savedOk) {
            val t = typed.trim()
            if (t.isEmpty() || MaskedDisplay.looksMasked(t)) e["idNumber"] = ID_REQUIRED
        }
        if (!idPhotoCaptured) e["idImageKey"] = ID_PHOTO_REQUIRED
        return e
    }
}
