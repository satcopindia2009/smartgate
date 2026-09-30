package com.satcop.smartvisitor.kiosk.data.registration

import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.MaskedDisplay

object FieldKeys {
    const val VISITOR_NAME = "visitorName"
    const val MOBILE = "mobile"
    const val PURPOSE = "purpose"
    const val COMPANY = "company"
    const val HOST_ID = "hostId"
    const val VISITOR_TYPE = "visitorType"
    const val ACCOMPANYING = "accompanyingCount"
    const val LIVE_PHOTO = "livePhotoKey"
    const val ID = "idNumber"
    const val ID_TYPE = "idType"
    const val ID_IMAGE = "idImageKey"
    const val ID_TYPE_NAME = "idTypeName"
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
            errors[FieldKeys.VISITOR_NAME] = "Name is required."
        }
        if (draft.mobile.trim().isEmpty()) {
            errors[FieldKeys.MOBILE] = "Mobile is required"
        } else if (!MobileIndia.isValid(draft.mobile)) {
            errors[FieldKeys.MOBILE] = "Enter a valid +91 mobile number"
        }
        if (draft.purpose.trim().isEmpty()) {
            errors[FieldKeys.PURPOSE] = "Purpose of visit is required"
        }
        if (draft.profileKind == com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind.VENDOR &&
            draft.company.trim().isEmpty()
        ) {
            errors[FieldKeys.COMPANY] = "Company is required"
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

    /** Everything the single Add Visitor form needs before Submit. */
    fun validateForm(draft: RegistrationDraft): Map<String, String> = validateStep2(draft) + validateStep3(draft)

    /** Live photo required; govt ID number ALWAYS compulsory (ID image never substitutes). */
    fun validateStep3(draft: RegistrationDraft): Map<String, String> {
        val errors = linkedMapOf<String, String>()
        if (!draft.livePhotoCaptured) {
            errors[FieldKeys.LIVE_PHOTO] = AddVisitorLogic.LIVE_PHOTO_REQUIRED
        }
        if (draft.idType.isBlank()) {
            errors[FieldKeys.ID_TYPE] = "Select an ID type"
        }
        // 1064: ID number (typed, or the saved ID the server holds) AND a fresh ID photo are required every time.
        val savedOk = draft.useSavedId && draft.savedId != null
        if (!savedOk && (draft.idNumber.trim().isEmpty() || MaskedDisplay.looksMasked(draft.idNumber))) {
            errors[FieldKeys.ID] = AddVisitorLogic.ID_REQUIRED
        }
        if (draft.idType == "Other" && !savedOk && draft.idTypeName.trim().isEmpty()) {
            errors[FieldKeys.ID_TYPE_NAME] = AddVisitorLogic.ID_TYPE_NAME_REQUIRED
        }
        if (!draft.idImageCaptured) {
            errors[FieldKeys.ID_IMAGE] = AddVisitorLogic.ID_PHOTO_REQUIRED
        }
        return errors
    }

    fun toastMessage(errors: Map<String, String>): String {
        val required = listOf(FieldKeys.VISITOR_NAME, FieldKeys.MOBILE, FieldKeys.PURPOSE, FieldKeys.HOST_ID)
        return when {
            FieldKeys.LIVE_PHOTO in errors -> AddVisitorLogic.LIVE_PHOTO_REQUIRED
            FieldKeys.ID in errors -> AddVisitorLogic.ID_REQUIRED
            FieldKeys.ID_IMAGE in errors -> AddVisitorLogic.ID_PHOTO_REQUIRED
            required.any { it in errors } -> "Please fill name, mobile, purpose, and host"
            else -> errors.values.firstOrNull() ?: "Please check the form"
        }
    }
}
