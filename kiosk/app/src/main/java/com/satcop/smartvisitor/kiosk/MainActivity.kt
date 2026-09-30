package com.satcop.smartvisitor.kiosk

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.satcop.smartvisitor.kiosk.data.geo.CaptureGeo
import com.satcop.smartvisitor.kiosk.ui.KioskApp
import com.satcop.smartvisitor.kiosk.ui.theme.SatcopKioskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureGeo.install(applicationContext)
        // AC-PH: phone portrait only — do not restore FULL_USER.
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            // Draw under display cutouts/notches; Compose insets (statusBars/displayCutout) keep content clear of them.
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // AC-APP1: keep system bars visible; Material3 NavigationBar uses navigationBarsPadding.
        // Do not hide(navigationBars()) — immersive hide made homes feel like a web scroll page.
        setContent {
            SatcopKioskTheme {
                KioskApp()
            }
        }
    }
}
