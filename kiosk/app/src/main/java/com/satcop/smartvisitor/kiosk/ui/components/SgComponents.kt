package com.satcop.smartvisitor.kiosk.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ListAlt
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.ChipShape
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
import com.satcop.smartvisitor.kiosk.ui.theme.SgSize
import com.satcop.smartvisitor.kiosk.ui.theme.SgSpacing
import com.satcop.smartvisitor.kiosk.ui.theme.SgType
import com.satcop.smartvisitor.kiosk.ui.theme.kioskTextFieldColors

/*
 * SmartGate teal restyle — shared components (STYLE-GUIDE §5). Visual only; no logic.
 * Everything reads KioskColors / SgType / SgSize so Light + Dark + Auto follow the theme.
 */

// ───────────────────────── card surface ─────────────────────────

/** White card: soft shadow in light, 1dp separator border in dark (STYLE-GUIDE §4). */
@Composable
fun Modifier.sgCardSurface(shape: androidx.compose.ui.graphics.Shape = CardShape): Modifier =
    if (KioskColors.isDark) {
        this.clip(shape).background(KioskColors.card).border(1.dp, KioskColors.border, shape)
    } else {
        this
            .shadow(3.dp, shape, ambientColor = Color(0x2214201D), spotColor = Color(0x2214201D))
            .clip(shape)
            .background(KioskColors.card)
    }

// ───────────────────────── status chip ─────────────────────────

enum class SgStatusKind { PENDING, IN_PROGRESS, COMPLETED, REJECTED, BRAND, NEUTRAL }

/** Small tinted pill, 24dp high. Always colour + text label (never colour alone). */
@Composable
fun SgStatusChip(label: String, kind: SgStatusKind, modifier: Modifier = Modifier) {
    val (bg, fg) = when (kind) {
        SgStatusKind.PENDING -> KioskColors.infoSoft to KioskColors.info
        SgStatusKind.IN_PROGRESS -> KioskColors.warningSoft to KioskColors.warning
        SgStatusKind.COMPLETED -> KioskColors.successSoft to KioskColors.success
        SgStatusKind.REJECTED -> KioskColors.dangerSoft to KioskColors.danger
        SgStatusKind.BRAND -> KioskColors.brandSoft to KioskColors.primary
        SgStatusKind.NEUTRAL -> KioskColors.secondaryFill to KioskColors.textMuted
    }
    Box(
        modifier = modifier
            .heightIn(min = SgSize.StatusChipHeight)
            .clip(ChipShape)
            .background(bg)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = SgType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
    }
}

// ───────────────────────── buttons ─────────────────────────

/** Primary = solid pill, 52 high. Label is white on light / dark ink on dark (onPrimary). */
@Composable
fun SgPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .heightIn(min = SgSize.ButtonHeight)
            .clip(PillShape)
            .background(if (enabled) KioskColors.primary else KioskColors.secondaryFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) KioskColors.onPrimary else KioskColors.textMuted,
            style = SgType.Button,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Secondary = light grey fill pill with text colour. */
@Composable
fun SgSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .heightIn(min = SgSize.ButtonHeight)
            .clip(PillShape)
            .background(KioskColors.secondaryFill)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) KioskColors.text else KioskColors.textMuted,
            style = SgType.Button,
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Destructive-outline pill (Reject, Sign out). */
@Composable
fun SgDangerOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Box(
        modifier = modifier
            .heightIn(min = SgSize.ButtonHeight)
            .clip(PillShape)
            .border(BorderStroke(1.dp, KioskColors.danger), PillShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = KioskColors.danger, style = SgType.Button, maxLines = 2, textAlign = TextAlign.Center)
    }
}

enum class SgPillStyle { SOLID, OUTLINE_DANGER, SOFT }

