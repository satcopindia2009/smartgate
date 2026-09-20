package com.satcop.smartvisitor.kiosk.ui.theme

import android.content.Context
import android.content.SharedPreferences

enum class AppearanceMode {
    LIGHT,
    DARK,
    AUTO,
}

object AppearancePrefs {
    private const val PREFS = "satcop_appearance"
    private const val KEY = "appearance_mode"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): AppearanceMode {
        val raw = prefs(context).getString(KEY, AppearanceMode.AUTO.name) ?: AppearanceMode.AUTO.name
        return runCatching { AppearanceMode.valueOf(raw) }.getOrDefault(AppearanceMode.AUTO)
    }

    fun save(context: Context, mode: AppearanceMode) {
        prefs(context).edit().putString(KEY, mode.name).apply()
    }
}
