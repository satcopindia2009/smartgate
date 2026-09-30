package com.satcop.smartvisitor.kiosk.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1061b: ONE set of layout tokens for every Check-in step (type, details, photo/ID/consent/signature, outcome).
 *
 * Rules
 * - Horizontal screen margin is applied ONCE by the container ([ScreenHPad]); steps and their
 *   headers/labels/inputs/host control never add their own horizontal screen padding, so the
 *   "Visitor details" header and the fields share the same left/right edge.
 * - Heights are minimums only (heightIn(min = …)) so text can wrap at 1.3–2.0× font scale.
 */
object FormTokens {
    /** Left/right screen margin (matches Apple shell nav/title/sub inset). */
    val ScreenHPad: Dp = 16.dp

    /** Field label -> its input. */
    val LabelToField: Dp = 6.dp

    /** One field block -> the next field block. */
    val FieldToField: Dp = 12.dp

    /** Input -> its inline error text. */
    val ErrorGap: Dp = 4.dp

    /** Step title -> subtitle. */
    val TitleToSub: Dp = 4.dp

    /** Header block (title + subtitle) -> first field. */
    val HeaderToForm: Dp = 16.dp

    /** Between larger groups (fields -> action buttons, dividers, sections). */
    val SectionGap: Dp = 20.dp

    /** Between stacked action buttons / chips. */
    val ButtonGap: Dp = 10.dp

    /** Minimum touch target for inputs, menu rows, chips and buttons. */
    val MinTouch: Dp = 48.dp

    /** All spacing tokens (used by consistency tests). */
    val allSpacing: List<Dp>
        get() = listOf(LabelToField, FieldToField, ErrorGap, TitleToSub, HeaderToForm, SectionGap, ButtonGap)
}
