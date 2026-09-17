package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
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
    name = "Gate phone portrait",
    device = "spec:width=390dp,height=844dp,dpi=420,orientation=portrait",
    showBackground = true,
    backgroundColor = 0xFF0F1115,
)
@Composable
fun KioskPhonePortraitPreview() {
    SatcopKioskTheme {
        KioskApp()
    }
}

@Preview(
    name = "Gate phone login",
    device = "spec:width=390dp,height=844dp,dpi=420,orientation=portrait",
    showBackground = true,
    backgroundColor = 0xFF0F1115,
)
@Composable
fun KioskPhoneLoginPreview() {
    SatcopKioskTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KioskColors.bg)
                .padding(16.dp),
        ) {
            LoginScreen(
                username = "",
                password = "",
                error = null,
                busy = false,
                compact = true,
                onUsername = {},
                onPassword = {},
                onSubmit = {},
            )
        }
    }
}

@Preview(
    name = "Host phone home",
    device = "spec:width=390dp,height=844dp,dpi=420,orientation=portrait",
    showBackground = true,
    backgroundColor = 0xFF0F1115,
)
@Composable
fun KioskPhoneHostHomePreview() {
    SatcopKioskTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KioskColors.bg)
                .padding(16.dp),
        ) {
            HostHomeScreen(
                displayName = "Pranay Host",
                schoolId = "SCH-PRANAY-01",
                staffId = "",
                compact = true,
                onOpenUrl = {},
            )
        }
    }
}
