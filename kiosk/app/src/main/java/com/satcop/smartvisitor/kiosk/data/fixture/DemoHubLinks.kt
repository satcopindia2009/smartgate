package com.satcop.smartvisitor.kiosk.data.fixture

/**
 * Public web previews for the phone Demo hub. Tunnels are ephemeral —
 * if they 502, stay on this APK (FIXTURES) for the visitor-gate path.
 */
data class DemoHubItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val url: String? = null,
    val inApp: Boolean = false,
)

object DemoHubLinks {
    const val AFTER_HOURS_GATE = "https://simon-configure-hiking-cms.trycloudflare.com"
    const val HOST_APPROVE_AFTERHOURS =
        "https://england-content-resulting-heavily.trycloudflare.com/#afterhours"
    const val VISITOR_QR_P7K88 =
        "https://ethernet-prairie-carefully-furnishings.trycloudflare.com/?passId=P-7K88"
    const val ESCORT_ZONES = "https://votes-carlos-charter-damaged.trycloudflare.com"

    const val TUNNEL_NOTE =
        "Tunnels are ephemeral. If a preview returns 502, stay on this app — FIXTURES still work."

    val gateCheckIn = DemoHubItem(
        id = "gate",
        title = "Visitor gate (this app)",
        subtitle = "Register a visitor on this phone · steps 1–4",
        emoji = "🚪",
        inApp = true,
    )

    val afterHoursGate = DemoHubItem(
        id = "after-hours",
        title = "After-hours gate",
        subtitle = "Public web preview · after-hours entry",
        emoji = "🌙",
        url = AFTER_HOURS_GATE,
    )

    val hostApprove = DemoHubItem(
        id = "host-approve",
        title = "Host approve (After-hours tab)",
        subtitle = "Public host-web · opens After-hours tab",
        emoji = "✔",
        url = HOST_APPROVE_AFTERHOURS,
    )

    val visitorQr = DemoHubItem(
        id = "visitor-qr",
        title = "Visitor QR P-7K88",
        subtitle = "Public visitor pass preview",
        emoji = "▣",
        url = VISITOR_QR_P7K88,
    )

    val escortZones = DemoHubItem(
        id = "escort",
        title = "Escort / zones",
        subtitle = "Public escort and zone preview",
        emoji = "🗺",
        url = ESCORT_ZONES,
    )

    val items: List<DemoHubItem> = listOf(
        gateCheckIn,
        afterHoursGate,
        hostApprove,
        visitorQr,
        escortZones,
    )

    val webPreviews: List<DemoHubItem> = items.filter { it.url != null }
}
