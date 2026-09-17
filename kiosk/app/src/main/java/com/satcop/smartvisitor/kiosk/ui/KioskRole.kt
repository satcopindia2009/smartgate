package com.satcop.smartvisitor.kiosk.ui

/**
 * Post-login home is chosen from JWT `user.role`.
 * Gate keeps the registration stepper; host stays on native Host Home.
 */
enum class KioskRole {
    GATE,
    HOST,
    UNSUPPORTED,
    ;

    companion object {
        fun fromJwt(role: String?): KioskRole = when (role?.trim()?.lowercase()) {
            "gate" -> GATE
            "host" -> HOST
            else -> UNSUPPORTED
        }
    }
}

enum class KioskScreen {
    HOME,
    PICKUP,
}

fun KioskUiState.homeRole(): KioskRole = KioskRole.fromJwt(meRole)

fun KioskUiState.showsGateRegistration(): Boolean =
    signedIn && homeRole() == KioskRole.GATE && screen == KioskScreen.HOME

fun KioskUiState.showsHostApprove(): Boolean =
    signedIn && homeRole() == KioskRole.HOST

fun KioskUiState.showsPickup(): Boolean =
    signedIn && homeRole() == KioskRole.GATE && screen == KioskScreen.PICKUP

fun KioskUiState.showsUnsupportedRole(): Boolean =
    signedIn && homeRole() == KioskRole.UNSUPPORTED
