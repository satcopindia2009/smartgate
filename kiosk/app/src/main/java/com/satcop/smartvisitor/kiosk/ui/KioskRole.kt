package com.satcop.smartvisitor.kiosk.ui

enum class KioskRole {
    GATE, HOST, GUARD, UNSUPPORTED;
    companion object {
        fun fromJwt(role: String?): KioskRole = when (role?.trim()?.lowercase()) {
            "gate" -> GATE
            "host" -> HOST
            "guard", "security_head" -> GUARD
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
