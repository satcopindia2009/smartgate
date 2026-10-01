package com.satcop.smartvisitor.kiosk.data.model

import kotlinx.serialization.Serializable

/** 1077: compact assignment inside login / me / today / GET /duty/me (Backend frozen contract 2026-10-01). Every field optional. */
@Serializable
data class DutyCompact(
    val id: String? = null,
    val type: String? = null,
    val gateId: String? = null,
    val gateName: String? = null,
    val routeName: String? = null,
    val shiftName: String? = null,
    val shiftStart: String? = null,
    val shiftEnd: String? = null,
    val inForce: Boolean? = null,
    val source: String? = null,
)

@Serializable
data class DutyGate(val id: String? = null, val name: String? = null)

@Serializable
data class DutyPatrol(
    val assignmentIds: List<String> = emptyList(),
    val routeNames: List<String> = emptyList(),
    val count: Int = 0,
)

/** GET /v1/duty/me. All fields optional: absent => the app falls back (DutyLogic). */
@Serializable
data class DutyMe(
    val dutyDate: String? = null,
    val serverTime: String? = null,
    val hasDuty: Boolean? = null,
    val dutyTypes: List<String>? = null,
    val primaryHome: String? = null,
    val homeOrder: List<String>? = null,
    val dutyRevision: String? = null,
    val assignments: List<DutyCompact> = emptyList(),
    val gates: List<DutyGate> = emptyList(),
    val patrol: DutyPatrol? = null,
    val legacyGateIds: List<String>? = null,
)
