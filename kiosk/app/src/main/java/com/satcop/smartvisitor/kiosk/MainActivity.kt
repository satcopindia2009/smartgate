package com.satcop.smartvisitor.kiosk

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.satcop.smartvisitor.kiosk.data.geo.CaptureGeo
import com.satcop.smartvisitor.kiosk.ui.KioskApp
import com.satcop.smartvisitor.kiosk.ui.theme.SatcopKioskTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CaptureGeo.install(applicationContext)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        setContent {
            SatcopKioskTheme {
                KioskApp()
            }
        }
    }
}
