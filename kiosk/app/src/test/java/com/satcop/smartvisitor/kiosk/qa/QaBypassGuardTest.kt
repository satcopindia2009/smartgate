package com.satcop.smartvisitor.kiosk.qa

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Release must never contain the QA fake camera / bypass. (Gradle also enforces this on assembleRelease.) */
class QaBypassGuardTest {
    private val forbidden = listOf("DebugQaTestPattern", "QaSwitchReceiver", "debugqa", "FAKE_CAMERA", "QA TEST IMAGE", "TEST CAMERA (QA build)",
        "QaOtpRepository", "QaOtpState", "QA_OTP", "qa-otp-", "qa-reset-token")
    private fun src(rel: String) = File(System.getProperty("user.dir"), rel)

    @Test fun mainAndReleaseSourcesHaveNoQaSymbols() {
        for (root in listOf("src/main", "src/release")) {
            val dir = src(root); assertTrue("$root missing", dir.exists())
            dir.walkTopDown().filter { it.isFile }.forEach { f ->
                val t = f.readText()
                forbidden.forEach { s -> assertFalse("${f.path} contains $s", t.contains(s)) }
            }
        }
    }

    @Test fun releaseAndDebugQaHooksAreConstantFalseNoOps() {
        for (v in listOf("release", "debug")) {
            val t = src("src/$v/java/com/satcop/smartvisitor/kiosk/qa/QaHooks.kt").readText()
            assertTrue(t.contains("const val fakeCamera: Boolean = false"))
            assertTrue(t.contains("fun frame(label: String): Bitmap? = null"))
            assertTrue(t.contains("const val otpEntryPoints: Boolean = false"))
            assertTrue(t.contains("fun otpRepository(): OtpRepository? = null"))
            assertFalse(t.contains("var enabled"))
        }
    }

    @Test fun onlyDebugqaSourceSetHoldsTheRealImplementation() {
        val real = File(System.getProperty("user.dir"), "src").walkTopDown().filter { it.isFile && it.name == "DebugQaTestPattern.kt" }.toList()
        assertTrue(real.size == 1 && real.single().path.contains("src/debugqa/"))
        for (n in listOf("QaOtpRepository.kt", "QaSwitchReceiver.kt")) {
            val f = File(System.getProperty("user.dir"), "src").walkTopDown().filter { it.isFile && it.name == n }.toList()
            assertTrue("$n must exist only in debugqa", f.size == 1 && f.single().path.contains("src/debugqa/"))
        }
        // OTP entry points in main may only be gated by OtpAvailability (constant-false in release unless CONTRACT_BOUND).
        val otpModels = src("src/main/java/com/satcop/smartvisitor/kiosk/data/otp/OtpModels.kt").readText()
        assertTrue(otpModels.contains("const val CONTRACT_BOUND: Boolean = true")) // OTP contract 2026-09-30 is bound
        // The mock code is never in the app: no literal in main sources.
        val mainText = File(System.getProperty("user.dir"), "src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        assertFalse(mainText.contains("123456"))
        val gradle = src("build.gradle.kts").readText()
        assertTrue(gradle.contains("verifyNoQaBypassInReleaseApk"))
        assertTrue(gradle.contains("packageRelease"))
    }

    @Test fun qaVariantHasItsOwnApplicationId() {
        val g = src("build.gradle.kts").readText()
        assertTrue(g.contains("create(\"debugqa\")") && g.contains("applicationIdSuffix = \".debugqa\""))
        assertFalse("release must not use the QA suffix", Regex("release\\s*\\{[^}]*debugqa").containsMatchIn(g))
    }
}
