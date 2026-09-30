package com.satcop.smartvisitor.kiosk.data.addvisitor

import com.satcop.smartvisitor.kiosk.data.api.ErrorCopy
import com.satcop.smartvisitor.kiosk.data.api.LiveVisitorApi
import com.satcop.smartvisitor.kiosk.data.model.ApiException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Add Visitor lookup bound to the Backend contract (2026-09-30): GET /v1/visitors/lookup?mobile=.
 * Server-side only: no cache, no fixture fallback, no blank-form fallback on failure. Values are used as returned
 * (masked for guard/gate); nothing is rebuilt from them.
 */
class ContractLookup(private val api: LiveVisitorApi = LiveVisitorApi()) : AddVisitorLookup {
    override suspend fun lookup(tenDigitMobile: String): LookupOutcome = try {
        map(withContext(Dispatchers.IO) { api.lookupProfile(tenDigitMobile) })
    } catch (e: ApiException) {
        mapError(e)
    } catch (e: Exception) {
        LookupOutcome.Failed(ErrorCopy.LOOKUP_FAILED)
    }

    companion object {
        fun map(r: com.satcop.smartvisitor.kiosk.data.model.ProfileLookupResponse): LookupOutcome {
            if (r.blocked) return LookupOutcome.Blocked
            val active = r.activeVisit
            if (active != null) {
                return LookupOutcome.AlreadyInside(
                    ActiveVisit(
                        visitId = active.id,
                        status = active.status.orEmpty(),
                        sinceLabel = active.since?.let { com.satcop.smartvisitor.kiosk.ui.guardhome.ClockInLogic.time12h(it).takeIf { t -> t != "—" } },
                        gateName = null,
                    ),
                )
            }
            if (!r.found) return LookupOutcome.NotFound
            val kind = if ((r.kind ?: r.profileType).equals("vendor", true)) ProfileKind.VENDOR else ProfileKind.VISITOR
            val idRef = r.idReference?.takeIf { r.idOnFile && it.isNotBlank() }?.let { ref ->
                val type = r.idType?.takeIf { it.isNotBlank() } ?: ref.substringBefore(' ')
                SavedIdRef(type, ref.takeLast(4))
            } ?: if (r.idOnFile && !r.idNumberMasked.isNullOrBlank()) SavedIdRef(r.idType.orEmpty().ifBlank { "ID" }, r.idNumberMasked.takeLast(4)) else null
            val todayNote = when {
                r.rejectedToday != null -> "Rejected earlier today" + (r.rejectedToday.rejectReason?.takeIf { it.isNotBlank() }?.let { ": $it" } ?: "")
                r.approvedToday != null -> "Approved earlier today"
                else -> null
            }
            return LookupOutcome.Found(
                KnownProfile(
                    kind = kind,
                    name = r.name,
                    company = r.company,
                    photoKey = r.photoKey,
                    idRef = idRef,
                    lastHostId = r.lastHostId,
                    lastPurpose = r.lastPurpose,
                    visitorTypeApi = null,
                    profileId = r.profileId,
                    photoUrl = r.photoUrl,
                    lastHostName = r.lastHostName,
                    alertMessage = if (r.alert) (r.alertMessage?.takeIf { it.isNotBlank() } ?: AddVisitorLogic.ALERT_DEFAULT) else null,
                    todayNote = todayNote,
                ),
            )
        }

        fun mapError(e: ApiException): LookupOutcome = when (e.code.uppercase()) {
            "INVALID_MOBILE" -> LookupOutcome.InvalidNumber
            "BLACKLISTED" -> LookupOutcome.Blocked
            "RATE_LIMITED" -> LookupOutcome.Failed(ErrorCopy.RATE_LIMITED)
            else -> if (e.httpStatus in setOf(401, 403)) throw e else LookupOutcome.Failed(ErrorCopy.LOOKUP_FAILED)
        }
    }
}
