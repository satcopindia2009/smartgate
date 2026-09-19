package com.satcop.smartvisitor.kiosk.guardpatrol.data

/**
 * In-memory fixture store for PatrolIncident when living POST /patrol-incidents
 * (or /rounds/{id}/incidents) is 404. Never throws into Start/Scan/End flows.
 */
object LocalPatrolIncidentStore {
    private val lock = Any()
    private val items = mutableListOf<PatrolIncident>()

    fun add(incident: PatrolIncident): PatrolIncident = synchronized(lock) {
        items.add(0, incident)
        incident
    }

    fun all(): List<PatrolIncident> = synchronized(lock) { items.toList() }

    fun forRound(roundId: String): List<PatrolIncident> =
        synchronized(lock) { items.filter { it.roundId == roundId } }

    fun clear() = synchronized(lock) { items.clear() }

    fun size(): Int = synchronized(lock) { items.size }

    fun newId(): String = "inc-local-${System.currentTimeMillis()}-${size() + 1}"
}
