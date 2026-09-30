package com.satcop.smartvisitor.kiosk

import com.satcop.smartvisitor.kiosk.data.store.MemoryStringStore
import com.satcop.smartvisitor.kiosk.ui.theme.AppearanceMode
import com.satcop.smartvisitor.kiosk.ui.theme.AppearancePrefs
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearancePrefsTest {
    @Test
    fun defaultIsSystem() {
        assertEquals(AppearanceMode.AUTO, AppearancePrefs.loadFrom(MemoryStringStore()))
    }

    @Test
    fun choicePersistsAcrossReload() {
        val store = MemoryStringStore()
        for (m in AppearanceMode.values()) {
            AppearancePrefs.saveTo(store, m)
            assertEquals(m, AppearancePrefs.loadFrom(store)) // "restart" = fresh load from same backing store
        }
    }

    @Test
    fun corruptValueFallsBackToSystem() {
        val store = MemoryStringStore()
        store.put(AppearancePrefs.KEY, "PURPLE")
        assertEquals(AppearanceMode.AUTO, AppearancePrefs.loadFrom(store))
    }
}
