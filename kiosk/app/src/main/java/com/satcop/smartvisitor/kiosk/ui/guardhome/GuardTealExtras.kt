package com.satcop.smartvisitor.kiosk.ui.guardhome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.ui.components.sgCardSurface
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
import com.satcop.smartvisitor.kiosk.ui.theme.SgSize
import com.satcop.smartvisitor.kiosk.ui.theme.SgSpacing
import com.satcop.smartvisitor.kiosk.ui.theme.SgType

/*
 * Guard teal restyle: small extras that ui/components/SgComponents.kt does not have.
 * Visual only. Screen copy for the flow titles follows the approved pictures (sentence case);
 * the ClockInLogic strings and their tests are untouched.
 */

/** Sentence-case flow titles from the approved pictures (40-49). */
object GuardCopy {
    fun flowTitle(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Self Check In" else "Self Check Out"
    fun selfieTitle(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Check-in selfie" else "Check-out selfie"
    fun resultBody(mode: AttendanceMode) = ClockInLogic.resultBody(mode)
    fun resultChip(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Checked in" else "Checked out"
    const val LOOK_AT_CAMERA = "Look at the camera"
    const val CONTINUE = "Continue"
    const val BACK = "Back"
    const val CHECK_IN = "Check in"
    const val SIGN_OUT = "Sign out"
    const val DONE = "Done"
    const val RETRY = "Retry"
    const val OPEN_SETTINGS = "Open Settings"
    const val OPEN_LOCATION_SETTINGS = GuardGeoLogic.OPEN_LOCATION_SETTINGS
    const val CAPTURE = "Capture Selfie"
    const val CURRENT_STATUS = "Current Status"
    const val GUARD_DETAILS = "Guard Details"
    const val HOW_IT_WORKS = "How it works"
    const val ROLE = "Security Guard"
    const val SELFIE_UPLOADED = "Uploaded ✓"
    fun photoPill(mode: AttendanceMode) = if (mode == AttendanceMode.CHECK_IN) "Selfie Check-In Photo" else "Selfie Check-Out Photo"
    fun infoBanner(mode: AttendanceMode): String {
        val word = if (mode == AttendanceMode.CHECK_IN) "check-in" else "check-out"
        return "Your front camera will open to capture a selfie for $word verification. Make sure your face is clearly visible and well-lit."
    }
    val HOW_STEPS = listOf(
        "Camera opens — position your face in the oval",
        "Selfie is securely uploaded to the server",
        "Attendance is recorded with time & location",
    )
}

/** Top bar (56): back arrow left, centred title. */
@Composable
fun GuardTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxWidth().statusBarsPadding().heightIn(min = SgSize.TopBarHeight)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape)
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = KioskColors.text) }
        Text(title, color = KioskColors.text, style = SgType.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 56.dp))
    }
}

/** Round soft icon badge. */
@Composable
fun GuardIconBadge(icon: ImageVector, size: Dp = 40.dp, bg: Color = KioskColors.brandSoft, tint: Color = KioskColors.primary, iconSize: Dp = 22.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** White card surface (shadow light / border dark) with 16 padding. */
@Composable
fun GuardCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().sgCardSurface(CardShape).padding(SgSpacing.CardPadding),
    ) { content() }
}

/** Row inside a card: icon badge, title 15/600 + subtitle, optional trailing check. */
@Composable
fun GuardInfoRow(icon: ImageVector, title: String, subtitle: String, trailingCheck: Boolean = false, divider: Boolean = true) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            GuardIconBadge(icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = KioskColors.text, style = SgType.BodyStrong)
                Text(subtitle, color = KioskColors.textMuted, style = SgType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
            }
            if (trailingCheck) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = KioskColors.primary, modifier = Modifier.size(24.dp))
            }
        }
        if (divider) HorizontalDivider(thickness = 1.dp, color = KioskColors.border)
    }
}

/** Label left, value right (result card details). */
@Composable
fun GuardKeyValueRow(label: String, value: String, valueColor: Color = KioskColors.text) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, color = KioskColors.textMuted, style = SgType.Body)
        Text(value, color = valueColor, style = SgType.BodyStrong, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
    }
}

enum class GuardBannerKind { WARNING, DANGER, INFO }

/** Full-width rounded banner with icon (STYLE-GUIDE banners). */
@Composable
fun GuardBanner(text: String, kind: GuardBannerKind = GuardBannerKind.WARNING, icon: ImageVector = Icons.Outlined.Warning, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        GuardBannerKind.WARNING -> KioskColors.warningSoft to KioskColors.warning
        GuardBannerKind.DANGER -> KioskColors.dangerSoft to KioskColors.danger
        GuardBannerKind.INFO -> KioskColors.infoSoft to KioskColors.info
    }
    Row(
        modifier = modifier.fillMaxWidth().clip(CardShape).background(bg).padding(12.dp)
            .semantics { contentDescription = text },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = if (KioskColors.isDark) KioskColors.text else fg, style = SgType.Label, modifier = Modifier.weight(1f))
    }
}

/** Secondary button with a thin border (the "Back" pill in the pictures). */
@Composable
fun GuardOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier.heightIn(min = SgSize.ButtonHeight).clip(PillShape)
            .border(1.dp, KioskColors.border, PillShape)
            .background(KioskColors.card)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = KioskColors.text, style = SgType.Button, maxLines = 1) }
}

/** Text-only pill ("Sign out" under the primary button). */
@Composable
fun GuardTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.heightIn(min = 48.dp).clip(PillShape).clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = KioskColors.primary, style = SgType.Button, maxLines = 1) }
}

/** Big centred icon in a soft circle (result / error cards). */
@Composable
fun GuardHeroIcon(icon: ImageVector, bg: Color, tint: Color) {
    GuardIconBadge(icon, size = 72.dp, bg = bg, tint = tint, iconSize = 36.dp)
}

@Composable
fun GuardGap(h: Dp = SgSpacing.GapBetweenCards) = Spacer(Modifier.height(h))


/** Teal header of the Self Check In / Out flow (Viren's reference screenshots): back, bold title, subtitle. */
@Composable
fun GuardTealHeader(title: String, subtitle: String?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().background(KioskColors.primary).statusBarsPadding()
            .heightIn(min = SgSize.TopBarHeight).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(KioskColors.onPrimary.copy(alpha = 0.18f))
                .clickable(role = Role.Button, onClick = onBack)
                .semantics { contentDescription = "Back" },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null, tint = KioskColors.onPrimary) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = KioskColors.onPrimary, style = SgType.ScreenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, color = KioskColors.onPrimary.copy(alpha = 0.85f), style = SgType.Label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Primary pill with a leading icon ("Capture Selfie"). */
@Composable
fun GuardIconPrimaryButton(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Row(
        modifier = modifier.heightIn(min = SgSize.ButtonHeight).clip(PillShape)
            .background(if (enabled) KioskColors.primary else KioskColors.primary.copy(alpha = 0.45f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp)
            .semantics { contentDescription = text },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = KioskColors.onPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = KioskColors.onPrimary, style = SgType.Button, maxLines = 1)
    }
}
