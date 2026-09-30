package com.satcop.smartvisitor.kiosk.ui.notify

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.HostFeedItem
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

/** Foreground popup: "New visitor: <name> for <purpose>" + Approve / Reject / Later. */
@Composable
fun HostAlertDialog(
    alert: HostFeedItem,
    more: Int,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onLater: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = KioskColors.card,
        titleContentColor = KioskColors.text,
        textContentColor = KioskColors.text,
        title = {
            Text("New visitor", fontFamily = KioskFont, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    alert.visitorName ?: "A visitor",
                    color = KioskColors.text,
                    fontFamily = KioskFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                )
                Text(
                    "for ${alert.purpose?.takeIf { it.isNotBlank() } ?: "a visit"}",
                    color = KioskColors.text,
                    fontFamily = KioskFont,
                    fontSize = 15.sp,
                )
                if (!alert.gateLabel.isNullOrBlank()) {
                    Text("Gate · ${alert.gateLabel}", color = KioskColors.textMuted, fontFamily = KioskFont, fontSize = 13.sp)
                }
                if (more > 0) {
                    Text("+$more more waiting", color = KioskColors.systemOrange, fontFamily = KioskFont, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Row(Modifier.fillMaxWidth().padding(end = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onLater) { Text("Later", color = KioskColors.textMuted, fontFamily = KioskFont) }
                TextButton(onClick = onReject) { Text("Reject", color = KioskColors.systemRed, fontFamily = KioskFont) }
                TextButton(onClick = onApprove) {
                    Text("Approve", color = KioskColors.systemBlue, fontFamily = KioskFont, fontWeight = FontWeight.Bold)
                }
            }
        },
    )
}
