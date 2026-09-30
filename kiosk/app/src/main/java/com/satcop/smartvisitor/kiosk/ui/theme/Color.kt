package com.satcop.smartvisitor.kiosk.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * SmartGate TEAL palette (approved restyle 2026-09-30, tokens.json).
 *
 * The class keeps its historical name and the historical "systemX" field names so every existing
 * `KioskColors.x` / `AppleThemeState.palette.x` call site recolours without edits:
 *   systemBlue -> primary teal, systemGreen -> success, systemRed -> danger,
 *   systemOrange -> warning, systemPurple -> brand teal, searchFill -> inputFill, tabBarBg -> surface.
 * New semantic fields (brand, primary, *Soft, onPrimary...) are the preferred names for new code.
 */
data class ApplePalette(
    val bg: Color,
    val card: Color,
    val secondaryFill: Color,
    val label: Color,
    val secondaryLabel: Color,
    val separator: Color,
    val systemBlue: Color,
    val systemGreen: Color,
    val systemRed: Color,
    val systemOrange: Color,
    val systemPurple: Color,
    val tabBarBg: Color,
    val searchFill: Color,
    val isDark: Boolean,
    // 1059b input tokens — every text field reads ONLY these (contrast-tested in both modes).
    val inputBg: Color,
    val inputText: Color,
    val inputHint: Color,
    val inputLabel: Color,
    val inputBorder: Color,
    val inputCursor: Color,
    val errorText: Color,
    // Teal restyle semantic tokens
    val primary: Color,
    val onPrimary: Color,
    val primaryPressed: Color,
    val brand: Color,
    val brandSoft: Color,
    val surfaceAlt: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val successSoft: Color,
    val warning: Color,
    val warningSoft: Color,
    val info: Color,
    val infoSoft: Color,
    val scrim: Color,
)

val AppleLight = ApplePalette(
    bg = Color(0xFFF5F7F9),
    card = Color(0xFFFFFFFF),
    secondaryFill = Color(0xFFEEF2F1),
    label = Color(0xFF14201D),
    secondaryLabel = Color(0xFF5F6B67),
    separator = Color(0xFFE6ECEA),
    systemBlue = Color(0xFF1E806A),
    systemGreen = Color(0xFF1D7A4F),
    systemRed = Color(0xFFC9403D),
    systemOrange = Color(0xFFB45309),
    systemPurple = Color(0xFF4FB69C),
    tabBarBg = Color(0xFFFFFFFF),
    searchFill = Color(0xFFF1F4F6),
    isDark = false,
    inputBg = Color(0xFFF1F4F6),
    inputText = Color(0xFF14201D),
    inputHint = Color(0xFF5F6B67),
    inputLabel = Color(0xFF5F6B67),
    // tokens.json says #E1E7E5; that is only 1.1:1 on the input fill, InputContrastTest requires >= 1.2.
    inputBorder = Color(0xFFD3DBD8),
    inputCursor = Color(0xFF1E806A),
    // #C9403D is 4.44:1 on the input fill (< 4.5 for small text); slightly deeper red for error TEXT only.
    errorText = Color(0xFFBC3A37),
    primary = Color(0xFF1E806A),
    onPrimary = Color(0xFFFFFFFF),
    primaryPressed = Color(0xFF186A58),
    brand = Color(0xFF4FB69C),
    brandSoft = Color(0xFFE3F4EF),
    surfaceAlt = Color(0xFFEEF2F1),
    danger = Color(0xFFC9403D),
    dangerSoft = Color(0xFFFDECEC),
    success = Color(0xFF1D7A4F),
    successSoft = Color(0xFFE6F5EC),
    warning = Color(0xFFB45309),
    warningSoft = Color(0xFFFEF3E2),
    info = Color(0xFF2563EB),
    infoSoft = Color(0xFFE8F0FE),
    scrim = Color(0x66000000),
)

