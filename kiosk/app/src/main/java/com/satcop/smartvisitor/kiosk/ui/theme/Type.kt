package com.satcop.smartvisitor.kiosk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Inter from the demo; system sans-serif is the documented fallback. */
val KioskFont = FontFamily.SansSerif

val KioskTypography = Typography(
    headlineMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = KioskColors.text,
    ),
    titleLarge = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = KioskColors.text,
    ),
    titleMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = KioskColors.text,
    ),
    bodyLarge = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        color = KioskColors.text,
    ),
    bodyMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = KioskColors.text,
    ),
    bodySmall = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = KioskColors.textMuted,
    ),
    labelLarge = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        color = KioskColors.text,
    ),
    labelMedium = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = KioskColors.textMuted,
    ),
    labelSmall = TextStyle(
        fontFamily = KioskFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 10.sp,
        letterSpacing = 0.6.sp,
        color = KioskColors.textDim,
    ),
)
