package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable

/** GET /v1/visitors/lookup-by-mobile */
@Serializable
data class VisitorLookupResponse(
    val found: Boolean = false,
    val visitorName: String? = null,
    val livePhotoUrl: String? = null,
    val livePhotoKey: String? = null,
    val company: String? = null,
    val lastPurpose: String? = null,
    val lastVisitorType: String? = null,
    val lastVisitId: String? = null,
    val lastHostId: String? = null,
    val blacklistHit: Boolean = false,
    val blacklistSeverity: String? = null,
    val blacklistReason: String? = null,
    val meta: Meta? = null,
) {
    fun toPrefill(mobile: String): VisitorPrefill? {
        if (!found && visitorName.isNullOrBlank()) return null
        return VisitorPrefill(
            mobile = mobile,
            visitorName = visitorName,
            company = company,
            photoKey = livePhotoKey,
            photoUrl = livePhotoUrl,
            lastPurpose = lastPurpose,
            lastVisitorType = lastVisitorType,
            lastHostId = lastHostId,
            lastVisitId = lastVisitId,
            blacklisted = blacklistHit,
            blacklistSeverity = blacklistSeverity,
            blacklistReason = blacklistReason,
        )
    }
}

@Serializable
data class VisitorPrefill(
    val mobile: String,
    val visitorName: String? = null,
    val company: String? = null,
    val photoKey: String? = null,
    val photoUrl: String? = null,
    val lastPurpose: String? = null,
    val lastVisitorType: String? = null,
    val lastHostId: String? = null,
    val lastVisitId: String? = null,
    val blacklisted: Boolean = false,
    val blacklistSeverity: String? = null,
    val blacklistReason: String? = null,
)

@Serializable
data class GateHistoryRow(
    val type: String? = null,
    val kind: String? = null,
    val id: String,
    val status: String? = null,
    val gateId: String? = null,
    val timestamp: String? = null,
    val occurredAt: String? = null,
    val visitorName: String? = null,
    val title: String? = null,
    val subtitle: String? = null,
    val mobile: String? = null,
    val company: String? = null,
    val courierCompany: String? = null,
    val recipientName: String? = null,
    val guardUserId: String? = null,
    val relatedId: String? = null,
    val notes: String? = null,
    val photoKey: String? = null,
) {
    fun asEvent(): GuardHistoryEvent {
        val kindVal = (type ?: kind ?: "visitor").lowercase()
        val whenAt = timestamp ?: occurredAt ?: ""
        val displayTitle = title
            ?: visitorName
            ?: when {
                kindVal == "courier" -> (courierCompany.orEmpty() + " → " + recipientName.orEmpty()).trim()
                kindVal == "checkout" -> "Checkout · " + (visitorName ?: id)
                else -> visitorName ?: id
            }
        val displaySub = subtitle ?: when (kindVal) {
            "courier" -> listOfNotNull(status, courierCompany).joinToString(" · ")
            else -> listOfNotNull(status, company).joinToString(" · ")
        }
        return GuardHistoryEvent(
            id = id,
            kind = kindVal,
            title = displayTitle.ifBlank { id },
            subtitle = displaySub.ifBlank { null },
            status = status ?: "",
            gateId = gateId,
            mobile = mobile,
            company = company ?: courierCompany,
            photoKey = photoKey,
            occurredAt = whenAt,
            relatedId = relatedId ?: id,
            notes = notes,
            recipientName = recipientName,
            guardUserId = guardUserId,
        )
    }
}

@Serializable
data class GuardHistoryEvent(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String? = null,
    val status: String,
    val gateId: String? = null,
    val mobile: String? = null,
    val company: String? = null,
    val photoKey: String? = null,
    val occurredAt: String,
    val relatedId: String? = null,
    val notes: String? = null,
    val recipientName: String? = null,
    val guardUserId: String? = null,
)

@Serializable
data class GateHistoryListResponse(
    val data: List<GateHistoryRow> = emptyList(),
    val meta: Meta? = null,
)

@Serializable
data class CourierEvent(
    val id: String,
    val schoolId: String? = null,
    val gateId: String,
    val courierCompany: String,
    val courierPersonName: String? = null,
    val courierMobile: String? = null,
    val recipientName: String,
    val recipientDeptOrHost: String? = null,
    val packageNote: String? = null,
    val photoKey: String? = null,
    val status: String,
    val receivedAt: String,
    val handedOverAt: String? = null,
    val guardUserId: String? = null,
    val photoUrl: String? = null,
    val meta: Meta? = null,
)

@Serializable
data class CourierCreate(
    val gateId: String,
    val courierCompany: String,
    val courierPersonName: String? = null,
    val courierMobile: String? = null,
    val recipientName: String,
    val recipientDeptOrHost: String? = null,
    val packageNote: String? = null,
    val photoKey: String? = null,
)

@Serializable
data class CourierListResponse(
    val data: List<CourierEvent> = emptyList(),
    val meta: Meta? = null,
)

object CourierStatus {
    const val RECEIVED = "Received"
    const val HANDED_OVER = "HandedOver"
    const val RETURNED = "Returned"
}

@Serializable
data class LostFoundItem(
    val id: String,
    val schoolId: String? = null,
    val gateId: String? = null,
    val description: String,
    val locationFound: String,
    val foundAt: String,
    val finderName: String,
    val finderMobile: String? = null,
    val status: String = "Open",
    val notes: String? = null,
    val photoKey: String? = null,
    val createdAt: String? = null,
    val meta: Meta? = null,
)

@Serializable
data class LostFoundCreate(
    val description: String,
    val locationFound: String,
    val foundAt: String,
    val finderName: String,
    val finderMobile: String? = null,
    val gateId: String? = null,
    val notes: String? = null,
    val photoKey: String? = null,
)

object HistoryKind {
    const val VISITOR = "visitor"
    const val VENDOR = "vendor"
    const val COURIER = "courier"
    const val CHECKOUT = "checkout"
    const val PICKUP = "pickup"
    const val LOST_FOUND = "lost_found"
}

object HistoryFilter {
    val TYPES = listOf("all", HistoryKind.VISITOR, HistoryKind.VENDOR, HistoryKind.COURIER, HistoryKind.CHECKOUT)
    val STATUSES = listOf("all", "pending", "approved", "inside", "completed", "Received", "HandedOver", "Returned", "Open")
}
