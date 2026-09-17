package com.satcop.smartvisitor.kiosk.ui

/**
 * Post-login home is chosen from JWT `user.role`.
 * Gate keeps the registration stepper; host opens host-web; other roles stay put.
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

/** Seed host-web (external browser). Not the kiosk registration API. */
object HostWebLinks {
    const val BASE = "https://england-content-resulting-heavily.trycloudflare.com"
    const val PENDING = "$BASE#pending"
    const val AFTER_HOURS = "$BASE#afterhours"
}

fun KioskUiState.homeRole(): KioskRole = KioskRole.fromJwt(meRole)

fun KioskUiState.showsGateRegistration(): Boolean =
    signedIn && homeRole() == KioskRole.GATE

fun KioskUiState.showsHostApprove(): Boolean =
    signedIn && homeRole() == KioskRole.HOST

fun KioskUiState.showsUnsupportedRole(): Boolean =
    signedIn && homeRole() == KioskRole.UNSUPPORTED
