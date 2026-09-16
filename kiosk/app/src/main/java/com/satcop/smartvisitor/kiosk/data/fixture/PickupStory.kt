package com.satcop.smartvisitor.kiosk.data.fixture

/**
 * Locked P6 pickup story (Hub P1–P6+H1). Mother is Neha Mehta — not Priya Singh / not visitor Priya.
 * Day-1 wired flow lives in ../pickup-gate/. This kiosk mode is an entry point only.
 */
object PickupStory {
    const val WEB_PORT = 8769
    const val WEB_HINT = "cd pickup-gate && python3 -m http.server 8769 → http://127.0.0.1:8769/"

    const val STUDENT_AARAV = "Aarav Mehta"
    const val CLASS_AARAV = "5-B"
    const val COLLECTOR_MOTHER = "Neha Mehta"
    const val COLLECTOR_UNCLE = "Rohan Mehta"
    const val STUDENT_KABIR = "Kabir Singh"
    const val CUSTODY_FLAG = "court_order"

    val gateScreens = listOf(
        "1 Purpose — Student pickup (PickupEvent, no QR by default)",
        "2 Lookup — name / class; F2 disambiguate Aarav Mehta 5-B vs Aarav Patel 3-A",
        "3 List + banner — authorized people + flag + gate_instruction ≤280",
        "4 Relative release — Rohan (Uncle) or Neha (Mother) + photo + consent",
        "5 BlockedNotAuthorized — not on list; SH override + reason only",
        "6 BlockedCustody — Kabir court_order; claimed father / blocked row",
        "7 Override pending — waiting for Security Head",
    )
}
