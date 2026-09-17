package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Material compact / phone-portrait breakpoint. Tablet & landscape stay two-column. */
val CompactWidthBreakpoint = 600.dp

val LocalKioskCompact = compositionLocalOf { false }
