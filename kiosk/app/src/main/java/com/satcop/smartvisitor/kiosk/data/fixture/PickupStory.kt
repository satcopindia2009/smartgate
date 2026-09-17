package com.satcop.smartvisitor.kiosk.data.fixture

/**
 * Gate pickup entry copy. Wired Day-1 flow lives in ../pickup-gate/.
 * Default GATE demo is Pranay School Pune. SCH-DEMO-01 Aarav/Kabir stays isolated.
 * Mother / guardian is not Priya Singh. Visitor register is unchanged.
 */
object PickupStory {
    const val WEB_PORT = 8769
    const val WEB_HINT = "cd pickup-gate && python3 -m http.server 8769 → http://127.0.0.1:8769/"

    const val SCHOOL_PRANAY = "Pranay School Pune"
    const val SCHOOL_ID_PRANAY = "SCH-PRANAY-01"
    const val SCHOOL_CODE_PRANAY = "PRANAY"
    const val GATE_USER_PRANAY = "pranay.gate"
    const val GATE_PASS_PRANAY = "PranayGate@2026"
    const val SH_USER_PRANAY = "pranay.sh"
    const val SH_PASS_PRANAY = "PranaySH@2026"

    const val STUDENT_ASHA = "Asha Patil"
    const val CLASS_ASHA = "5-B"
    const val COLLECTOR_PARENT = "Ramesh Patil"
    const val COLLECTOR_GUARDIAN = "Smita Patil"
    const val STUDENT_ROHAN_SHAH = "Rohan Shah"
    const val CUSTODY_FIXTURE = "Dev Joshi"
    const val CUSTODY_FLAG_PRANAY = "restricted"

    const val STUDENT_AARAV = "Aarav Mehta"
    const val CLASS_AARAV = "5-B"
    const val COLLECTOR_MOTHER = "Neha Mehta"
    const val COLLECTOR_UNCLE = "Rohan Mehta"
    const val STUDENT_KABIR = "Kabir Singh"
    const val CUSTODY_FLAG = "court_order"

    val gateScreens = listOf(
        "1 Purpose — Student pickup (PickupEvent, no QR by default)",
        "2 Lookup — Asha Patil 5-B (Pranay) or Aarav on SCH-DEMO-01",
        "3 List + banner — Ramesh (parent) + Smita (guardian); flag + gate_instruction ≤280",
        "4 Happy release — match mobile → live photo stub → consent → Released",
        "5 BlockedNotAuthorized — not on list; SH override + reason only (AC-D1)",
        "6 Custody — Dev Joshi restricted fixture, or demo Kabir court_order (AC-D2)",
        "7 Override pending — waiting for Security Head (AC-D1)",
    )
}
