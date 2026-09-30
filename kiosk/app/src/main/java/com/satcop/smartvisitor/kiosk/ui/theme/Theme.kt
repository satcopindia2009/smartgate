package com.satcop.smartvisitor.kiosk.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Shapes
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

// Teal restyle radii (tokens.json): input 14, card 16, hero 24, sheet 24. Old names keep working.
val RadiusSm = 14.dp   // controls / inputs (was 6)
val RadiusMd = 14.dp   // insets (was 10)
val RadiusLg = 16.dp   // cards (was 12)
val RadiusXl = 16.dp   // cards (was 14)
val RadiusHero = 24.dp
val RadiusSheet = 24.dp

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
    focusedBorderColor = KioskColors.primary,
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
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.brandSoft,
            onPrimaryContainer = palette.primary,
            secondary = palette.brand,
            onSecondary = palette.onPrimary,
            secondaryContainer = palette.brandSoft,
            onSecondaryContainer = palette.label,
            tertiary = palette.info,
            background = palette.bg,
            onBackground = palette.label,
            surface = palette.card,
            onSurface = palette.label,
            surfaceVariant = palette.secondaryFill,
            onSurfaceVariant = palette.secondaryLabel,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.secondaryFill,
            outline = palette.inputBorder,
            outlineVariant = palette.separator,
            error = palette.danger,
            onError = palette.onPrimary,
            errorContainer = palette.dangerSoft,
            onErrorContainer = palette.danger,
            scrim = palette.scrim,
        )
    } else {
        lightColorScheme(
            primary = palette.primary,
            onPrimary = palette.onPrimary,
            primaryContainer = palette.brandSoft,
            onPrimaryContainer = palette.primary,
            secondary = palette.brand,
            onSecondary = ColorWhite,
            secondaryContainer = palette.brandSoft,
            onSecondaryContainer = palette.label,
            tertiary = palette.info,
            background = palette.bg,
            onBackground = palette.label,
            surface = palette.card,
            onSurface = palette.label,
            surfaceVariant = palette.secondaryFill,
            onSurfaceVariant = palette.secondaryLabel,
            surfaceContainer = palette.card,
            surfaceContainerHigh = palette.secondaryFill,
            outline = palette.inputBorder,
            outlineVariant = palette.separator,
            error = palette.danger,
            onError = ColorWhite,
            errorContainer = palette.dangerSoft,
            onErrorContainer = palette.danger,
            scrim = palette.scrim,
        )
    }

    // Hindi UI (app or device language) uses the bundled Noto Sans Devanagari for every M3 text style.
    val hindi = java.util.Locale.getDefault().language == "hi"
    val typography = remember(hindi) { if (hindi) kioskTypography(NotoDevanagariFamily) else KioskTypography }

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
            typography = typography,
            shapes = KioskShapes,
        ) {
            // Explicit defaults for everything that does not set its own colour (M3 falls back to BLACK
            // LocalContentColor outside a Surface -> black-on-black in Dark mode).
            CompositionLocalProvider(LocalContentColor provides palette.label) {
                ProvideTextStyle(typography.bodyLarge.copy(color = palette.label), content)
            }
        }
    }
}

private val ColorWhite = androidx.compose.ui.graphics.Color.White

val CardShape = RoundedCornerShape(RadiusXl)
val ControlShape = RoundedCornerShape(RadiusSm)
val ChipShape = RoundedCornerShape(50)
val InsetShape = RoundedCornerShape(RadiusMd)
val HeroShape = RoundedCornerShape(RadiusHero)
val SheetShape = RoundedCornerShape(topStart = RadiusSheet, topEnd = RadiusSheet)
val PillShape = RoundedCornerShape(50)

/** MaterialTheme shapes so un-styled M3 components (dialogs, menus, cards, buttons) match the restyle. */
val KioskShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(RadiusSm),
    large = RoundedCornerShape(RadiusLg),
    extraLarge = RoundedCornerShape(RadiusHero),
)

/** Spacing tokens (tokens.json, 4-point grid). */
object SgSpacing {
    val ScreenMargin = 20.dp
    val CardPadding = 16.dp
    val GapInCard = 8.dp
    val GapBetweenCards = 12.dp
    val SectionGap = 20.dp
    val MinTap = 48.dp
}

/** Component sizes (tokens.json). */
object SgSize {
    val ButtonHeight = 52.dp
    val SmallButtonHeight = 36.dp
    val ChipHeight = 36.dp
    val StatusChipHeight = 24.dp
    val InputHeight = 52.dp
    val TopBarHeight = 56.dp
    val BottomNavHeight = 64.dp
    val BottomNavCenterButton = 56.dp
    val BottomNavCenterLift = 16.dp
    val AvatarList = 44.dp
    val AvatarDetail = 72.dp
    val Icon = 24.dp
}
