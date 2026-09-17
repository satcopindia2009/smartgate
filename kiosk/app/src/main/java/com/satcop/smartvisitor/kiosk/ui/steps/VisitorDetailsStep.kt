package com.satcop.smartvisitor.kiosk.ui.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
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
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusMd

@Composable
fun VisitorDetailsStep(
    draft: RegistrationDraft,
    hosts: List<Staff>,
    errors: Map<String, String>,
    onName: (String) -> Unit,
    onMobile: (String) -> Unit,
    onPurpose: (String) -> Unit,
    onHost: (String) -> Unit,
    onVehicle: (String) -> Unit,
    onAccompanying: (String) -> Unit,
    onNotes: (String) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    val compact = LocalKioskCompact.current
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Visitor details",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Name, mobile, purpose, and host",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (compact) {
            KioskField(
                label = "Full name",
                value = draft.visitorName,
                onValueChange = onName,
                placeholder = "e.g. Priya Sharma",
                error = errors[FieldKeys.VISITOR_NAME],
                modifier = Modifier.fillMaxWidth(),
            )
            KioskField(
                label = "Mobile",
                value = draft.mobile,
                onValueChange = onMobile,
                placeholder = "+91 98220 11122",
                error = errors[FieldKeys.MOBILE],
                keyboardType = KeyboardType.Phone,
                capitalization = KeyboardCapitalization.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KioskField(
                    label = "Full name",
                    value = draft.visitorName,
                    onValueChange = onName,
                    placeholder = "e.g. Priya Sharma",
                    error = errors[FieldKeys.VISITOR_NAME],
                    modifier = Modifier.weight(1f),
                )
                KioskField(
                    label = "Mobile",
                    value = draft.mobile,
                    onValueChange = onMobile,
                    placeholder = "+91 98220 11122",
                    error = errors[FieldKeys.MOBILE],
                    keyboardType = KeyboardType.Phone,
                    capitalization = KeyboardCapitalization.None,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        KioskField(
            label = "Purpose of visit",
            value = draft.purpose,
            onValueChange = onPurpose,
            placeholder = "e.g. PTM follow-up, Class 4B",
            error = errors[FieldKeys.PURPOSE],
            capitalization = KeyboardCapitalization.Sentences,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        Text(
            text = "Person to meet (host)",
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
        )
        HostGrid(
            hosts = hosts,
            selectedId = draft.hostId,
            onSelect = onHost,
            columns = if (compact) 2 else 3,
        )
        if (errors[FieldKeys.HOST_ID] != null) {
            Text(
                text = errors[FieldKeys.HOST_ID].orEmpty(),
                color = KioskColors.red,
                fontSize = 12.sp,
                fontFamily = KioskFont,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (compact) {
            KioskField(
                label = "Vehicle number (optional)",
                value = draft.vehicleNumber,
                onValueChange = onVehicle,
                placeholder = "MH12AB1234",
                capitalization = KeyboardCapitalization.Characters,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )
            KioskField(
                label = "Accompanying (optional)",
                value = draft.accompanyingCount,
                onValueChange = onAccompanying,
                placeholder = "0",
                error = errors[FieldKeys.ACCOMPANYING],
                keyboardType = KeyboardType.Number,
                capitalization = KeyboardCapitalization.None,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                KioskField(
                    label = "Vehicle number (optional)",
                    value = draft.vehicleNumber,
                    onValueChange = onVehicle,
                    placeholder = "MH12AB1234",
                    capitalization = KeyboardCapitalization.Characters,
                    modifier = Modifier.weight(1f),
                )
                KioskField(
                    label = "Accompanying (optional)",
                    value = draft.accompanyingCount,
                    onValueChange = onAccompanying,
                    placeholder = "0",
                    error = errors[FieldKeys.ACCOMPANYING],
                    keyboardType = KeyboardType.Number,
                    capitalization = KeyboardCapitalization.None,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        KioskField(
            label = "Notes (optional)",
            value = draft.notes,
            onValueChange = onNotes,
            placeholder = "Gate remarks",
            capitalization = KeyboardCapitalization.Sentences,
            singleLine = false,
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        )

        PanelDivider()
        if (compact) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    .padding(top = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                KioskGhostButton(text = "Back", onClick = onBack)
                KioskPrimaryButton(text = "Continue", onClick = onContinue)
            }
        }
    }
}

@Composable
private fun HostGrid(
    hosts: List<Staff>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    columns: Int = 3,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        hosts.chunked(columns).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { staff ->
                    HostCard(
                        staff = staff,
                        selected = staff.id == selectedId,
                        onClick = { onSelect(staff.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(columns - row.size) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HostCard(
    staff: Staff,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) KioskColors.purple else KioskColors.border
    val bg = if (selected) KioskColors.purpleDim else KioskColors.bg
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(RadiusMd))
            .background(bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = border,
                shape = RoundedCornerShape(RadiusMd),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = staff.name,
            color = KioskColors.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = staff.roleTitle,
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
