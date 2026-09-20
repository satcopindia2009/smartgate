package com.satcop.smartvisitor.kiosk.ui.apple

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import com.satcop.smartvisitor.kiosk.ui.theme.InsetShape
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
            .height(44.dp)
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
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = KioskFont,
        letterSpacing = 0.37.sp,
        lineHeight = 41.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
fun AppleSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        placeholder = {
            Text(placeholder, color = KioskColors.textMuted, fontFamily = KioskFont, fontSize = 17.sp)
        },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(10.dp)),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = KioskColors.searchFill,
            unfocusedContainerColor = KioskColors.searchFill,
            disabledContainerColor = KioskColors.searchFill,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = KioskColors.systemBlue,
            focusedTextColor = KioskColors.text,
            unfocusedTextColor = KioskColors.text,
        ),
        textStyle = androidx.compose.ui.text.TextStyle(
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
            .clip(RoundedCornerShape(9.dp))
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
                    .clip(RoundedCornerShape(7.dp))
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
        text = text.uppercase(),
        color = KioskColors.textMuted,
        fontSize = 13.sp,
        fontFamily = KioskFont,
        letterSpacing = (-0.08).sp,
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
                        .clip(RoundedCornerShape(7.dp))
                        .background(glyphColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(glyph, color = Color.White, fontSize = 14.sp)
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
    color: Color = KioskColors.systemBlue,
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
            color = Color.White,
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
        color = if (positive) Color.White else KioskColors.systemRed,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = KioskFont,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (positive) KioskColors.systemGreen else KioskColors.secondaryFill)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 8.dp),
    )
}

data class AppleTabItem(val icon: String, val label: String)

@Composable
fun AppleTabBar(
    tabs: List<AppleTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(KioskColors.tabBarBg)
            .border(width = 0.33.dp, color = KioskColors.border)
            .navigationBarsPadding()
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround,
    ) {
        tabs.forEachIndexed { index, tab ->
            val on = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(index) }
                    .padding(vertical = 2.dp),
            ) {
                Text(tab.icon, fontSize = 22.sp, color = if (on) KioskColors.systemBlue else KioskColors.textMuted)
                Text(
                    tab.label,
                    fontSize = 10.sp,
                    color = if (on) KioskColors.systemBlue else KioskColors.textMuted,
                    fontFamily = KioskFont,
                )
            }
        }
    }
}

@Composable
fun AppearanceSegmentedRow() {
    val mode = LocalAppearanceMode.current
    val setMode = LocalSetAppearanceMode.current
    val options = listOf("Light", "Dark", "Auto")
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
                    "Follows Apple Light / Dark tokens. Auto uses system setting.",
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
        color = Color.White,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = KioskFont,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(KioskColors.systemBlue)
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
