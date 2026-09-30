package com.satcop.smartvisitor.kiosk.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only switch (adb): turn the fake camera off to use a real camera on a device that has one.
 *   adb shell am broadcast -a com.satcop.smartvisitor.kiosk.debugqa.FAKE_CAMERA --ez enabled false -p com.satcop.smartvisitor.kiosk.debugqa
 */
class QaSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        QaHooks.enabled = intent.getBooleanExtra("enabled", true)
    }
}
