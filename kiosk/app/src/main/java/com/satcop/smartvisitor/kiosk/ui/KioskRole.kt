package com.satcop.smartvisitor.kiosk.ui

enum class KioskRole {
    GATE, HOST, GUARD, UNSUPPORTED;
    companion object {
        /** Roles that live on the web dashboard only (Product final rulings 4). */
        fun isWebOnly(role: String?): Boolean = role?.trim()?.lowercase() in setOf("admin", "security_head")

        fun fromJwt(role: String?): KioskRole = when (role?.trim()?.lowercase()?.replace('-', '_')) {
            "gate", "gate_staff" -> GATE   // 1076: gate staff use the Gate home
            "host" -> HOST
            "guard", "security" -> GUARD   // 1076: security uses the Guard home (clock-in, patrol)
            else -> UNSUPPORTED
        }
    }
}

enum class KioskScreen {
    HOME, PICKUP, HISTORY, HISTORY_DETAIL, COURIER, CHECKOUT, LOST_FOUND, FACE_LOGIN, GUARD_PATROL,
}

/** 1078: NEW-action buttons of the active area are on unless that area is outside its shift window. */
fun KioskUiState.newActionsEnabled(): Boolean =
    com.satcop.smartvisitor.kiosk.ui.duty.DutyLogic.newActionsEnabled(activeArea, shiftNotices)

fun KioskUiState.homeRole(): KioskRole = KioskRole.fromJwt(meRole)
/** 1077: the Gate desk area is up: by duty (activeArea) when known, else by the old role mapping. */
fun KioskUiState.isGateDesk(): Boolean =
    if (activeArea != null) activeArea == com.satcop.smartvisitor.kiosk.ui.duty.DutyArea.GATE else homeRole() == KioskRole.GATE
fun KioskUiState.showsGateRegistration(): Boolean =
    signedIn && isGateDesk() && screen == KioskScreen.HOME
fun KioskUiState.showsHostApprove(): Boolean = signedIn && homeRole() == KioskRole.HOST
fun KioskUiState.showsGuardPatrol(): Boolean = signedIn && homeRole() == KioskRole.GUARD
fun KioskUiState.showsPickup(): Boolean =
    signedIn && isGateDesk() && screen == KioskScreen.PICKUP
fun KioskUiState.showsUnsupportedRole(): Boolean =
    signedIn && homeRole() == KioskRole.UNSUPPORTED
