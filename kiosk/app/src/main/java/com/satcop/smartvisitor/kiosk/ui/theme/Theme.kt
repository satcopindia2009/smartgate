package com.satcop.smartvisitor.kiosk.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

val RadiusSm = 6.dp
val RadiusMd = 10.dp
val RadiusLg = 14.dp
val RadiusXl = 16.dp

private val scheme = darkColorScheme(
    primary = KioskColors.purple,
    onPrimary = KioskColors.text,
    secondary = KioskColors.cyan,
    onSecondary = KioskColors.bg,
    background = KioskColors.bg,
    onBackground = KioskColors.text,
    surface = KioskColors.card,
    onSurface = KioskColors.text,
    surfaceVariant = KioskColors.cardHover,
    outline = KioskColors.border,
    error = KioskColors.red,
)

@Composable
fun SatcopKioskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = KioskTypography,
        content = content,
    )
}

val CardShape = RoundedCornerShape(RadiusXl)
val ControlShape = RoundedCornerShape(RadiusSm)
val ChipShape = RoundedCornerShape(50)