val AppleDark = ApplePalette(
    bg = Color(0xFF0E1513),
    card = Color(0xFF17211E),
    secondaryFill = Color(0xFF1F2B27),
    label = Color(0xFFF2F6F5),
    secondaryLabel = Color(0xFF9AA8A3),
    separator = Color(0xFF2A3733),
    systemBlue = Color(0xFF4FB69C),
    systemGreen = Color(0xFF5BD68E),
    systemRed = Color(0xFFFF7B78),
    systemOrange = Color(0xFFF5B14C),
    systemPurple = Color(0xFF4FB69C),
    tabBarBg = Color(0xFF17211E),
    searchFill = Color(0xFF1F2B27),
    isDark = true,
    inputBg = Color(0xFF1F2B27),
    inputText = Color(0xFFF2F6F5),
    inputHint = Color(0xFF9AA8A3),
    inputLabel = Color(0xFF9AA8A3),
    inputBorder = Color(0xFF33413D),
    inputCursor = Color(0xFF4FB69C),
    errorText = Color(0xFFFF7B78),
    primary = Color(0xFF4FB69C),
    onPrimary = Color(0xFF062018),
    primaryPressed = Color(0xFF5CC7AC),
    brand = Color(0xFF4FB69C),
    brandSoft = Color(0xFF173029),
    surfaceAlt = Color(0xFF1F2B27),
    danger = Color(0xFFFF7B78),
    dangerSoft = Color(0xFF3A1E1E),
    success = Color(0xFF5BD68E),
    successSoft = Color(0xFF153224),
    warning = Color(0xFFF5B14C),
    warningSoft = Color(0xFF3A2C14),
    info = Color(0xFF7DA8FF),
    infoSoft = Color(0xFF1A2740),
    scrim = Color(0xAA000000),
)

/** Marker string retained in DEX for Hub verify (Appearance / Apple tokens). */
const val APPLE_THEME_MARKER = "AppearanceMode-Apple-LD-1055-bottomnav"

/**
 * Reactive palette holder so existing `KioskColors.x` call sites recompose on L/D switch.
 * Active palette set by [SatcopKioskTheme] from AppearanceMode.
 */
object AppleThemeState {
    var palette by mutableStateOf(AppleLight)
        private set

    fun apply(palette: ApplePalette) {
        this.palette = palette
    }
}

/**
 * Compatibility facade over Apple LIGHT/DARK.
 * Legacy names (purple/cyan/…) map onto Apple system tokens — Soft Blue not used.
 */
object KioskColors {
    val bg: Color get() = AppleThemeState.palette.bg
    val sidebar: Color get() = AppleThemeState.palette.bg
    val card: Color get() = AppleThemeState.palette.card
    val cardHover: Color get() = AppleThemeState.palette.secondaryFill
    val border: Color get() = AppleThemeState.palette.separator
    val borderSubtle: Color get() = AppleThemeState.palette.separator
    val text: Color get() = AppleThemeState.palette.label
    val textMuted: Color get() = AppleThemeState.palette.secondaryLabel
    val textDim: Color get() = AppleThemeState.palette.secondaryLabel
    val purple: Color get() = AppleThemeState.palette.systemBlue
    val purpleBright: Color get() = AppleThemeState.palette.systemBlue
    val purpleDim: Color get() = AppleThemeState.palette.systemBlue.copy(alpha = 0.15f)
    val purpleGlow: Color get() = AppleThemeState.palette.systemBlue.copy(alpha = 0.35f)
    val blue: Color get() = AppleThemeState.palette.systemBlue
    val cyan: Color get() = AppleThemeState.palette.systemBlue
    val cyanBright: Color get() = AppleThemeState.palette.systemBlue
    val cyanDim: Color get() = AppleThemeState.palette.systemBlue.copy(alpha = 0.15f)
    val green: Color get() = AppleThemeState.palette.systemGreen
    val greenBright: Color get() = AppleThemeState.palette.systemGreen
    val greenDim: Color get() = AppleThemeState.palette.systemGreen.copy(alpha = 0.15f)
    val red: Color get() = AppleThemeState.palette.systemRed
    val redDim: Color get() = AppleThemeState.palette.systemRed.copy(alpha = 0.15f)
    val orange: Color get() = AppleThemeState.palette.systemOrange
    val orangeDim: Color get() = AppleThemeState.palette.systemOrange.copy(alpha = 0.15f)
    val peakAmber: Color get() = AppleThemeState.palette.systemOrange
    val peakAmberBright: Color get() = AppleThemeState.palette.systemOrange
    val watermark: Color get() = AppleThemeState.palette.label.copy(alpha = 0.18f)

