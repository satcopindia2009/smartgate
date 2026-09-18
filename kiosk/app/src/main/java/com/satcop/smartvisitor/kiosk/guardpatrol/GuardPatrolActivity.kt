package com.satcop.smartvisitor.kiosk.guardpatrol

import android.content.pm.ActivityInfo
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.satcop.smartvisitor.kiosk.guardpatrol.ui.GuardPatrolApp
import com.satcop.smartvisitor.kiosk.ui.theme.SatcopKioskTheme

/**
 * Guard Patrol shell (SCH-DEMO-01 · guard/guard123).
 * Not exported as a product launcher — reached via JWT role from MainActivity (1-role APK).
 */
class GuardPatrolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.navigationBars())
        }
        setContent {
            SatcopKioskTheme {
                GuardPatrolApp()
            }
        }
    }
}
