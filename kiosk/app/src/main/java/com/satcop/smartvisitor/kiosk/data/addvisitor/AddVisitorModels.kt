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
)

sealed interface LookupOutcome {
    data class Found(val profile: KnownProfile) : LookupOutcome
    data object NotFound : LookupOutcome
    /** Blacklisted number (or ID): red block screen, no form, no reason for the guard. */
    data object Blocked : LookupOutcome
    data class AlreadyInside(val active: ActiveVisit) : LookupOutcome
    data object InvalidNumber : LookupOutcome
    /** Network/server problem. There is NO fallback to a blank form (it would create duplicates). */
    data class Failed(val message: String) : LookupOutcome
}

/** Server-side lookup only. The client renders the answer and keeps no local profile cache. */
interface AddVisitorLookup {
    suspend fun lookup(tenDigitMobile: String): LookupOutcome
}
