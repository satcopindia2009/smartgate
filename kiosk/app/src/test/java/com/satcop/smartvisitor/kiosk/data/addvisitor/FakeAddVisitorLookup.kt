package com.satcop.smartvisitor.kiosk.data.addvisitor

/** Test/debug fake: scripted answers keyed by ten-digit mobile. Never wired in release code paths. */
class FakeAddVisitorLookup(
    private val answers: Map<String, LookupOutcome> = emptyMap(),
    private val default: LookupOutcome = LookupOutcome.NotFound,
) : AddVisitorLookup {
    val calls = mutableListOf<String>()
    override suspend fun lookup(tenDigitMobile: String): LookupOutcome {
        calls += tenDigitMobile
        return answers[tenDigitMobile] ?: default
    }
}