/** Small action pill, 36 high, used inside list cards (Reject outline, Approve/Check in/Check out solid). */
@Composable
fun SgSmallPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SgPillStyle = SgPillStyle.SOLID,
    enabled: Boolean = true,
) {
    val base = modifier
        .heightIn(min = SgSize.SmallButtonHeight)
        .clip(PillShape)
    val (styled, fg) = when (style) {
        SgPillStyle.SOLID -> base.background(if (enabled) KioskColors.primary else KioskColors.secondaryFill) to
            (if (enabled) KioskColors.onPrimary else KioskColors.textMuted)
        SgPillStyle.OUTLINE_DANGER -> base.border(BorderStroke(1.dp, KioskColors.danger), PillShape) to KioskColors.danger
        SgPillStyle.SOFT -> base.background(KioskColors.secondaryFill) to KioskColors.text
    }
    Box(
        modifier = styled
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, style = SgType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), maxLines = 1)
    }
}

// ───────────────────────── text field / search / chips ─────────────────────────

/** Filled input (52 min, 14 radius, 1dp border) with the label above; optional helper below. */
@Composable
fun SgTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helper: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    readOnly: Boolean = false,
) {
    Column(modifier) {
        KioskField(
            label = label,
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            error = error,
            keyboardType = keyboardType,
            visualTransformation = visualTransformation,
            singleLine = singleLine,
            imeAction = imeAction,
            onImeAction = onImeAction,
            readOnly = readOnly,
        )
        if (!helper.isNullOrBlank() && error == null) {
            Text(
                helper,
                color = KioskColors.textMuted,
                style = SgType.Caption,
                modifier = Modifier.padding(top = FormTokens.ErrorGap),
            )
        }
    }
}

/** Search pill: 48 high, light grey fill, magnifier icon. */
@Composable
fun SgSearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = SgType.Body.copy(color = KioskColors.inputText),
        placeholder = { Text(placeholder, color = KioskColors.inputHint, style = SgType.Body) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = KioskColors.inputLabel) },
        keyboardOptions = KeyboardOptions(imeAction = if (onSearch != null) ImeAction.Search else ImeAction.Default),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
        shape = PillShape,
        colors = kioskTextFieldColors(container = KioskColors.searchFill),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).clip(PillShape),
    )
}

/** Horizontally scrollable filter chips: active = primary fill, inactive = light grey fill. */
@Composable
fun SgFilterChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { i, label ->
            val on = i == selectedIndex
            Box(
                modifier = Modifier
                    .heightIn(min = SgSize.ChipHeight)
                    .clip(ChipShape)
                    .background(if (on) KioskColors.primary else KioskColors.secondaryFill)
                    .semantics { selected = on; role = Role.Tab }
                    .clickable { onSelect(i) }
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (on) KioskColors.onPrimary else KioskColors.text,
                    style = SgType.Label,
                    maxLines = 1,
                )
            }
        }
    }
}

// ───────────────────────── visit card ─────────────────────────

/**
 * Visit card: 44 round photo (or initials) left, name 15/600, purpose line, time+date caption,
 * status chip top right, optional action-pill row underneath. Same card on Home, Log, Inside, Host Inbox.
 * [subtitle]/[timeText] are shown as given (masked values from the API are never altered here).
 */
