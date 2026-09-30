package com.satcop.smartvisitor.kiosk.ui.notify

import com.satcop.smartvisitor.kiosk.data.model.HostFeedItem
import com.satcop.smartvisitor.kiosk.data.store.StringStore

/**
 * Dedupe + cursor for the host new-visitor feed (Backend contract 2026-09-30).
 *
 * - dedupes by notification id (in-memory set, plus persisted `lastId` cursor sent as `since`)
 * - only `visit.pending` items that still offer "approve" produce an alert
 * - first-ever run for a host (no stored cursor): only items younger than [freshWindowMs] alert,
 *   so a stale unread backlog does not spam the popup (they still appear in the Inbox list)
 */
class HostFeedTracker(
    private val store: StringStore,
    private val freshWindowMs: Long = 10 * 60_000L,
) {
    private val seen = LinkedHashSet<String>()

    private fun key(hostKey: String) = "last_id_$hostKey"

    fun lastId(hostKey: String): String? = store.get(key(hostKey))?.takeIf { it.isNotBlank() }

    /**
     * @param items newest-first as returned by the API
     * @return alerts to show, oldest first
     */
    fun ingest(
        hostKey: String,
        items: List<HostFeedItem>,
        metaLastId: String?,
        nowMs: Long = System.currentTimeMillis(),
        parseTime: (String?) -> Long? = { null },
    ): List<HostFeedItem> {
        val hadCursor = lastId(hostKey) != null
        val fresh = items.filter { seen.add(it.id) }
        val alerts = fresh
            .filter { it.isPendingVisit && (it.actions.isEmpty() || "approve" in it.actions) }
            .filter { hadCursor || isFresh(it, nowMs, parseTime) }
            .reversed()
        val newCursor = metaLastId ?: items.firstOrNull()?.id
        if (newCursor != null && newCursor != lastId(hostKey)) store.put(key(hostKey), newCursor)
        return alerts
    }

    private fun isFresh(item: HostFeedItem, nowMs: Long, parseTime: (String?) -> Long?): Boolean {
        val t = parseTime(item.createdAt) ?: return true
        return nowMs - t <= freshWindowMs
    }
}

/** Poll cadence: 3 s foreground, slower when backgrounded, exponential back-off on errors, reset on success. */
class PollBackoff(
    private val foregroundMs: Long = 3_000L,
    private val backgroundMs: Long = 20_000L,
    private val maxMs: Long = 30_000L,
) {
    private var failures = 0

    fun onSuccess() { failures = 0 }
    fun onFailure() { if (failures < 6) failures++ }

    fun nextDelayMs(foreground: Boolean): Long {
        val base = if (foreground) foregroundMs else backgroundMs
        if (failures == 0) return base
        return (base shl failures).coerceAtMost(maxMs)
    }
}
