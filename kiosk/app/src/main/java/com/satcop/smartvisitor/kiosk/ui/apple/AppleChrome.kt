package com.satcop.smartvisitor.kiosk.ui.apple

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.theme.AppearanceMode
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.HeroShape
import com.satcop.smartvisitor.kiosk.ui.theme.InsetShape
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
import com.satcop.smartvisitor.kiosk.ui.components.SgBottomNav
import com.satcop.smartvisitor.kiosk.ui.components.SgNavItem
import com.satcop.smartvisitor.kiosk.ui.components.SgNavSets
import com.satcop.smartvisitor.kiosk.ui.theme.SgSize
import com.satcop.smartvisitor.kiosk.ui.theme.SgType
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.LocalAppearanceMode
import com.satcop.smartvisitor.kiosk.ui.theme.LocalSetAppearanceMode

@Composable
fun AppleNavBar(
    title: String = "",
    leading: String? = null,
    trailing: String? = null,
    onLeading: (() -> Unit)? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp),
    ) {
        if (leading != null) {
            Text(
                text = leading,
                color = KioskColors.systemBlue,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = KioskFont,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .clickable(enabled = onLeading != null) { onLeading?.invoke() },
            )
        }
        if (title.isNotBlank()) {
            Text(
                text = title,
                color = KioskColors.text,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        if (trailing != null) {
            Text(
                text = trailing,
                color = KioskColors.systemBlue,
                fontSize = 17.sp,
                fontFamily = KioskFont,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clickable(enabled = onTrailing != null) { onTrailing?.invoke() },
            )
        }
    }
}

@Composable
fun AppleLargeTitle(text: String) {
    Text(
        text = text,
        color = KioskColors.text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = KioskFont,
        lineHeight = 28.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
fun AppleSearchField(
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
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            imeAction = if (onSearch != null) androidx.compose.ui.text.input.ImeAction.Search
            else androidx.compose.ui.text.input.ImeAction.Default,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
            onSearch = { onSearch?.invoke() },
        ),
        placeholder = {
            Text(placeholder, color = KioskColors.inputHint, fontFamily = KioskFont, fontSize = 17.sp)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 48.dp)
            .clip(PillShape),
        shape = PillShape,
        colors = com.satcop.smartvisitor.kiosk.ui.theme.kioskTextFieldColors(container = KioskColors.searchFill),
        textStyle = androidx.compose.ui.text.TextStyle(
            color = KioskColors.inputText,
            fontFamily = KioskFont,
            fontSize = 17.sp,
        ),
    )
}

@Composable
fun AppleSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(KioskColors.secondaryFill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { index, label ->
            val on = index == selectedIndex
            Text(
                text = label,
                color = if (on) KioskColors.text else KioskColors.textMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = KioskFont,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (on) KioskColors.card else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 7.dp),
            )
        }
    }
}

@Composable
fun AppleSectionHeader(text: String) {
    Text(
        text = text,
        color = KioskColors.textMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        fontFamily = KioskFont,
        modifier = Modifier.padding(start = 28.dp, end = 16.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun AppleInset(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(InsetShape)
            .background(KioskColors.card),
    ) {
        content()
    }
}

@Composable
fun AppleCell(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    glyph: String? = null,
    glyphColor: Color = KioskColors.systemBlue,
    showChevron: Boolean = true,
    showDivider: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (glyph != null) {
                Box(
                    modifier = Modifier
                        .size(29.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(glyphColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, color = KioskColors.onFill(glyphColor), fontSize = 14.sp)
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, color = KioskColors.text, fontSize = 17.sp, fontFamily = KioskFont)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
                }
            }
            if (!trailing.isNullOrBlank()) {
                Text(trailing, color = KioskColors.textMuted, fontSize = 17.sp, fontFamily = KioskFont)
            }
            if (showChevron) {
                Text("›", color = KioskColors.textMuted.copy(alpha = 0.45f), fontSize = 20.sp)
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.33.dp,
                color = KioskColors.border,
            )
        }
    }
}

@Composable
fun AppleAvatar(
    initials: String,
    color: Color = KioskColors.brandSoft,
    size: androidx.compose.ui.unit.Dp = 48.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = if (color == KioskColors.brandSoft) KioskColors.primary else KioskColors.onFill(color),
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.33f).sp,
            fontFamily = KioskFont,
        )
    }
}

