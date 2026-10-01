package com.satcop.smartvisitor.kiosk.ui

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 1070 crashed ~12 s after launch: the ticker coroutine in `init` ran before the `_clock` field existed
 * (property declared below the init block) -> NullPointerException in tickClock. This builds the REAL
 * KioskViewModel on an immediate main dispatcher (same as viewModelScope on a device) and runs past the first ticks.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KioskViewModelLaunchTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val uncaught = mutableListOf<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, e -> uncaught += e }
    }

    @After fun tearDown() {
        Thread.setDefaultUncaughtExceptionHandler(previousHandler)
        Dispatchers.resetMain()
    }

    @Test fun constructingTheViewModelStartsTheClockWithoutCrashing() = runTest(dispatcher) {
        val vm = KioskViewModel()
        // The first tick runs inside the constructor (immediate dispatcher): the clock must already show a time.
        assertTrue("clock empty right after construction: '${vm.clock.value}'", vm.clock.value.endsWith(" IST"))
        val first = vm.clock.value
        advanceTimeBy(15_000)
        assertTrue(vm.clock.value.endsWith(" IST"))
        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
        assertFalse(uncaught.any { it is NullPointerException })
        assertEquals(first.takeLast(4), vm.clock.value.takeLast(4))
    }

    /** Declaration-order guard: every property that init-launched coroutines touch must be declared above `init {`. */
    @Test fun clockFlowIsDeclaredBeforeInit() {
        val src = File("src/main/java/com/satcop/smartvisitor/kiosk/ui/KioskViewModel.kt").readText()
        val initAt = src.indexOf("    init {\n        // Cold start")
        assertTrue(initAt > 0)
        for (field in listOf("private val _state =", "private val _clock =", "private val clockFmt =")) {
            val at = src.indexOf(field)
            assertTrue("$field must be declared before init", at in 0 until initAt)
        }
        assertNotEquals(-1, src.indexOf("val clock: StateFlow<String>"))
        assertTrue(src.indexOf("val clock: StateFlow<String>") < initAt)
    }
}
