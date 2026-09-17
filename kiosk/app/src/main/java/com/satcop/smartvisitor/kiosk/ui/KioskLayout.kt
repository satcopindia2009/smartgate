package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.dp

/** Material compact / phone-portrait breakpoint. Tablet & landscape stay two-column. */
val CompactWidthBreakpoint = 600.dp

val LocalKioskCompact = compositionLocalOf { false }

data class DemoHubLink(
    val label: String,
    val url: String,
)

/** Seed/demo surfaces opened via ACTION_VIEW. Not part of the kiosk API contract. */
object DemoHubLinks {
    val AFTER_HOURS = DemoHubLink(
        label = "After-hours",
        url = "https://simon-configure-hiking-cms.trycloudflare.com",
    )
    val HOST_AFTERHOURS = DemoHubLink(
        label = "Host #afterhours",
        url = "https://england-content-resulting-heavily.trycloudflare.com/#afterhours",
    )
    val VISITOR_QR = DemoHubLink(
        label = "Visitor QR",
        url = "https://ethernet-prairie-carefully-furnishings.trycloudflare.com/?passId=P-7K88",
    )
    val ESCORT = DemoHubLink(
        label = "Escort",
        url = "https://votes-carlos-charter-damaged.trycloudflare.com",
    )

    val all: List<DemoHubLink> = listOf(AFTER_HOURS, HOST_AFTERHOURS, VISITOR_QR, ESCORT)
}
