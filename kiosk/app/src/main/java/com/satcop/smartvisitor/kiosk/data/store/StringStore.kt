package com.satcop.smartvisitor.kiosk.data.store

import android.content.Context

/** Tiny key/value seam so persistence logic is unit-testable without Android. */
interface StringStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
}

class SharedPrefsStringStore(context: Context, name: String) : StringStore {
    private val prefs = context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    override fun get(key: String): String? = prefs.getString(key, null)

    // commit(): the value must survive an immediate process kill (apply() is async).
    override fun put(key: String, value: String) {
        prefs.edit().putString(key, value).commit()
    }
}

class MemoryStringStore : StringStore {
    private val map = mutableMapOf<String, String>()
    override fun get(key: String): String? = map[key]
    override fun put(key: String, value: String) { map[key] = value }
}