    // Direct Apple aliases for new shells
    val systemBlue: Color get() = AppleThemeState.palette.systemBlue
    val systemGreen: Color get() = AppleThemeState.palette.systemGreen
    val systemRed: Color get() = AppleThemeState.palette.systemRed
    val systemOrange: Color get() = AppleThemeState.palette.systemOrange
    val systemPurple: Color get() = AppleThemeState.palette.systemPurple
    val secondaryFill: Color get() = AppleThemeState.palette.secondaryFill
    val tabBarBg: Color get() = AppleThemeState.palette.tabBarBg
    val searchFill: Color get() = AppleThemeState.palette.searchFill
    val isDark: Boolean get() = AppleThemeState.palette.isDark

    // 1059b input tokens
    val inputBg: Color get() = AppleThemeState.palette.inputBg
    val inputText: Color get() = AppleThemeState.palette.inputText
    val inputHint: Color get() = AppleThemeState.palette.inputHint
    val inputLabel: Color get() = AppleThemeState.palette.inputLabel
    val inputBorder: Color get() = AppleThemeState.palette.inputBorder
    val inputCursor: Color get() = AppleThemeState.palette.inputCursor
    val errorText: Color get() = AppleThemeState.palette.errorText

    // Teal restyle semantic tokens (preferred names for new code)
    val primary: Color get() = AppleThemeState.palette.primary
    val onPrimary: Color get() = AppleThemeState.palette.onPrimary
    val primaryPressed: Color get() = AppleThemeState.palette.primaryPressed
    val brand: Color get() = AppleThemeState.palette.brand
    val brandSoft: Color get() = AppleThemeState.palette.brandSoft
    val surfaceAlt: Color get() = AppleThemeState.palette.surfaceAlt
    val danger: Color get() = AppleThemeState.palette.danger
    val dangerSoft: Color get() = AppleThemeState.palette.dangerSoft
    val success: Color get() = AppleThemeState.palette.success
    val successSoft: Color get() = AppleThemeState.palette.successSoft
    val warning: Color get() = AppleThemeState.palette.warning
    val warningSoft: Color get() = AppleThemeState.palette.warningSoft
    val info: Color get() = AppleThemeState.palette.info
    val infoSoft: Color get() = AppleThemeState.palette.infoSoft
    val scrim: Color get() = AppleThemeState.palette.scrim

    /** Readable content colour (white or the dark on-primary ink) for a solid [fill]. */
    fun onFill(fill: Color): Color {
        val ink = Color(0xFF062018)
        return if (Contrast.ratio(Color.White, fill) >= Contrast.ratio(ink, fill)) Color.White else ink
    }
}

/** WCAG 2.x contrast helper (pure, unit tested). */
object Contrast {
    private fun lin(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }

    fun luminance(c: Color): Double = 0.2126 * lin(c.red) + 0.7152 * lin(c.green) + 0.0722 * lin(c.blue)

    /** Contrast of [fg] over an opaque [bg]; a translucent fg is composited over bg first. */
    fun ratio(fg: Color, bg: Color): Double {
        val f = if (fg.alpha < 1f) fg.compositeOver(bg) else fg
        val a = luminance(f)
        val b = luminance(bg)
        return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
    }
}
