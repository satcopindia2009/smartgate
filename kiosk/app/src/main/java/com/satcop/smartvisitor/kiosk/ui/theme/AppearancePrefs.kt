package com.satcop.smartvisitor.kiosk.ui.theme

import android.content.Context
import com.satcop.smartvisitor.kiosk.data.store.SharedPrefsStringStore
import com.satcop.smartvisitor.kiosk.data.store.StringStore

enum class AppearanceMode {
    LIGHT,
    DARK,
    AUTO,
}

object AppearancePrefs {
    const val PREFS = "satcop_appearance"
    const val KEY = "appearance_mode"

    /** Default = System (AUTO). Unknown/corrupt values also fall back to System. */
    fun loadFrom(store: StringStore): AppearanceMode {
        val raw = store.get(KEY) ?: return AppearanceMode.AUTO
        return runCatching { AppearanceMode.valueOf(raw) }.getOrDefault(AppearanceMode.AUTO)
    }

    fun saveTo(store: StringStore, mode: AppearanceMode) = store.put(KEY, mode.name)

    fun load(context: Context): AppearanceMode = loadFrom(SharedPrefsStringStore(context, PREFS))

    fun save(context: Context, mode: AppearanceMode) = saveTo(SharedPrefsStringStore(context, PREFS), mode)
}
