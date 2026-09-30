package com.satcop.smartvisitor.kiosk.ui.addvisitor

import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.LookupOutcome
import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft

/** Pure state transitions for Add Visitor (Compose-free, unit-tested). */
object AddVisitorReducer {
    const val MAX_DIGITS = 13 // "+91" + 10 digits with a stray zero: never accept more than this

    fun fresh(): AddVisitorState = AddVisitorState()

    /** Typing in the number-only field: keep digits, show an inline error only when clearly wrong. */
    fun typeMobile(s: AddVisitorState, raw: String): AddVisitorState {
        val d = AddVisitorLogic.digitsOnly(raw).take(MAX_DIGITS)
        return s.copy(mobileInput = d, mobileError = AddVisitorLogic.mobileError(d), notice = null)
    }

    /** Continue pressed. Returns the ten digits to look up, or null (with the inline error set). */
    fun tenDigitsOrError(s: AddVisitorState): Pair<AddVisitorState, String?> {
        val ten = MobileIndia.tenDigit(s.mobileInput)
        return if (ten == null) {
            s.copy(mobileError = AddVisitorLogic.INVALID_MOBILE) to null
        } else {
            s.copy(busy = true, mobileError = null, notice = null) to ten
        }
    }

    fun applyOutcome(s: AddVisitorState, outcome: LookupOutcome): AddVisitorState {
        val base = s.copy(busy = false)
        return when (outcome) {
            is LookupOutcome.Found -> base.copy(
                stage = AvStage.FORM,
                known = outcome.profile,
                kind = outcome.profile.kind, // existing vendor => vendor form directly (type selector skipped)
                notice = null,
            )
            LookupOutcome.NotFound -> base.copy(
                stage = AvStage.FORM, known = null, kind = ProfileKind.VISITOR, notice = null,
            )
            LookupOutcome.Blocked -> base.copy(stage = AvStage.NUMBER, notice = AvNotice.Blocked)
            is LookupOutcome.AlreadyInside -> base.copy(stage = AvStage.NUMBER, notice = AvNotice.Inside(outcome.active))
            is LookupOutcome.OpenVisit -> base.copy(stage = AvStage.NUMBER, notice = AvNotice.Open(outcome.open))
            LookupOutcome.InvalidNumber -> base.copy(
                stage = AvStage.NUMBER, mobileError = AddVisitorLogic.INVALID_MOBILE, notice = null,
            )
            is LookupOutcome.Failed -> base.copy(stage = AvStage.NUMBER, notice = AvNotice.Failed(outcome.message))
        }
    }

    /** Visitor | Vendor selector (only offered for a number the server does not know). */
    fun chooseKind(s: AddVisitorState, kind: ProfileKind): AddVisitorState =
        if (s.returning) s else s.copy(kind = kind)

    /** "Register as different type" (after the guard confirmed): flips the kind and marks the switch for the server. */
    fun switchKind(s: AddVisitorState, d: RegistrationDraft): Pair<AddVisitorState, RegistrationDraft> {
        val next = if (s.kind == ProfileKind.VENDOR) ProfileKind.VISITOR else ProfileKind.VENDOR
        return s.copy(kind = next) to d.copy(
            profileKind = next, confirmKindSwitch = true,
            visitorType = AddVisitorLogic.apiVisitorType(next, null),
            scheduledAtMs = if (next == ProfileKind.VENDOR) null else d.scheduledAtMs,
        )
    }

    /** A 409 PROFILE_TYPE_CONFLICT / ALREADY_INSIDE on save: back to the number step and look up again. */
    fun backToNumber(s: AddVisitorState): AddVisitorState =
        AddVisitorState(mobileInput = s.mobileInput)

    fun closeNotice(s: AddVisitorState): AddVisitorState = s.copy(notice = null)

    /**
     * Build the draft for the form. Server values only prefill; the guard can edit everything except mobile.
     * A masked or empty server value never becomes an ID number: [RegistrationDraft.idNumber] stays blank.
     */
    fun draftFor(base: RegistrationDraft, s: AddVisitorState, ten: String, validHostIds: Set<String>): RegistrationDraft {
        val k = s.known
        val kind = s.kind
        val host = k?.lastHostId?.takeIf { it in validHostIds }
        return base.copy(
            visitorType = AddVisitorLogic.apiVisitorType(kind, k?.visitorTypeApi),
            profileKind = kind,
            visitorName = k?.name.orEmpty(),
            mobile = MobileIndia.formatDisplay(ten),
            company = k?.company.orEmpty(),
            purpose = k?.lastPurpose.orEmpty(),
            hostId = host,
            idNumber = "",
            idTypeName = "",
            savedId = k?.idRef,
            useSavedId = k?.idRef != null,
            scheduledAtMs = null,
            profileId = k?.profileId,
            confirmKindSwitch = false,
            livePhotoCaptured = false,
            livePhotoKey = null,
            idImageCaptured = false,
            idImageKey = null,
            signatureCaptured = false,
            signatureKey = null,
            consentAgreed = false,
            consentVersion = null,
            consentAt = null,
        ).let { d -> k?.idRef?.let { d.copy(idType = it.idType) } ?: d }
    }
}
