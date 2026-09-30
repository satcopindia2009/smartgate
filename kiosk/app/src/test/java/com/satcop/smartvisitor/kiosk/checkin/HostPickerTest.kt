package com.satcop.smartvisitor.kiosk.checkin

import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.ui.steps.HostPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HostPickerTest {
    private fun s(id: String, name: String, role: String) =
        Staff(id = id, schoolId = "S1", name = name, roleTitle = role, active = true)

    private val hosts = listOf(
        s("H1", "Meera Kulkarni", "Principal"),
        s("H2", "Rahul Deshpande", "Admin Officer"),
        s("H3", "Anita Joshi", "Primary Coordinator"),
        s("H4", "Sanjay Patil", "Accounts"),
        s("H5", "Vikram More", "Transport"),
    )

    @Test fun labelIsNameAndRole() {
        assertEquals("Anita Joshi · Primary Coordinator", HostPicker.label(hosts[2]))
    }

    @Test fun labelWithoutRoleIsJustName() {
        assertEquals("QA Host", HostPicker.label(s("H9", " QA Host ", "  ")))
    }

    @Test fun selectedLabelResolvesById() {
        assertEquals("Meera Kulkarni · Principal", HostPicker.selectedLabel(hosts, "H1"))
        assertNull(HostPicker.selectedLabel(hosts, null))
        assertNull(HostPicker.selectedLabel(hosts, ""))
        assertNull(HostPicker.selectedLabel(hosts, "nope"))
    }

    @Test fun blankQueryKeepsAllInOrder() {
        assertEquals(hosts, HostPicker.filter(hosts, ""))
        assertEquals(hosts, HostPicker.filter(hosts, "   "))
    }

    @Test fun filterMatchesNameCaseInsensitive() {
        assertEquals(listOf("H3"), HostPicker.filter(hosts, "anita").map { it.id })
        assertEquals(listOf("H3"), HostPicker.filter(hosts, "JOSHI").map { it.id })
    }

    @Test fun filterMatchesRole() {
        assertEquals(listOf("H1"), HostPicker.filter(hosts, "principal").map { it.id })
        assertEquals(listOf("H2"), HostPicker.filter(hosts, "admin").map { it.id })
    }

    @Test fun filterAllTermsMustMatch() {
        assertEquals(listOf("H3"), HostPicker.filter(hosts, "anita coord").map { it.id })
        assertTrue(HostPicker.filter(hosts, "anita principal").isEmpty())
    }

    @Test fun filterNoMatchIsEmpty() {
        assertTrue(HostPicker.filter(hosts, "zzz").isEmpty())
    }

    @Test fun filterPreservesServerOrder() {
        // "a" is in all names/roles; order must not change.
        assertEquals(hosts.map { it.id }, HostPicker.filter(hosts, "a").map { it.id })
    }

    @Test fun viewStates() {
        assertEquals(HostPicker.ViewState.READY, HostPicker.viewState(hosts, loading = true))
        assertEquals(HostPicker.ViewState.READY, HostPicker.viewState(hosts, loading = false))
        assertEquals(HostPicker.ViewState.LOADING, HostPicker.viewState(emptyList(), loading = true))
        assertEquals(HostPicker.ViewState.EMPTY, HostPicker.viewState(emptyList(), loading = false))
    }

    @Test fun emptyMessageIsPlainEnglish() {
        assertTrue(HostPicker.EMPTY.contains("No hosts"))
        assertTrue(!HostPicker.EMPTY.contains("http", ignoreCase = true))
        assertEquals("Person to meet (host)", HostPicker.LABEL)
    }
}
