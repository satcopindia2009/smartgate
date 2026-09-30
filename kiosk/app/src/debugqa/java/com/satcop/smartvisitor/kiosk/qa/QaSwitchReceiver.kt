package com.satcop.smartvisitor.kiosk.qa

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Debug-only switches (adb). Package: com.satcop.smartvisitor.kiosk.debugqa. See /workspace/qa-debug/README.md.
 *  Fake camera:  -a ...debugqa.FAKE_CAMERA --ez enabled false
 *  OTP states:   -a ...debugqa.QA_OTP  --ez sendFail true | --es send ok|fail|limit|daily|wait
 *                --es verify ok|wrong|expired|locked|used | --ez visitorOtp true|false | --ez visitorSkip true|false | --ez reset true
 */
class QaSwitchReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.satcop.smartvisitor.kiosk.debugqa.QA_OTP" -> {
                val e = intent.extras
                if (e?.getBoolean("reset", false) == true) QaOtpState.reset()
                if (e?.containsKey("sendFail") == true) QaOtpState.send = if (e.getBoolean("sendFail")) "fail" else "ok"
                e?.getString("send")?.let { QaOtpState.send = it }
                e?.getString("verify")?.let { QaOtpState.verify = it }
                if (e?.containsKey("visitorOtp") == true) QaOtpState.visitorOtpEnabled = e.getBoolean("visitorOtp")
                if (e?.containsKey("visitorSkip") == true) QaOtpState.visitorOtpAllowSkip = e.getBoolean("visitorSkip")
                e?.getString("channel")?.let { QaOtpState.otpChannel = it }
            }
            else -> QaHooks.enabled = intent.getBooleanExtra("enabled", true)
        }
    }
}
