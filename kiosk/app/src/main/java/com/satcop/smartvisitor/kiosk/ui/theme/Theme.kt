package com.satcop.smartvisitor.kiosk.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
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

/** Text-field colour sets built ONLY from palette tokens (testable, identical in Light/Dark/System). */
@Composable
fun kioskTextFieldColors(
    container: androidx.compose.ui.graphics.Color = KioskColors.inputBg,
): androidx.compose.material3.TextFieldColors {
    val t = androidx.compose.ui.graphics.Color.Transparent
    return androidx.compose.material3.TextFieldDefaults.colors(
        focusedTextColor = KioskColors.inputText,
        unfocusedTextColor = KioskColors.inputText,
        disabledTextColor = KioskColors.inputHint,
        errorTextColor = KioskColors.inputText,
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        errorContainerColor = container,
        cursorColor = KioskColors.inputCursor,
        errorCursorColor = KioskColors.errorText,
        selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
            handleColor = KioskColors.inputCursor,
            backgroundColor = KioskColors.inputCursor.copy(alpha = 0.3f),
        ),
        focusedIndicatorColor = t,
        unfocusedIndicatorColor = t,
        disabledIndicatorColor = t,
        errorIndicatorColor = t,
        focusedPlaceholderColor = KioskColors.inputHint,
        unfocusedPlaceholderColor = KioskColors.inputHint,
        disabledPlaceholderColor = KioskColors.inputHint,
        errorPlaceholderColor = KioskColors.inputHint,
        focusedLabelColor = KioskColors.inputLabel,
        unfocusedLabelColor = KioskColors.inputLabel,
        errorLabelColor = KioskColors.errorText,
        focusedTrailingIconColor = KioskColors.inputLabel,
        unfocusedTrailingIconColor = KioskColors.inputLabel,
        errorTrailingIconColor = KioskColors.errorText,
    )
}

@Composable
fun kioskOutlinedFieldColors(
    container: androidx.compose.ui.graphics.Color = KioskColors.inputBg,
): androidx.compose.material3.TextFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = KioskColors.inputText,
    unfocusedTextColor = KioskColors.inputText,
    disabledTextColor = KioskColors.inputHint,
    errorTextColor = KioskColors.inputText,
    focusedContainerColor = container,
    unfocusedContainerColor = container,
    disabledContainerColor = container,
    errorContainerColor = container,
    cursorColor = KioskColors.inputCursor,
    errorCursorColor = KioskColors.errorText,
    selectionColors = androidx.compose.foundation.text.selection.TextSelectionColors(
        handleColor = KioskColors.inputCursor,
        backgroundColor = KioskColors.inputCursor.copy(alpha = 0.3f),
    ),
    focusedBorderColor = KioskColors.systemBlue,
    unfocusedBorderColor = KioskColors.inputBorder,
    disabledBorderColor = KioskColors.inputBorder,
    errorBorderColor = KioskColors.errorText,
    focusedPlaceholderColor = KioskColors.inputHint,
    unfocusedPlaceholderColor = KioskColors.inputHint,
    disabledPlaceholderColor = KioskColors.inputHint,
    errorPlaceholderColor = KioskColors.inputHint,
    focusedLabelColor = KioskColors.inputLabel,
    unfocusedLabelColor = KioskColors.inputLabel,
    errorLabelColor = KioskColors.errorText,
)

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
    // Apply the palette synchronously (before children compose) so no frame ever paints with a stale palette.
    if (AppleThemeState.palette !== palette) AppleThemeState.apply(palette)
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
        ) {
            // Explicit defaults for everything that does not set its own colour (M3 falls back to BLACK
            // LocalContentColor outside a Surface -> black-on-black in Dark mode).
            CompositionLocalProvider(LocalContentColor provides palette.label) {
                ProvideTextStyle(KioskTypography.bodyLarge.copy(color = palette.label), content)
            }
        }
    }
}

private val ColorWhite = androidx.compose.ui.graphics.Color.White

val CardShape = RoundedCornerShape(RadiusXl)
val ControlShape = RoundedCornerShape(RadiusSm)
val ChipShape = RoundedCornerShape(50)
val InsetShape = RoundedCornerShape(RadiusMd)