@Composable
fun ApplePillButton(
    text: String,
    onClick: () -> Unit,
    positive: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        color = if (positive) KioskColors.onPrimary else KioskColors.danger,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = KioskFont,
        textAlign = TextAlign.Center,
        modifier = modifier
            .heightIn(min = SgSize.SmallButtonHeight)
            .clip(PillShape)
            .background(if (positive) KioskColors.primary else KioskColors.secondaryFill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

data class AppleTabItem(val label: String, val icon: ImageVector)

/**
 * Bottom navigation for every role. Delegates to [SgBottomNav] (teal restyle, filled-active icons).
 * Host and Guard pass 4 tabs and no centre; Gate passes [centerLabel] + [onCenter] for the raised centre button
 * (five slots). Tab callers keep their existing state: [selectedIndex] indexes [tabs] and the centre is never selected.
 */
@Composable
fun AppleTabBar(
    tabs: List<AppleTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    centerLabel: String? = null,
    onCenter: () -> Unit = {},
    visible: Boolean = true,
    centerEnabled: Boolean = true,
) {
    val known = SgNavSets.Gate + SgNavSets.Host + SgNavSets.Guard
    val items = tabs.map { t ->
        val label = if (t.label == "Settings") "Profile" else t.label
        known.firstOrNull { it.label == label } ?: SgNavItem(label, t.icon, t.icon)
    }
    SgBottomNav(
        items = items,
        selectedIndex = selectedIndex,
        onSelect = onSelect,
        centerLabel = centerLabel,
        onCenter = onCenter,
        visible = visible,
        centerEnabled = centerEnabled,
    )
}

@Composable
fun AppearanceSegmentedRow() {
    val mode = LocalAppearanceMode.current
    val setMode = LocalSetAppearanceMode.current
    val options = listOf("Light", "Dark", "System")
    val selected = when (mode) {
        AppearanceMode.LIGHT -> 0
        AppearanceMode.DARK -> 1
        AppearanceMode.AUTO -> 2
    }
    Column(Modifier.fillMaxWidth()) {
        AppleSectionHeader("Appearance")
        AppleInset {
            Column(Modifier.padding(vertical = 10.dp)) {
                Text(
                    "Theme",
                    color = KioskColors.text,
                    fontSize = 17.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                AppleSegmented(
                    options = options,
                    selectedIndex = selected,
                    onSelect = { idx ->
                        setMode(
                            when (idx) {
                                0 -> AppearanceMode.LIGHT
                                1 -> AppearanceMode.DARK
                                else -> AppearanceMode.AUTO
                            },
                        )
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Light, Dark, or follow this phone's system setting (default).",
                    color = KioskColors.textMuted,
                    fontSize = 13.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun ApplePrimaryButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        color = KioskColors.onPrimary,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = KioskFont,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = SgSize.ButtonHeight)
            .clip(PillShape)
            .background(KioskColors.primary)
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
    )
}

@Composable
fun ApplePlainButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = text,
        color = KioskColors.systemBlue,
        fontSize = 17.sp,
        fontFamily = KioskFont,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    )
}


/* ——— AC-APP1 bottomnav shell chrome (phone-apple-bottomnav SoT) ——— */

@Composable
fun AppleShellNav(
    leading: String = "Roles",
    trailing: String? = null,
    onLeading: (() -> Unit)? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = leading,
            color = KioskColors.systemBlue,
            fontSize = 17.sp,
            fontFamily = KioskFont,
            modifier = Modifier.clickable(enabled = onLeading != null) { onLeading?.invoke() },
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = KioskColors.systemBlue,
                fontSize = 17.sp,
                fontFamily = KioskFont,
                modifier = Modifier.clickable(enabled = onTrailing != null) { onTrailing?.invoke() },
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
fun AppleShellTitle(text: String) {
    Text(
        text = text,
        color = KioskColors.text,
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = KioskFont,
        lineHeight = 28.sp,
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp, bottom = 2.dp),
    )
}

@Composable
fun AppleShellSub(text: String) {
    Text(
        text = text,
        color = KioskColors.textMuted,
        fontSize = 13.sp,
        fontFamily = KioskFont,
        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp),
    )
}

@Composable
fun AppleHeroCta(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    trailing: String = "→",
) {
    val bg = if (filled) KioskColors.primary else KioskColors.secondaryFill
    val fg = if (filled) KioskColors.onPrimary else KioskColors.text
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(HeroShape)
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
            Text(
                subtitle,
                color = if (filled) fg.copy(alpha = 0.9f) else KioskColors.textMuted,
                fontSize = 13.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(trailing, color = fg, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AppleGrid2(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun RowScope.AppleTile(
    label: String,
    detail: String? = null,
    icon: String? = null,
    stat: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 88.dp)
            .clip(CardShape)
            .background(KioskColors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Text(icon, fontSize = 22.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        if (stat != null) {
            Text(label, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
            Text(
                stat,
                color = KioskColors.systemBlue,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 34.sp,
                fontFamily = KioskFont,
            )
        } else {
            Text(label, color = KioskColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
            if (!detail.isNullOrBlank()) {
                Text(
                    detail,
                    color = KioskColors.textMuted,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun AppleStatusPill(text: String) {
    Text(
        text = text,
        color = KioskColors.systemBlue,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = KioskFont,
        modifier = Modifier
            .clip(PillShape)
            .background(KioskColors.brandSoft)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
