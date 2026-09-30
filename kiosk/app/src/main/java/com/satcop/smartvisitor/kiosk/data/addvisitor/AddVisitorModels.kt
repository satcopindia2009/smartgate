package com.satcop.smartvisitor.kiosk.data.addvisitor

/** Person profile kind. The SERVER decides it (lookup); the client only renders the matching form. */
enum class ProfileKind { VISITOR, VENDOR }

/**
 * Saved ID reference as returned by the server: the type plus the LAST 4 characters only.
 * The full number never reaches a guard/gate device, so it can never be sent back either.
 */
data class SavedIdRef(val idType: String, val last4: String) {
    val display: String get() = MaskedDisplay.idRef(idType, last4)
}

/** An open visit for this person (inside, or waiting for host approval). */
data class ActiveVisit(
    val visitId: String,
    val status: String,
    val sinceLabel: String? = null,
    val gateName: String? = null,
) {
    val isInside: Boolean get() = status.equals("inside", true) || status.equals("checked_in", true)
}

/** D15: same-day visit that is waiting for the host (pending) or approved but not yet checked in. */
data class OpenVisitInfo(
    val visitId: String,
    val status: String,
    val hostName: String? = null,
    val hostId: String? = null,
    /** Extra rows for the card (boards 22/23). Only what the lookup returned; null when the server sent nothing. */
    val visitorName: String? = null,
    val mobileMasked: String? = null,
    val purpose: String? = null,
    /** "10:15 am" from the server's `since`, IST. */
    val askedAtLabel: String? = null,
    val photoUrl: String? = null,
) {
    val isApproved: Boolean get() = status.equals("approved", true)
}

/** What the server knows about the mobile number (already deduped across visitor + vendor). */
data class KnownProfile(
    val kind: ProfileKind,
    val name: String? = null,
    val company: String? = null,
    /** Media key of the last photo: shown as a small reference only, never a substitute for a fresh photo. */
    val photoKey: String? = null,
    val idRef: SavedIdRef? = null,
    val lastHostId: String? = null,
    val lastPurpose: String? = null,
    val lastVisitLabel: String? = null,
    /** API visitorType to send on the visit (kept as the server gave it). */
    val visitorTypeApi: String? = null,
    /** Server profile id (PRF-nnnnn): sent back on the visit so the server links, never duplicates. */
    val profileId: String? = null,
    /** Absolute URL with a 30-minute signed ?t= token: fetched as given (never rebuilt). */
    val photoUrl: String? = null,
    val lastHostName: String? = null,
    /** Yellow banner text for an Alert-severity number ("Alert: call the security head before entry."). */
    val alertMessage: String? = null,
    /** "Approved earlier today" / "Rejected earlier today: <reason>" line for the guard. */
    val todayNote: String? = null,
)

sealed interface LookupOutcome {
    data class Found(val profile: KnownProfile) : LookupOutcome
    data object NotFound : LookupOutcome
    /** Blacklisted number (or ID): red block screen, no form, no reason for the guard. */
    data object Blocked : LookupOutcome
    data class AlreadyInside(val active: ActiveVisit) : LookupOutcome
    /** D15 (AC-AV7): pending / approved visit exists. No second visit is ever created. */
    data class OpenVisit(val open: OpenVisitInfo) : LookupOutcome
    data object InvalidNumber : LookupOutcome
    /** Network/server problem. There is NO fallback to a blank form (it would create duplicates). */
    data class Failed(val message: String) : LookupOutcome
}

/** Server-side lookup only. The client renders the answer and keeps no local profile cache. */
interface AddVisitorLookup {
    suspend fun lookup(tenDigitMobile: String): LookupOutcome
}
