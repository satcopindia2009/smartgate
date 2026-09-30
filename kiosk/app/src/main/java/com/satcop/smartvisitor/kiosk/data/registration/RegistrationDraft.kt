package com.satcop.smartvisitor.kiosk.data.registration

import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind
import com.satcop.smartvisitor.kiosk.data.addvisitor.SavedIdRef
import com.satcop.smartvisitor.kiosk.data.fixture.DemoFixtures
import com.satcop.smartvisitor.kiosk.data.model.DemoStory
import com.satcop.smartvisitor.kiosk.data.model.IdType
import com.satcop.smartvisitor.kiosk.data.model.VisitorType

/**
 * In-progress gate registration. Field names match POST /v1/visits where they exist.
 */
data class RegistrationDraft(
    val visitorType: String = VisitorType.Parent.apiValue,
    val visitorName: String = "",
    val mobile: String = "",
    val company: String = "",
    val purpose: String = "",
    val hostId: String? = DemoFixtures.HOST_ANITA_ID,
    val gateId: String = DemoFixtures.GATE_MAIN_ID,
    val vehicleNumber: String = "",
    val accompanyingCount: String = "",
    val notes: String = "",
    val livePhotoCaptured: Boolean = false,
    val livePhotoKey: String? = null,
    val idType: String = IdType.Aadhaar.apiValue,
    val idNumber: String = "",
    val idImageCaptured: Boolean = false,
    val idImageKey: String? = null,
    val signatureCaptured: Boolean = false,
    val signatureKey: String? = null,
    val consentAgreed: Boolean = false,
    val consentVersion: String? = null,
    val consentAt: String? = null,
    /** 1064 Add Visitor: server-decided kind of this person (Visitor | Vendor). */
    val profileKind: ProfileKind = ProfileKind.VISITOR,
    /** ID reference the SERVER returned (type + last 4). Never a full number. */
    val savedId: SavedIdRef? = null,
    /** Default true when [savedId] exists; false after the guard taps Edit and types a new number. */
    val useSavedId: Boolean = false,
    /** Visitor only (never Vendor). null = walk-in now. */
    val scheduledAtMs: Long? = null,
    /** Server profile (PRF-nnnnn) of a known number; null for a new number. */
    val profileId: String? = null,
    /** True only after the guard confirmed "Register as different type". */
    val confirmKindSwitch: Boolean = false,
    /** visitorVerifyId of a verified visitor number (OTP); null = not verified / skipped. */
    val otpId: String? = null,
) {
    companion object {
        fun fromStory(story: DemoStory): RegistrationDraft = RegistrationDraft(
            visitorType = story.visitorType,
            visitorName = story.visitorName,
            mobile = story.mobile,
            purpose = story.purpose,
            hostId = story.hostId,
            gateId = story.gateId,
            idType = IdType.Aadhaar.apiValue,
            idNumber = "XXXX1234",
        )

        fun blockSample(): RegistrationDraft = RegistrationDraft(
            visitorType = VisitorType.Guest.apiValue,
            visitorName = "Vikram More",
            mobile = "+91 98765 00001",
            purpose = "Meet accounts",
            hostId = "H04",
            gateId = DemoFixtures.GATE_MAIN_ID,
            idType = IdType.Aadhaar.apiValue,
            idNumber = "XXXX-XXXX-3321",
        )

        fun alertSample(): RegistrationDraft = RegistrationDraft(
            visitorType = VisitorType.Vendor.apiValue,
            visitorName = "Neha Salunkhe",
            mobile = "+91 98765 00002",
            purpose = "Stationery delivery",
            hostId = "H02",
            gateId = "G-PED",
            idType = IdType.DL.apiValue,
            idNumber = "MH12-XXXX-8890",
        )
    }
}
