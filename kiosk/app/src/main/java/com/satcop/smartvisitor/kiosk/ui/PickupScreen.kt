package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.AuthorizedPickup
import com.satcop.smartvisitor.kiosk.data.model.PickupOut
import com.satcop.smartvisitor.kiosk.data.model.PickupReasons
import com.satcop.smartvisitor.kiosk.data.model.StudentOut
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusLg
import com.satcop.smartvisitor.kiosk.ui.theme.RadiusSm

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PickupScreen(
    query: String,
    students: List<StudentOut>,
    selectedStudent: StudentOut?,
    authorized: List<AuthorizedPickup>,
    selectedCollector: AuthorizedPickup?,
    pickupReason: String,
    reasonOther: String,
    pickup: PickupOut?,
    busy: Boolean,
    compact: Boolean,
    gateName: String,
    onQuery: (String) -> Unit,
    onSelectStudent: (StudentOut) -> Unit,
    onSelectCollector: (AuthorizedPickup) -> Unit,
    onReason: (String) -> Unit,
    onReasonOther: (String) -> Unit,
    onStart: () -> Unit,
    onConsent: () -> Unit,
    onRelease: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        Text(
            text = "Student pickup",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Search live students, match an authorized collector, then start pickup at $gateName.",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        KioskField(
            label = "Student search",
            value = query,
            onValueChange = onQuery,
            placeholder = "Name, class, or id",
            capitalization = KeyboardCapitalization.Words,
            modifier = Modifier.fillMaxWidth(),
        )
        if (students.isEmpty()) {
            Text(
                text = "No students match",
                color = KioskColors.textMuted,
                fontSize = 14.sp,
                fontFamily = KioskFont,
            )
        } else {
            students.forEach { student ->
                SelectRow(
                    title = student.name,
                    subtitle = listOfNotNull(
                        student.classSection().takeIf { it != "—" },
                        student.id,
                        if (student.legalHold) "legal hold" else null,
                    ).joinToString(" · "),
                    selected = selectedStudent?.id == student.id,
                    onClick = { onSelectStudent(student) },
                )
            }
        }
        if (selectedStudent != null) {
            Text(
                text = "Authorized pickup",
                color = KioskColors.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = KioskFont,
            )
            if (authorized.isEmpty()) {
                Text(
                    text = "No authorized collectors on file",
                    color = KioskColors.textMuted,
                    fontSize = 14.sp,
                    fontFamily = KioskFont,
                )
            } else {
                authorized.filter { it.active }.forEach { person ->
                    SelectRow(
                        title = person.name,
                        subtitle = listOfNotNull(person.relation, person.mobile).joinToString(" · "),
                        selected = selectedCollector?.id == person.id,
                        onClick = { onSelectCollector(person) },
                    )
                }
            }
            Text(
                text = "Reason",
                color = KioskColors.textMuted,
                fontSize = 12.sp,
                fontFamily = KioskFont,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PickupReasons.chips.forEach { (value, label) ->
                    val selected = pickupReason == value
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(RadiusSm))
                            .background(if (selected) KioskColors.cyanDim else KioskColors.bg)
                            .border(
                                1.dp,
                                if (selected) KioskColors.cyan else KioskColors.border,
                                RoundedCornerShape(RadiusSm),
                            )
                            .clickable { onReason(value) }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(label, color = KioskColors.text, fontSize = 13.sp, fontFamily = KioskFont)
                    }
                }
            }
            if (pickupReason == "other") {
                KioskField(
                    label = "Other reason",
                    value = reasonOther,
                    onValueChange = onReasonOther,
                    placeholder = "Short reason",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            KioskPrimaryButton(
                text = if (busy) "Starting…" else "Start pickup",
                onClick = onStart,
                enabled = !busy && selectedCollector != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            )
        }
        if (pickup != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(RadiusLg))
                    .background(KioskColors.bg)
                    .border(1.dp, KioskColors.border, RoundedCornerShape(RadiusLg))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${pickup.status} · ${pickup.id}",
                    color = KioskColors.cyanBright,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = KioskFont,
                )
                Text(
                    text = listOfNotNull(pickup.collectorName, pickup.collectorRelation, pickup.pickupReason)
                        .joinToString(" · "),
                    color = KioskColors.text,
                    fontSize = 14.sp,
                    fontFamily = KioskFont,
                )
                if (pickup.pickupConsentAt.isNullOrBlank()) {
                    KioskGhostButton(
                        text = if (busy) "Recording…" else "Record pickup consent",
                        onClick = onConsent,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else if (pickup.status != "Released") {
                    KioskPrimaryButton(
                        text = if (busy) "Releasing…" else "Capture photo & release",
                        onClick = onRelease,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(
                        text = "Released · student may leave with ${pickup.collectorName ?: "collector"}",
                        color = KioskColors.greenBright,
                        fontSize = 13.sp,
                        fontFamily = KioskFont,
                    )
                }
            }
        }
        KioskGhostButton(
            text = "Back to register",
            onClick = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
    }
}

@Composable
private fun SelectRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(RadiusLg))
            .background(if (selected) KioskColors.purpleDim else KioskColors.bg)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) KioskColors.purple else KioskColors.border,
                shape = RoundedCornerShape(RadiusLg),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            color = KioskColors.text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = subtitle,
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
