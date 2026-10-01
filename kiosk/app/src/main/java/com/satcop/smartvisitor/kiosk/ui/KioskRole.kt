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

fun KioskUiState.homeRole(): KioskRole = KioskRole.fromJwt(meRole)
fun KioskUiState.showsGateRegistration(): Boolean =
    signedIn && homeRole() == KioskRole.GATE && screen == KioskScreen.HOME
fun KioskUiState.showsHostApprove(): Boolean = signedIn && homeRole() == KioskRole.HOST
fun KioskUiState.showsGuardPatrol(): Boolean = signedIn && homeRole() == KioskRole.GUARD
fun KioskUiState.showsPickup(): Boolean =
    signedIn && homeRole() == KioskRole.GATE && screen == KioskScreen.PICKUP
fun KioskUiState.showsUnsupportedRole(): Boolean =
    signedIn && homeRole() == KioskRole.UNSUPPORTED
