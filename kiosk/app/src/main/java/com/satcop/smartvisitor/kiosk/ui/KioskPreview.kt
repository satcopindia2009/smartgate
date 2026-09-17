package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.satcop.smartvisitor.kiosk.ui.theme.SatcopKioskTheme

@Preview(
    name = "Gate tablet landscape",
    device = "spec:width=1280dp,height=800dp,dpi=240,orientation=landscape",
    showBackground = true,
    backgroundColor = 0xFF0F1115,
)
@Composable
fun KioskWave1Preview() {
    SatcopKioskTheme {
        KioskApp()
    }
}

@Preview(
    name = "Demo hub phone portrait",
    device = "spec:width=411dp,height=891dp,dpi=420,orientation=portrait",
    showBackground = true,
    backgroundColor = 0xFF0F1115,
)
@Composable
fun KioskDemoHubPhonePreview() {
    SatcopKioskTheme {
        KioskApp()
    }
}
