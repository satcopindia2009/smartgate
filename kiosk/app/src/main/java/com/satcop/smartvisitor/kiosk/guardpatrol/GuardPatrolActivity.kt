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
 * Isolated Phase 2 Guard Patrol demo entry.
 * Separate LAUNCHER — does not open Phase 1 kiosk/host/visitor flows (AC-GP6).
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
