package com.satcop.smartvisitor.kiosk.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.R

/**
 * 1059b: colours are deliberately NOT baked into these styles. In 1059 they were read once from
 * KioskColors at class-init (= Light palette -> black), and MaterialTheme feeds bodyLarge to every
 * TextField via LocalTextStyle, so typed text stayed black on the dark field. Colour now comes from
 * [SatcopKioskTheme] (LocalContentColor + ProvideTextStyle) and the per-field tokens.
 *
 * Teal restyle (2026-09-30): Inter (bundled static 400/500/600/700, OFL) with Noto Sans Devanagari
 * (bundled static 400/500/600/700, OFL) as the fallback family. Compose picks glyphs per character
 * from the family list, so Latin renders in Inter and Devanagari in Noto Sans Devanagari.
 * Licence texts ship under assets/licenses/.
 */
val InterFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

val NotoDevanagariFamily = FontFamily(
    Font(R.font.noto_sans_devanagari_regular, FontWeight.Normal),
    Font(R.font.noto_sans_devanagari_medium, FontWeight.Medium),
    Font(R.font.noto_sans_devanagari_semibold, FontWeight.SemiBold),
    Font(R.font.noto_sans_devanagari_bold, FontWeight.Bold),
)

/**
 * Default UI family = Inter. Devanagari glyphs missing from Inter fall back to the platform's
 * Noto Sans Devanagari; when the app/device language is Hindi the theme switches the whole
 * Material typography to [NotoDevanagariFamily] (bundled) so Hindi renders identically on every phone.
 */
val KioskFont: FontFamily = InterFamily

/** tokens.json type scale (size/weight/lineHeight). */
object SgType {
    val ScreenTitle = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp)
    val Greeting = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val SectionTitle = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp)
    val Body = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val BodyStrong = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 22.sp)
    val Label = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp)
    val Caption = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    val Button = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp)
    val OtpDigit = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp)
    val BigNumber = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp)
    /** Bottom-nav label (11sp per style guide). */
    val NavLabel = TextStyle(fontFamily = KioskFont, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp)
}

/** Material3 slots mapped onto the SmartGate scale so un-styled M3 text picks up the look. */
val KioskTypography: Typography = kioskTypography(KioskFont)

fun kioskTypography(family: FontFamily): Typography = Typography(
    displayLarge = SgType.BigNumber.copy(fontFamily = family),
    displayMedium = SgType.BigNumber.copy(fontFamily = family),
    displaySmall = SgType.BigNumber.copy(fontFamily = family),
    headlineLarge = SgType.ScreenTitle.copy(fontFamily = family),
    headlineMedium = SgType.ScreenTitle.copy(fontFamily = family),
    headlineSmall = SgType.Greeting.copy(fontFamily = family),
    titleLarge = SgType.Greeting.copy(fontFamily = family),
    titleMedium = SgType.SectionTitle.copy(fontFamily = family),
    titleSmall = SgType.BodyStrong.copy(fontFamily = family),
    bodyLarge = SgType.Body.copy(fontFamily = family),
    bodyMedium = SgType.Body.copy(fontSize = 14.sp, lineHeight = 20.sp).copy(fontFamily = family),
    bodySmall = SgType.Caption.copy(fontFamily = family),
    labelLarge = SgType.Button.copy(fontSize = 15.sp).copy(fontFamily = family),
    labelMedium = SgType.Label.copy(fontFamily = family),
    labelSmall = SgType.Caption.copy(fontWeight = FontWeight.Medium).copy(fontFamily = family),
)
