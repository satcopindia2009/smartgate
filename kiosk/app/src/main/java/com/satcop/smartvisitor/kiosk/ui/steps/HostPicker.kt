package com.satcop.smartvisitor.kiosk.ui.steps

import com.satcop.smartvisitor.kiosk.data.model.Staff
import java.util.Locale

/** 1061b: pure (Compose-free) logic behind the Check-in "Person to meet (host)" dropdown. */
object HostPicker {
    const val LABEL = "Person to meet (host)"
    const val PLACEHOLDER = "Select or search host"
    const val LOADING = "Loading hosts…"
    const val EMPTY = "No hosts are available right now. Please ask the school office to add a host."
    const val NO_MATCH = "No matching host"

    enum class ViewState { LOADING, EMPTY, READY }

    fun viewState(hosts: List<Staff>, loading: Boolean): ViewState = when {
        hosts.isNotEmpty() -> ViewState.READY
        loading -> ViewState.LOADING
        else -> ViewState.EMPTY
    }

    /** "Name · Role" (just "Name" when the role is blank). */
    fun label(staff: Staff): String {
        val name = staff.name.trim()
        val role = staff.roleTitle.trim()
        return if (role.isEmpty()) name else "$name · $role"
    }

    /** N1: distinct, non-blank departments of the active hosts (the server sets department = roleTitle), A-Z. */
    fun departments(hosts: List<Staff>): List<String> =
        hosts.filter { it.active }.map { it.roleTitle.trim() }.filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }

    fun filterDepartments(departments: List<String>, query: String): List<String> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return departments
        return departments.filter { d -> terms.all { d.lowercase(Locale.ROOT).contains(it) } }
    }

    const val DEPARTMENTS_HEADER = "Departments"

    /** Label of the selected host, or null when nothing (or an unknown id) is selected. */
    fun selectedLabel(hosts: List<Staff>, selectedId: String?): String? {
        if (selectedId.isNullOrBlank()) return null
        return hosts.firstOrNull { it.id == selectedId }?.let(::label)
    }

    /**
     * Type-to-filter: every whitespace separated term must occur (case-insensitive) in the name or the role.
     * Blank query keeps everything. Order is preserved (same order as the server list).
     */
    fun filter(hosts: List<Staff>, query: String): List<Staff> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return hosts
        return hosts.filter { staff ->
            val hay = (staff.name + " " + staff.roleTitle).lowercase(Locale.ROOT)
            terms.all { hay.contains(it) }
        }
    }
}
