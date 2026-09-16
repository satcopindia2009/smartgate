package com.satcop.smartvisitor.kiosk.data.registration

object FieldKeys {
    const val VISITOR_NAME = "visitorName"
    const val MOBILE = "mobile"
    const val PURPOSE = "purpose"
    const val HOST_ID = "hostId"
    const val VISITOR_TYPE = "visitorType"
    const val ACCOMPANYING = "accompanyingCount"
    const val LIVE_PHOTO = "livePhotoKey"
    const val ID = "idNumber"
    const val ID_TYPE = "idType"
}

object MobileIndia {
    fun digits(raw: String): String = raw.filter { it.isDigit() }

    fun tenDigit(raw: String): String? {
        val d = digits(raw)
        val ten = when {
            d.length == 10 -> d
            d.length == 12 && d.startsWith("91") -> d.substring(2)
            d.length == 11 && d.startsWith("0") -> d.substring(1)
            else -> return null
        }
        return ten.takeIf { it.length == 10 && it.first() in '6'..'9' }
    }

    fun isValid(raw: String): Boolean = tenDigit(raw) != null

    /** Display form used in the dark demo: +91 98220 11122 */
    fun formatDisplay(raw: String): String {
        val ten = tenDigit(raw) ?: return raw.trim()
        return "+91 ${ten.substring(0, 5)} ${ten.substring(5)}"
    }

    fun normalizeE164(raw: String): String? = tenDigit(raw)?.let { "+91$it" }
}

object RegistrationValidator {
    fun validateStep2(draft: RegistrationDraft): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (draft.visitorType.isBlank()) {
            errors[FieldKeys.VISITOR_TYPE] = "Select a visitor type"
        }
        if (draft.visitorName.trim().isEmpty()) {
            errors[FieldKeys.VISITOR_NAME] = "Full name is required"
        }
        if (draft.mobile.trim().isEmpty()) {
            errors[FieldKeys.MOBILE] = "Mobile is required"
        } else if (!MobileIndia.isValid(draft.mobile)) {
            errors[FieldKeys.MOBILE] = "Enter a valid +91 mobile number"
        }
        if (draft.purpose.trim().isEmpty()) {
            errors[FieldKeys.PURPOSE] = "Purpose of visit is required"
        }
        if (draft.hostId.isNullOrBlank()) {
            errors[FieldKeys.HOST_ID] = "Select a host to meet"
        }
        val count = draft.accompanyingCount.trim()
        if (count.isNotEmpty()) {
            val n = count.toIntOrNull()
            if (n == null || n < 0) {
                errors[FieldKeys.ACCOMPANYING] = "Accompanying count must be 0 or more"
            }
        }
        return errors
    }

    /** Wave 2: live photo required; V1 = ID number OR ID image (number preferred). */
    fun validateStep3(draft: RegistrationDraft): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (!draft.livePhotoCaptured) {
            errors[FieldKeys.LIVE_PHOTO] = "Live photo is required"
        }
        if (draft.idType.isBlank()) {
            errors[FieldKeys.ID_TYPE] = "Select an ID type"
        }
        if (draft.idNumber.trim().isEmpty() && !draft.idImageCaptured) {
            errors[FieldKeys.ID] = "Enter an ID number or attach an ID image"
        }
        return errors
    }

    fun toastMessage(errors: Map<String, String>): String {
        val required = listOf(FieldKeys.VISITOR_NAME, FieldKeys.MOBILE, FieldKeys.PURPOSE, FieldKeys.HOST_ID)
        return when {
            FieldKeys.LIVE_PHOTO in errors -> "Please capture a live photo"
            FieldKeys.ID in errors -> "ID number or ID image is required"
            required.any { it in errors } -> "Please fill name, mobile, purpose, and host"
            else -> errors.values.firstOrNull() ?: "Please check the form"
        }
    }
}
