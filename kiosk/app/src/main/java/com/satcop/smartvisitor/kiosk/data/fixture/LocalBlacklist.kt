package com.satcop.smartvisitor.kiosk.data.fixture

import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.registration.MobileIndia

/** Fixture-side copy of contract §5 hard-match (mobile OR idType+idNumber). */
object LocalBlacklist {
    val entries: List<BlacklistEntry> = listOf(
        BlacklistEntry(
            id = "BL-01",
            schoolId = DemoFixtures.SCHOOL_ID,
            name = "Vikram More",
            mobile = "9876500001",
            idType = "Aadhaar",
            idNumber = "XXXX-XXXX-3321",
            reason = "Prior incident — unauthorized campus entry",
            severity = "Block",
            notes = "Do not issue pass without SH override",
        ),
        BlacklistEntry(
            id = "BL-02",
            schoolId = DemoFixtures.SCHOOL_ID,
            name = "Neha Salunkhe",
            mobile = "9876500002",
            idType = "DL",
            idNumber = "MH12-XXXX-8890",
            reason = "Repeat after-hours vendor; watch only",
            severity = "Alert",
            notes = "Alert banner + gate ack",
        ),
    )

    fun match(mobile: String?, idType: String?, idNumber: String?): BlacklistEntry? {
        val ten = mobile?.let { MobileIndia.tenDigit(it) }
        val nid = normalizeId(idNumber)
        return entries.firstOrNull { entry ->
            val em = entry.mobile?.let { MobileIndia.tenDigit(it) }
            val mobileHit = ten != null && em != null && ten == em
            val idHit = !idType.isNullOrBlank() &&
                nid != null &&
                entry.idType == idType &&
                normalizeId(entry.idNumber) == nid
            mobileHit || idHit
        }
    }

    private fun normalizeId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return raw.filter { it.isLetterOrDigit() }.uppercase().ifBlank { null }
    }
}
