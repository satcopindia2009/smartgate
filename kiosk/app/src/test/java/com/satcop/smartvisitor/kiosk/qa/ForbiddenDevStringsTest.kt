package com.satcop.smartvisitor.kiosk.qa

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Release guard: user-visible development / scaffolding text must never ship.
 * Scans every string literal in src/main Kotlin (fixture data files excluded: they are data, never UI copy)
 * and every res/values string.
 */
class ForbiddenDevStringsTest {
    private val forbidden = listOf(
        "sample", "under shell", "tabs stay", "demo", "fixture", "placeholder", "lorem", "dummy",
        "P-4F21", "not a real government", "coming soon", "TODO", "FIXME", "step 3 ·", "story pass", "mock",
        "compact home", "no long scroll", "Demo host approve", "(stub)", "ID captured (demo)", "Sample parent visit loaded",
    )
    // Literal contexts that are not shown to users (identifiers, API keys, logs).
    private val allowedContains = listOf("placeholder =", "hint")

    private fun root() = File(System.getProperty("user.dir"), "src/main")

    private fun literals(text: String): List<String> =
        Regex("\"((?:[^\"\\\\\\n]|\\\\.)*)\"").findAll(text).map { it.groupValues[1] }.toList()

    @Test fun noDevTextInStringLiterals() {
        val hits = mutableListOf<String>()
        root().walkTopDown().filter { it.isFile && it.extension == "kt" }
            .filterNot { it.path.contains("/data/fixture/") || it.name.endsWith("Fixtures.kt") || it.path.contains("/data/registration/RegistrationDraft") }
            .forEach { f ->
                literals(f.readText()).forEach { lit ->
                    if (lit == "DEMO" || lit == "FIXTURES") { hits += "${f.name}: \"$lit\""; return@forEach }
                    // Identifier-like tokens (ids, keys, api values) are not copy shown to users.
                    if (lit.length < 3 || lit.none { it == ' ' }) return@forEach
                    forbidden.forEach { bad ->
                        if (lit.contains(bad, ignoreCase = true)) hits += "${f.name}: \"$lit\" ~ $bad"
                    }
                }
            }
        assertTrue("Dev text in user-visible literals:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun noDevTextInResourceStrings() {
        val hits = mutableListOf<String>()
        File(root(), "res").walkTopDown().filter { it.isFile && it.extension == "xml" && it.path.contains("/values") }.forEach { f ->
            Regex("<string[^>]*>([^<]*)</string>").findAll(f.readText()).forEach { m ->
                forbidden.forEach { bad -> if (m.groupValues[1].contains(bad, ignoreCase = true)) hits += "${f.name}: ${m.groupValues[1]} ~ $bad" }
            }
        }
        assertTrue("Dev text in res strings:\n" + hits.joinToString("\n"), hits.isEmpty())
    }

    @Test fun noFixtureAssetsInMain() {
        val fx = File(root(), "assets/fixtures")
        assertTrue("assets/fixtures must not ship in main (tests use src/test/resources)", !fx.exists() || fx.listFiles().isNullOrEmpty())
    }

    @Test fun noWatermarkOrSourcePillComposable() {
        val all = root().walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertTrue(!all.contains("fun DemoWatermark") && !all.contains("fun SourcePill"))
    }
}
