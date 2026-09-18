package com.satcop.smartvisitor.kiosk.guardpatrol.data

/**
 * Scan + end-round rules from desk AC-GP1 / AC-GP1b / AC-GP7 and demos/guard-patrol/app.js.
 */
object GuardPatrolEngine {
    const val DEDUPE_MS = 2 * 60 * 1000L

    fun startRound(
        template: RoundTemplate,
        guardId: String = GuardPatrolFixtures.DEFAULT_GUARD_ID,
        nowMs: Long = System.currentTimeMillis(),
    ): RoundInstance {
        require(template.active) { "Template inactive" }
        return RoundInstance(
            id = "rnd-live-$nowMs",
            schoolId = template.schoolId,
            templateId = template.id,
            guardId = guardId,
            startedAtEpochMs = nowMs,
            status = RoundStatus.IN_PROGRESS,
            scans = emptyList(),
        )
    }

    fun simulateScan(
        round: RoundInstance,
        template: RoundTemplate,
        checkpointId: String,
        nowMs: Long = System.currentTimeMillis(),
        offCampusSuspect: Boolean = false,
        deviceId: String = GuardPatrolFixtures.DEVICE_ID,
        checkpointLookup: (String) -> Checkpoint? = { GuardPatrolFixtures.checkpoint(it) },
    ): Pair<RoundInstance, ScanResult> {
        val cp = checkpointLookup(checkpointId)
        if (cp == null || !cp.active) {
            return round to ScanResult.Rejected(
                reason = "inactive",
                message = "Inactive / unknown tag — not counted",
            )
        }
        if (checkpointId !in template.checkpointIds) {
            return round to ScanResult.Rejected(
                reason = "unknown",
                message = "Unknown tag for this round — not counted",
            )
        }

        val recent = round.scans.any { scan ->
            scan.checkpointId == checkpointId && (nowMs - scan.scannedAtEpochMs) < DEDUPE_MS
        }
        if (recent) {
            return round to ScanResult.Rejected(
                reason = "dedupe",
                message = "Already scanned — ignored (2-min dedupe)",
            )
        }

        var outOfOrder = false
        if (template.ordered) {
            val scannedSet = round.scans.map { it.checkpointId }.toSet()
            val nextExpected = template.checkpointIds.firstOrNull { it !in scannedSet }
            if (nextExpected != null && checkpointId != nextExpected) {
                val expectIdx = template.checkpointIds.indexOf(nextExpected)
                val scanIdx = template.checkpointIds.indexOf(checkpointId)
                if (scanIdx > expectIdx) outOfOrder = true
            }
        }

        val scan = Scan(
            checkpointId = checkpointId,
            scannedAtEpochMs = nowMs,
            deviceId = deviceId,
            offCampusSuspect = offCampusSuspect,
            outOfOrder = outOfOrder,
        )
        val updated = round.copy(scans = round.scans + scan)
        val message = if (outOfOrder) {
            "Out of sequence — scan recorded"
        } else {
            "Checkpoint scanned"
        }
        return updated to ScanResult.Accepted(scan = scan, outOfOrder = outOfOrder, message = message)
    }

    fun uniqueScannedCount(round: RoundInstance): Int =
        round.scans.map { it.checkpointId }.toSet().size

    fun uniqueScannedIds(round: RoundInstance): Set<String> =
        round.scans.map { it.checkpointId }.toSet()

    fun nextExpectedId(round: RoundInstance, template: RoundTemplate): String? {
        if (!template.ordered) return null
        val scanned = uniqueScannedIds(round)
        return template.checkpointIds.firstOrNull { it !in scanned }
    }

    /** AC-GP7 / F2 status after End (or duration elapsed treated as ended). */
    fun resolveStatus(round: RoundInstance, template: RoundTemplate, ended: Boolean): RoundStatus {
        val count = uniqueScannedCount(round)
        val total = template.checkpointIds.size
        if (count >= total) return RoundStatus.COMPLETED
        if (ended) {
            return if (count == 0) RoundStatus.MISSED else RoundStatus.PARTIAL
        }
        return RoundStatus.IN_PROGRESS
    }

    fun endRound(
        round: RoundInstance,
        template: RoundTemplate,
        nowMs: Long = System.currentTimeMillis(),
    ): RoundInstance {
        val status = resolveStatus(round, template, ended = true)
        return round.copy(
            completedAtEpochMs = nowMs,
            status = status,
        )
    }
}
