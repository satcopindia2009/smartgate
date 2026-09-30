package com.satcop.smartvisitor.kiosk

import androidx.compose.ui.graphics.Color
import com.satcop.smartvisitor.kiosk.ui.theme.AppleDark
import com.satcop.smartvisitor.kiosk.ui.theme.AppleLight
import com.satcop.smartvisitor.kiosk.ui.theme.ApplePalette
import com.satcop.smartvisitor.kiosk.ui.theme.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1059 bug: typed text BLACK on BLACK. Every input token pair must be >= 4.5:1 in both modes. */
class InputContrastTest {
    private val modes: List<Pair<String, ApplePalette>> = listOf("light" to AppleLight, "dark" to AppleDark)

    private fun check(name: String, fg: Color, bg: Color, min: Double) {
        val r = Contrast.ratio(fg, bg)
        assertTrue("$name contrast $r < $min", r >= min)
    }

    @Test
    fun helperMatchesWcagReferenceValues() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, Contrast.ratio(Color.Black, Color.Black), 0.001)
        // the 1059 bug: black text on the Dark field
        assertTrue(Contrast.ratio(Color.Black, AppleDark.inputBg) < 1.5)
    }

    @Test
    fun inputTextOnFieldBackgroundIsReadableInEveryMode() {
        for ((n, p) in modes) check("$n inputText/inputBg", p.inputText, p.inputBg, 7.0)
    }

    @Test
    fun inputTextOnCardAndSearchFillIsReadable() {
        for ((n, p) in modes) {
            check("$n inputText/card", p.inputText, p.card, 7.0)
            check("$n inputText/searchFill", p.inputText, p.searchFill, 7.0)
            check("$n label/bg", p.label, p.bg, 7.0)
        }
    }

    @Test
    fun hintLabelAndErrorAreReadable() {
        for ((n, p) in modes) {
            check("$n hint", p.inputHint, p.inputBg, 4.5)
            check("$n hint/card", p.inputHint, p.card, 4.5)
            check("$n label", p.inputLabel, p.inputBg, 4.5)
            check("$n error", p.errorText, p.inputBg, 4.5)
            check("$n error/card", p.errorText, p.card, 4.5)
            check("$n cursor", p.inputCursor, p.inputBg, 3.0)
            check("$n border", p.inputBorder, p.inputBg, 1.2)
        }
    }

    @Test
    fun textIsNeverTheSameColourAsItsBackground() {
        for ((n, p) in modes) {
            assertTrue(n, p.inputText != p.inputBg)
            assertTrue(n, p.inputText != p.card)
            assertTrue(n, p.inputHint != p.inputBg)
        }
    }

    @Test
    fun lightAndDarkAreOpposites() {
        assertTrue(Contrast.luminance(AppleLight.inputBg) > Contrast.luminance(AppleLight.inputText))
        assertTrue(Contrast.luminance(AppleDark.inputBg) < Contrast.luminance(AppleDark.inputText))
    }
}
