package com.satcop.smartvisitor.kiosk.checkin

import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormTokensTest {
    private fun src(rel: String) =
        File("src/main/java/com/satcop/smartvisitor/kiosk/ui/$rel").readText()

    private val stepFiles = listOf(
        "steps/VisitorTypeStep.kt",
        "steps/VisitorDetailsStep.kt",
        "addvisitor/AddVisitorScreens.kt",
        "steps/HostDropdown.kt",
    )

    @Test fun tokenValues() {
        assertEquals(16f, FormTokens.ScreenHPad.value, 0f)
        assertEquals(6f, FormTokens.LabelToField.value, 0f)
        assertEquals(12f, FormTokens.FieldToField.value, 0f)
        assertEquals(48f, FormTokens.MinTouch.value, 0f)
        assertTrue(FormTokens.allSpacing.all { it.value > 0f })
        // label hugs its input more than fields are separated from each other
        assertTrue(FormTokens.LabelToField < FormTokens.FieldToField)
        assertTrue(FormTokens.FieldToField < FormTokens.SectionGap)
    }

    @Test fun shellChromeMarginMatchesScreenPad() {
        // Apple shell nav/title/sub use 16.dp horizontally; forms must share that exact edge.
        val chrome = src("apple/AppleChrome.kt")
        assertTrue(chrome.contains("padding(horizontal = 16.dp)"))
        assertEquals(16f, FormTokens.ScreenHPad.value, 0f)
    }

    @Test fun stepsNeverAddOwnHorizontalScreenPadding() {
        // Header/labels/inputs/host share the container's single ScreenHPad -> no ad-hoc horizontal padding of 16dp+.
        for (f in stepFiles) {
            val t = src(f)
            val bad = Regex("""padding\(horizontal\s*=\s*(1[6-9]|[2-9]\d)\.dp""").findAll(t)
                .filter { !t.substring((it.range.first - 60).coerceAtLeast(0), it.range.first).contains("vertical") }
                .toList()
            // inner chip/button paddings (14/12 dp) are fine; nothing >= 16 except pill/button internals (24)
            for (m in bad) {
                val ctx = t.substring((m.range.first - 200).coerceAtLeast(0), m.range.last + 40)
                assertTrue("$f has screen-level horizontal padding: $ctx",
                    ctx.contains("clickable") || ctx.contains("clip(") || ctx.contains("border("))
            }
        }
    }

    @Test fun containerAppliesScreenPadOnceForGateRegistration() {
        val app = src("KioskApp.kt")
        assertTrue(app.contains("padding(horizontal = FormTokens.ScreenHPad)"))
        assertTrue(app.contains("val cardHPad = FormTokens.ScreenHPad"))
    }

    @Test fun detailsStepUsesSharedHeaderAndFieldGap() {
        val t = src("steps/VisitorDetailsStep.kt")
        assertTrue(t.contains("FormHeader("))
        assertTrue(t.contains("FormFields {"))
        assertFalse("ad-hoc top paddings removed", Regex("""padding\(top\s*=\s*8\.dp""").containsMatchIn(t))
        assertFalse(t.contains("HostGrid"))
        assertFalse(t.contains("HostCard"))
        assertTrue(t.contains("HostDropdown("))
    }

    @Test fun hostDropdownKeepsLabelAndUsesMinHeightOnly() {
        val t = src("steps/HostDropdown.kt")
        assertTrue(t.contains("HostPicker.LABEL"))
        assertTrue(t.contains("ExposedDropdownMenuBox"))
        assertTrue(t.contains("heightIn(min = FormTokens.MinTouch)"))
        assertFalse("no fixed heights", Regex("""\.height\(\d+\.dp\)""").containsMatchIn(t))
    }

    @Test fun textFieldsUseMinHeightAndTokenLabelGap() {
        val t = src("components/KioskComponents.kt")
        val field = t.substring(t.indexOf("fun KioskField("), t.indexOf("fun KioskField(") + 6000)
        assertTrue(field.contains("heightIn(min = FormTokens.MinTouch)"))
        assertTrue(field.contains("padding(bottom = FormTokens.LabelToField)"))
        assertTrue(field.contains("FormTokens.ErrorGap"))
    }

    @Test fun stepsHaveNoFixedHeightOnTextOrInputs() {
        for (f in stepFiles) {
            val t = src(f)
            // Only spacers (<=12dp), dividers and the 72dp image preview may have a fixed .height(...)
            Regex("""\.height\((\d+)\.dp\)""").findAll(t).forEach {
                val n = it.groupValues[1].toInt()
                assertTrue("$f fixed height ${n}dp", n <= 12 || n == 72)
            }
        }
    }

    @Test fun versionBumped() {
        val g = File("build.gradle.kts").readText()
        assertTrue(g.contains("versionCode = 1066"))
        assertTrue(g.contains("1.0.14-1role-PREVIEW-TEAL-2"))
    }
}