@Composable
fun SgVisitCard(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    timeText: String? = null,
    initials: String = name.trim().take(1).uppercase(),
    avatar: (@Composable () -> Unit)? = null,
    status: (@Composable () -> Unit)? = null,
    extraChip: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .sgCardSurface()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(SgSpacing.CardPadding),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            if (avatar != null) {
                Box(Modifier.size(SgSize.AvatarList).clip(CircleShape)) { avatar() }
            } else {
                Box(
                    Modifier.size(SgSize.AvatarList).clip(CircleShape).background(KioskColors.brandSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(initials, color = KioskColors.primary, style = SgType.SectionTitle)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = KioskColors.text, style = SgType.BodyStrong, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = KioskColors.textMuted, style = SgType.Label.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (!timeText.isNullOrBlank()) {
                    Text(timeText, color = KioskColors.textMuted, style = SgType.Caption, maxLines = 1)
                }
            }
            if (status != null || extraChip != null) {
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    status?.invoke()
                    extraChip?.invoke()
                }
            }
        }
        if (actions != null) {
            Spacer(Modifier.height(SgSpacing.GapInCard + 4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
        }
    }
}


// ───────────────────────── bottom nav ─────────────────────────

data class SgNavItem(
    val label: String,
    val outlined: ImageVector,
    val filled: ImageVector,
)

/** Role tab sets (STYLE-GUIDE §5). Labels are English UI copy; the centre action of Gate is "Add visitor". */
object SgNavSets {
    val Gate = listOf(
        SgNavItem("Home", Icons.Outlined.Home, Icons.Filled.Home),
        SgNavItem("Inside", Icons.Outlined.Groups, Icons.Filled.Groups),
        SgNavItem("Log", Icons.Outlined.ListAlt, Icons.Filled.ListAlt),
        SgNavItem("Profile", Icons.Outlined.Person, Icons.Filled.Person),
    )
    val Host = listOf(
        SgNavItem("Inbox", Icons.Outlined.Inbox, Icons.Filled.Inbox),
        SgNavItem("Done", Icons.Outlined.CheckCircle, Icons.Filled.CheckCircle),
        SgNavItem("Inside", Icons.Outlined.Groups, Icons.Filled.Groups),
        SgNavItem("Profile", Icons.Outlined.Person, Icons.Filled.Person),
    )
    val Guard = listOf(
        SgNavItem("Today", Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday),
        SgNavItem("Patrol", Icons.Outlined.Security, Icons.Filled.Security),
        SgNavItem("Desk", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
        SgNavItem("Profile", Icons.Outlined.Person, Icons.Filled.Person),
    )
}

/**
 * Bottom navigation, 64dp + safe area. [items] are the TABS (4). When [centerLabel] is non-null (Gate),
 * a raised 56dp round button sits between tab 2 and 3 = five slots, and [onCenter] fires.
 * Host/Guard pass no centre. [visible] = false hides it on full-screen steps (camera, OTP, Add Visitor, clock-in).
 * [selectedIndex] indexes [items] (the centre button is never "selected").
 *
 * NOT wired into the shell yet (KioskApp/GateToday/... still use AppleTabBar); drop-in ready.
 */
@Composable
fun SgBottomNav(
    items: List<SgNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    centerLabel: String? = null,
    centerIcon: ImageVector = Icons.Filled.Add,
    onCenter: () -> Unit = {},
    visible: Boolean = true,
) {
    if (!visible) return
    val hasCenter = centerLabel != null
    val lift = if (hasCenter) SgSize.BottomNavCenterLift else 0.dp
    Box(modifier.fillMaxWidth().navigationBarsPadding().height(SgSize.BottomNavHeight + lift)) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(SgSize.BottomNavHeight)
                .then(
                    if (KioskColors.isDark) Modifier.background(KioskColors.tabBarBg).topSeparator(KioskColors.border)
                    else Modifier.shadow(6.dp, ambientColor = Color(0x0F14201D), spotColor = Color(0x0F14201D)).background(KioskColors.tabBarBg),
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val split = if (hasCenter) items.size / 2 else items.size
            items.forEachIndexed { i, item ->
                if (hasCenter && i == split) Spacer(Modifier.weight(1f)) // centre slot
                SgNavTab(item, i == selectedIndex) { onSelect(i) }
            }
        }
        if (hasCenter) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .size(SgSize.BottomNavCenterButton)
                    .shadow(10.dp, CircleShape, ambientColor = KioskColors.brand.copy(alpha = 0.35f), spotColor = KioskColors.brand.copy(alpha = 0.35f))
                    .clip(CircleShape)
                    .background(KioskColors.primary)
                    .semantics { contentDescription = centerLabel!!; role = Role.Button }
                    .clickable(onClick = onCenter),
                contentAlignment = Alignment.Center,
            ) {
                Icon(centerIcon, contentDescription = null, tint = KioskColors.onPrimary, modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun RowScope.SgNavTab(item: SgNavItem, selected: Boolean, onClick: () -> Unit) {
    val tint = if (selected) KioskColors.primary else KioskColors.textMuted
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeightMin()
            .semantics { this.selected = selected; role = Role.Tab }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = if (selected) item.filled else item.outlined,
            contentDescription = item.label,
            tint = tint,
            modifier = Modifier.size(SgSize.Icon),
        )
        Text(item.label, color = tint, style = SgType.NavLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun Modifier.fillMaxHeightMin(): Modifier = this.heightIn(min = SgSize.BottomNavHeight)

private fun Modifier.topSeparator(color: Color): Modifier =
    this.drawBehind {
        drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
    }
