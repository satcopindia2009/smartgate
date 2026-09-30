@file:OptIn(ExperimentalMaterial3Api::class)

package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.ui.components.FormLabel
import com.satcop.smartvisitor.kiosk.ui.theme.ControlShape
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.kioskTextFieldColors

/**
 * 1061b: searchable "Person to meet (host)" dropdown (replaces the 2-column card grid).
 * Items show name (bold) + role (muted); typing filters by name/role. Selection, validation and
 * downstream use are unchanged: [onSelect] receives the Staff id (viewModel.selectHost).
 */
@Composable
fun HostDropdown(
    hosts: List<Staff>,
    selectedId: String?,
    error: String?,
    loading: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FormLabel(HostPicker.LABEL)
        when (HostPicker.viewState(hosts, loading)) {
            HostPicker.ViewState.LOADING -> HostMessage(HostPicker.LOADING, error != null)
            HostPicker.ViewState.EMPTY -> HostMessage(HostPicker.EMPTY, error != null)
            HostPicker.ViewState.READY -> HostDropdownReady(hosts, selectedId, error != null, onSelect)
        }
        if (error != null) {
            Text(
                text = error,
                color = KioskColors.errorText,
                fontSize = 12.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = FormTokens.ErrorGap),
            )
        }
    }
}

@Composable
private fun HostMessage(text: String, isError: Boolean) {
    Text(
        text = text,
        color = KioskColors.textMuted,
        fontSize = 14.sp,
        fontFamily = KioskFont,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = FormTokens.MinTouch)
            .clip(ControlShape)
            .border(1.dp, if (isError) KioskColors.errorText else KioskColors.inputBorder, ControlShape)
            .background(KioskColors.inputBg)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics { contentDescription = HostPicker.LABEL + ". " + text },
    )
}

@Composable
private fun HostDropdownReady(
    hosts: List<Staff>,
    selectedId: String?,
    hasError: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // null = show the selected host's label; non-null = user is typing a search query.
    var query by remember { mutableStateOf<String?>(null) }
    val selectedLabel = HostPicker.selectedLabel(hosts, selectedId)
    val text = query ?: selectedLabel.orEmpty()
    val matches = HostPicker.filter(hosts, query.orEmpty())

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextField(
            value = text,
            onValueChange = {
                query = it
                expanded = true
            },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .heightIn(min = FormTokens.MinTouch)
                .border(1.dp, if (hasError) KioskColors.errorText else KioskColors.inputBorder, ControlShape)
                .clip(ControlShape)
                .semantics { contentDescription = HostPicker.LABEL },
            textStyle = TextStyle(color = KioskColors.inputText, fontFamily = KioskFont, fontSize = 16.sp),
            placeholder = { Text(HostPicker.PLACEHOLDER, color = KioskColors.inputHint, fontFamily = KioskFont) },
            singleLine = false,
            maxLines = 3,
            isError = hasError,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
            ),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = ControlShape,
            colors = kioskTextFieldColors(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = {
                expanded = false
                query = null
            },
            modifier = Modifier.background(KioskColors.card),
        ) {
            if (matches.isEmpty()) {
                DropdownMenuItem(
                    text = { Text(HostPicker.NO_MATCH, color = KioskColors.textMuted, fontFamily = KioskFont) },
                    onClick = {},
                    enabled = false,
                    modifier = Modifier.heightIn(min = FormTokens.MinTouch),
                )
            }
            matches.forEach { staff ->
                val selected = staff.id == selectedId
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = staff.name,
                                color = KioskColors.text,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                fontFamily = KioskFont,
                            )
                            if (staff.roleTitle.isNotBlank()) {
                                Text(
                                    text = staff.roleTitle,
                                    color = KioskColors.textMuted,
                                    fontSize = 12.sp,
                                    fontFamily = KioskFont,
                                )
                            }
                        }
                    },
                    onClick = {
                        onSelect(staff.id)
                        query = null
                        expanded = false
                    },
                    modifier = Modifier
                        .heightIn(min = FormTokens.MinTouch)
                        .background(if (selected) KioskColors.purpleDim else KioskColors.card)
                        .semantics { contentDescription = HostPicker.label(staff) },
                )
            }
        }
    }
}
