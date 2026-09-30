package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.ui.LocalKioskCompact
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.PanelDivider
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.components.FormFields
import com.satcop.smartvisitor.kiosk.ui.components.FormHeader
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens

@Composable
fun VisitorDetailsStep(
    draft: RegistrationDraft,
    hosts: List<Staff>,
    errors: Map<String, String>,
    autofetchHint: String? = null,
    autofetchBusy: Boolean = false,
    hostsLoading: Boolean = false,
    onName: (String) -> Unit,
    onMobile: (String) -> Unit,
    onCompany: (String) -> Unit = {},
    onPurpose: (String) -> Unit,
    onHost: (String) -> Unit,
    onVehicle: (String) -> Unit,
    onAccompanying: (String) -> Unit,
    onNotes: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val compact = LocalKioskCompact.current
    // No horizontal padding here: the container applies FormTokens.ScreenHPad once (header == field left edge).
    Column(Modifier.fillMaxWidth()) {
        FormHeader(title = "Visitor details", subtitle = "Name, mobile, purpose, and host")

        FormFields {
            TwoUp(compact) { mod ->
                KioskField(
                    label = "Full name",
                    value = draft.visitorName,
                    onValueChange = onName,
                    placeholder = "Visitor full name",
                    error = errors[FieldKeys.VISITOR_NAME],
                    modifier = mod,
                )
                KioskField(
                    label = "Mobile",
                    value = draft.mobile,
                    onValueChange = onMobile,
                    placeholder = "+91 98220 11122",
                    error = errors[FieldKeys.MOBILE],
                    keyboardType = KeyboardType.Phone,
                    capitalization = KeyboardCapitalization.None,
                    modifier = mod,
                )
            }

            if (autofetchBusy || !autofetchHint.isNullOrBlank()) {
                Text(
                    text = when {
                        autofetchBusy -> "Auto-fetch…"
                        else -> autofetchHint.orEmpty()
                    },
                    color = KioskColors.cyanBright,
                    fontSize = 12.sp,
                    fontFamily = KioskFont,
                )
            }
            KioskField(
                label = "Company (optional)",
                value = draft.company,
                onValueChange = onCompany,
                placeholder = "Org / vendor company",
                modifier = Modifier.fillMaxWidth(),
            )
            KioskField(
                label = "Purpose of visit",
                value = draft.purpose,
                onValueChange = onPurpose,
                placeholder = "e.g. PTM follow-up, Class 4B",
                error = errors[FieldKeys.PURPOSE],
                capitalization = KeyboardCapitalization.Sentences,
                modifier = Modifier.fillMaxWidth(),
            )

            HostDropdown(
                hosts = hosts,
                selectedId = draft.hostId,
                error = errors[FieldKeys.HOST_ID],
                loading = hostsLoading,
                onSelect = onHost,
            )

            TwoUp(compact) { mod ->
                KioskField(
                    label = "Vehicle number (optional)",
                    value = draft.vehicleNumber,
                    onValueChange = onVehicle,
                    placeholder = "MH12AB1234",
                    capitalization = KeyboardCapitalization.Characters,
                    modifier = mod,
                )
                KioskField(
                    label = "Accompanying (optional)",
                    value = draft.accompanyingCount,
                    onValueChange = onAccompanying,
                    placeholder = "0",
                    error = errors[FieldKeys.ACCOMPANYING],
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                    modifier = mod,
                )
            }

            KioskField(
                label = "Notes (optional)",
                value = draft.notes,
                onValueChange = onNotes,
                placeholder = "Gate remarks",
                capitalization = KeyboardCapitalization.Sentences,
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PanelDivider()
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FormTokens.SectionGap),
                verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
            ) {
                KioskGhostButton(
                    text = "Back",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth(),
                )
                KioskPrimaryButton(
                    text = "Continue",
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = FormTokens.SectionGap),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                KioskGhostButton(text = "Back", onClick = onBack)
                KioskPrimaryButton(text = "Continue", onClick = onContinue)
            }
        }
    }
}

/** Two fields: stacked (phone / large font) or side by side (wide, non-compact) with the same token gap. */
@Composable
private fun TwoUp(compact: Boolean, content: @Composable (Modifier) -> Unit) {
    if (compact) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        ) { content(Modifier.weight(1f)) }
    }
}
