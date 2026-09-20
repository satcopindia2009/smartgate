package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/**
 * AC-PH1/PH2 (2026-09-20): phone-only · portrait.
 * Compact layout is the only product surface — no tablet/landscape two-column shell.
 */
val CompactWidthBreakpoint = 600.dp

val LocalKioskCompact = compositionLocalOf { true }
