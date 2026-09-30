package com.satcop.smartvisitor.kiosk.data.addvisitor

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * INTERIM live binding on the endpoint that exists today (GET /v1/visitors/lookup-by-mobile, fields as in
 * VisitorLookupResponse). Server-side only, no fallback to fixtures and no local cache.
 *
 * NOT available on this endpoint (waiting for the Backend add-visitor contract, deliberately not guessed):
 * masked ID reference, open-visit ("already inside") info, unified visitor/vendor kind, PROFILE_TYPE_CONFLICT.
 * Until then: vendor is inferred only from lastVisitorType == "Vendor".
 */
class InterimLookup(private val api: LiveVisitorApi = LiveVisitorApi()) : AddVisitorLookup {
    override suspend fun lookup(tenDigitMobile: String): LookupOutcome = try {
        val r = withContext(Dispatchers.IO) { api.lookupVisitorByMobile(tenDigitMobile) }
        when {
            r.blacklistHit && r.blacklistSeverity.equals("Block", ignoreCase = true) -> LookupOutcome.Blocked
            !r.found && r.visitorName.isNullOrBlank() -> LookupOutcome.NotFound
            else -> LookupOutcome.Found(
                KnownProfile(
                    kind = if (r.lastVisitorType.equals("Vendor", ignoreCase = true)) ProfileKind.VENDOR else ProfileKind.VISITOR,
                    name = r.visitorName,
                    company = r.company,
                    photoKey = r.livePhotoKey,
                    idRef = null,
                    lastHostId = r.lastHostId,
                    lastPurpose = r.lastPurpose,
                    visitorTypeApi = r.lastVisitorType,
                ),
            )
        }
    } catch (e: ApiException) {
        when (e.code.uppercase()) {
            "INVALID_MOBILE" -> LookupOutcome.InvalidNumber
            "BLACKLISTED" -> LookupOutcome.Blocked
            else -> if (e.httpStatus in setOf(401, 403)) throw e else LookupOutcome.Failed(ErrorCopy.LOOKUP_FAILED)
        }
    } catch (e: Exception) {
        LookupOutcome.Failed(ErrorCopy.LOOKUP_FAILED)
    }
}

/** Test/debug fake: scripted answers keyed by ten-digit mobile. Never wired in release code paths. */
class FakeAddVisitorLookup(
    private val answers: Map<String, LookupOutcome> = emptyMap(),
    private val default: LookupOutcome = LookupOutcome.NotFound,
) : AddVisitorLookup {
    val calls = mutableListOf<String>()
    override suspend fun lookup(tenDigitMobile: String): LookupOutcome {
        calls += tenDigitMobile
        return answers[tenDigitMobile] ?: default
    }
}
