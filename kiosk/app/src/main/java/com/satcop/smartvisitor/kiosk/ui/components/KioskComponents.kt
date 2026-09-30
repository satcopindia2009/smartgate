package com.satcop.smartvisitor.kiosk.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.satcop.smartvisitor.kiosk.ui.theme.kioskTextFieldColors
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.LocalKioskCompact
import com.satcop.smartvisitor.kiosk.ui.theme.ChipShape
import com.satcop.smartvisitor.kiosk.ui.theme.ControlShape
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusSm

@Composable
fun DemoWatermark(label: String, modifier: Modifier = Modifier) {
    Text(
        text = label.uppercase(),
        modifier = modifier.padding(start = 12.dp, bottom = 12.dp),
        color = KioskColors.watermark,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.4.sp,
        fontFamily = KioskFont,
    )
}

@Composable
fun StepDots(current: Int, total: Int = 4) {
    val compact = LocalKioskCompact.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (compact) FormTokens.FieldToField else FormTokens.HeaderToForm),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(total) { index ->
            val step = index + 1
            val color = when {
                step == current -> KioskColors.purple
                step < current -> KioskColors.cyan
                else -> KioskColors.border
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(ChipShape)
                    .background(color),
            )
        }
    }
}

@Composable
fun KioskPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val brush = Brush.linearGradient(listOf(KioskColors.systemBlue, KioskColors.systemBlue))
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(KioskColors.border, KioskColors.border)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun KioskCyanButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val brush = Brush.linearGradient(listOf(KioskColors.systemBlue, KioskColors.systemBlue))
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(KioskColors.border, KioskColors.border)))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else KioskColors.textDim,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun KioskGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(RadiusSm))
            .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusSm))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = KioskColors.textMuted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            maxLines = 2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun KioskField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    singleLine: Boolean = true,
    minLines: Int = 1,
    autoCorrect: Boolean = true,
    imeAction: ImeAction = ImeAction.Default,
    onImeAction: (() -> Unit)? = null,
    /** Password field: shows a Show/Hide toggle; [visualTransformation] is ignored while shown. */
    passwordToggle: Boolean = false,
) {
    var shown by remember { mutableStateOf(false) }
    val masked = passwordToggle && !shown
    Column(modifier = modifier) {
        Text(
            text = label,
            color = KioskColors.inputLabel,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            modifier = Modifier.padding(bottom = FormTokens.LabelToField),
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FormTokens.MinTouch)
                .border(
                    1.dp,
                    if (error != null) KioskColors.errorText else KioskColors.inputBorder,
                    ControlShape,
                )
                .clip(ControlShape),
            // Explicit colour on the text style itself: it wins over any inherited LocalTextStyle colour.
            textStyle = TextStyle(
                color = KioskColors.inputText,
                fontFamily = KioskFont,
                fontSize = 16.sp,
            ),
            placeholder = {
                Text(placeholder, color = KioskColors.inputHint, fontFamily = KioskFont)
            },
            singleLine = singleLine,
            minLines = minLines,
            visualTransformation = if (passwordToggle) {
                if (masked) PasswordVisualTransformation() else VisualTransformation.None
            } else {
                visualTransformation
            },
            isError = error != null,
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                capitalization = capitalization,
                autoCorrectEnabled = autoCorrect,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onDone = { onImeAction?.invoke() },
                onGo = { onImeAction?.invoke() },
                onNext = { onImeAction?.invoke() },
            ),
            trailingIcon = if (passwordToggle) {
                {
                    Text(
                        text = if (shown) "Hide" else "Show",
                        color = KioskColors.systemBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = KioskFont,
                        modifier = Modifier
                            .clickable { shown = !shown }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                    )
                }
            } else {
                null
            },
            shape = ControlShape,
            colors = kioskTextFieldColors(),
        )
        AnimatedVisibility(visible = error != null, enter = fadeIn(), exit = fadeOut()) {
            Text(
                text = error.orEmpty(),
                color = KioskColors.errorText,
                fontSize = 12.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = FormTokens.ErrorGap),
            )
        }
    }
}

@Composable
fun GatePill(name: String, onClick: () -> Unit) {
    StatusPill(
        label = name,
        background = KioskColors.cyanDim,
        foreground = KioskColors.cyanBright,
        onClick = onClick,
    )
}

@Composable
fun SourcePill(live: Boolean) {
    StatusPill(
        label = if (live) "LIVE" else "FIXTURES",
        background = if (live) KioskColors.cyanDim else KioskColors.orangeDim,
        foreground = if (live) KioskColors.cyanBright else KioskColors.peakAmber,
    )
}

@Composable
fun StatusPill(
    label: String,
    background: Color,
    foreground: Color,
    onClick: (() -> Unit)? = null,
) {
    val compact = LocalKioskCompact.current
    Box(
        modifier = Modifier
            .heightIn(min = if (compact) 48.dp else 0.dp)
            .clip(ChipShape)
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = if (compact) 12.dp else 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
        )
    }
}

enum class ToastKind { INFO, SUCCESS, WARNING, ERROR }

@Composable
fun ToastBanner(message: String?, kind: ToastKind = ToastKind.INFO) {
    val accent = when (kind) {
        ToastKind.SUCCESS -> KioskColors.green
        ToastKind.WARNING -> KioskColors.orange
        ToastKind.ERROR -> KioskColors.red
        ToastKind.INFO -> KioskColors.purple
    }
    AnimatedVisibility(visible = !message.isNullOrBlank(), enter = fadeIn(), exit = fadeOut()) {
        Row(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 360.dp)
                .clip(RoundedCornerShape(RadiusSm))
                .background(KioskColors.card)
                .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusSm)),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 3.dp, height = 48.dp)
                    .background(accent),
            )
            Text(
                text = message.orEmpty(),
                color = KioskColors.text,
                fontSize = 13.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
fun RecentChip(label: String) {
    Row(
        modifier = Modifier
            .clip(ChipShape)
            .background(KioskColors.secondaryFill)
            .border(1.dp, KioskColors.borderSubtle, ChipShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(KioskColors.cyan.copy(alpha = 0.7f)),
        )
        Text(
            text = label,
            color = KioskColors.textMuted,
            fontSize = 11.sp,
            fontFamily = KioskFont,
        )
    }
}

@Composable
fun PanelDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FormTokens.SectionGap)
            .height(1.dp)
            .background(KioskColors.borderSubtle),
    )
}

@Composable
fun ShieldMark() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(RadiusLg))
            .background(Brush.linearGradient(listOf(KioskColors.systemBlue, KioskColors.systemBlue))),
        contentAlignment = Alignment.Center,
    ) {
        Text("🛡", fontSize = 16.sp)
    }
}
