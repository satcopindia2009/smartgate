package com.satcop.smartvisitor.kiosk.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val RadiusSm = 6.dp
val RadiusMd = 10.dp
val RadiusLg = 12.dp
val RadiusXl = 14.dp

val LocalAppearanceMode = staticCompositionLocalOf { AppearanceMode.AUTO }
val LocalSetAppearanceMode = staticCompositionLocalOf<(AppearanceMode) -> Unit> { {} }

/** Palette for the persisted mode, usable before composition (avoids a wrong-colour first frame). */
fun initialPalette(context: android.content.Context): ApplePalette {
    val systemDark = (context.resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    return when (AppearancePrefs.load(context)) {
        AppearanceMode.LIGHT -> AppleLight
        AppearanceMode.DARK -> AppleDark
        AppearanceMode.AUTO -> if (systemDark) AppleDark else AppleLight
    }
}

@Composable
fun SatcopKioskTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AppearancePrefs.load(context)) }
    val systemDark = isSystemInDarkTheme()
    val dark = when (mode) {
        AppearanceMode.LIGHT -> false
        AppearanceMode.DARK -> true
        AppearanceMode.AUTO -> systemDark
    }
    val palette = if (dark) AppleDark else AppleLight
    // Keep facade + Material in sync; marker forces DEX retain.
    SideEffect {
        AppleThemeState.apply(palette)
        @Suppress("UNUSED_VARIABLE")
        val retain = APPLE_THEME_MARKER
    }

    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.systemBlue,
            onPrimary = ColorWhite,
            secondary = palette.systemBlue,
            onSecondary = ColorWhite,
            background = palette.bg,
            onBackground = palette.label,
            surface = palette.card,
            onSurface = palette.label,
            surfaceVariant = palette.secondaryFill,
            outline = palette.separator,
            error = palette.systemRed,
        )
    } else {
        lightColorScheme(
            primary = palette.systemBlue,
            onPrimary = ColorWhite,
            secondary = palette.systemBlue,
            onSecondary = ColorWhite,
            background = palette.bg,
            onBackground = palette.label,
            surface = palette.card,
            onSurface = palette.label,
            surfaceVariant = palette.secondaryFill,
            outline = palette.separator,
            error = palette.systemRed,
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            window.statusBarColor = palette.bg.toArgb()
            window.navigationBarColor = palette.tabBarBg.toArgb()
            // Window background follows the CHOSEN mode (not the system one) so insets never show dark in Light.
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(palette.bg.toArgb()))
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !dark
            controller.isAppearanceLightNavigationBars = !dark
        }
    }

    CompositionLocalProvider(
        LocalAppearanceMode provides mode,
        LocalSetAppearanceMode provides { next ->
            mode = next
            AppearancePrefs.save(context, next)
        },
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = KioskTypography,
            content = content,
        )
    }
}

private val ColorWhite = androidx.compose.ui.graphics.Color.White

val CardShape = RoundedCornerShape(RadiusXl)
val ControlShape = RoundedCornerShape(RadiusSm)
val ChipShape = RoundedCornerShape(50)
val InsetShape = RoundedCornerShape(RadiusMd)
