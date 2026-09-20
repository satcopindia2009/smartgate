package com.satcop.smartvisitor.kiosk.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * Apple HIG LIGHT + DARK paint tokens (TOKENS-FOR-MOBILE.md / Hub GO LIVE).
 * Soft Blue / Orange SoT parked — do not ship.
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
)

val AppleLight = ApplePalette(
    bg = Color(0xFFF2F2F7),
    card = Color(0xFFFFFFFF),
    secondaryFill = Color(0xFFE5E5EA),
    label = Color(0xFF000000),
    secondaryLabel = Color(0xFF8E8E93),
    separator = Color(0x4A3C3C43),
    systemBlue = Color(0xFF007AFF),
    systemGreen = Color(0xFF34C759),
    systemRed = Color(0xFFFF3B30),
    systemOrange = Color(0xFFFF9500),
    systemPurple = Color(0xFFAF52DE),
    tabBarBg = Color(0xFFF9F9F9),
    searchFill = Color(0xFFE3E3E8),
    isDark = false,
)

val AppleDark = ApplePalette(
    bg = Color(0xFF000000),
    card = Color(0xFF1C1C1E),
    secondaryFill = Color(0xFF2C2C2E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0xFF8E8E93),
    separator = Color(0xA6545458),
    systemBlue = Color(0xFF0A84FF),
    systemGreen = Color(0xFF30D158),
    systemRed = Color(0xFFFF453A),
    systemOrange = Color(0xFFFF9F0A),
    systemPurple = Color(0xFFBF5AF2),
    tabBarBg = Color(0xFF1C1C1E),
    searchFill = Color(0xFF2C2C2E),
    isDark = true,
)

/** Marker string retained in DEX for Hub verify (Appearance / Apple tokens). */
const val APPLE_THEME_MARKER = "AppearanceMode-Apple-LD-1055-bottomnav"

/**
 * Reactive palette holder so existing `KioskColors.x` call sites recompose on L/D switch.
 * Active palette set by [SatcopKioskTheme] from AppearanceMode.
 */
object AppleThemeState {
    var palette by mutableStateOf(AppleDark)
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
}
