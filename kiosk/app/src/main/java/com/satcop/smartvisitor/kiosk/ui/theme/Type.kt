package com.satcop.smartvisitor.kiosk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 1059b: colours are deliberately NOT baked into these styles. In 1059 they were read once from
 * KioskColors at class-init (= Light palette -> black), and MaterialTheme feeds bodyLarge to every
 * TextField via LocalTextStyle, so typed text stayed black on the dark field. Colour now comes from
 * [SatcopKioskTheme] (LocalContentColor + ProvideTextStyle) and the per-field tokens.
 */
/** Inter from the demo; system sans-serif is the documented fallback. */
val KioskFont = FontFamily.SansSerif

val KioskTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp,
    ),
)
