package com.satcop.smartvisitor.kiosk.ui.addvisitor

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.addvisitor.ActiveVisit
import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
import com.satcop.smartvisitor.kiosk.ui.components.FormFields
import com.satcop.smartvisitor.kiosk.ui.components.FormHeader
import com.satcop.smartvisitor.kiosk.ui.components.FormLabel
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.PanelDivider
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusChip
import com.satcop.smartvisitor.kiosk.ui.components.SgStatusKind
import com.satcop.smartvisitor.kiosk.ui.components.sgCardSurface
import com.satcop.smartvisitor.kiosk.ui.steps.HostDropdown
import com.satcop.smartvisitor.kiosk.ui.theme.ControlShape
import com.satcop.smartvisitor.kiosk.ui.theme.FormTokens
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import com.satcop.smartvisitor.kiosk.ui.theme.PillShape
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Screen 1 (+ blocked / already-inside / lookup-failed notices): ONLY the mobile number. */
@Composable
fun AddVisitorNumberStep(
    state: AddVisitorState,
    onMobile: (String) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onCloseNotice: () -> Unit,
    onCheckout: (ActiveVisit) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FormHeader(title = "Add visitor", subtitle = "Enter the mobile number of the visitor or vendor.")
        when (val n = state.notice) {
            AvNotice.Blocked -> BlockedPanel(onClose = onCloseNotice)
            else -> {
                FormFields {
                    KioskField(
                        label = "Mobile number",
                        value = state.mobileInput,
                        onValueChange = onMobile,
                        placeholder = "10-digit mobile number",
                        error = state.mobileError,
                        keyboardType = KeyboardType.Phone,
                        capitalization = KeyboardCapitalization.None,
                        autoCorrect = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (n) {
                        is AvNotice.Inside -> InsideBanner(n.active, state.checkoutBusy, onCheckout, onCloseNotice)
                        is AvNotice.Failed -> Text(
                            text = n.message,
                            color = KioskColors.errorText,
                            fontSize = 14.sp,
                            fontFamily = KioskFont,
                        )
                        else -> Unit
                    }
                }
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = FormTokens.SectionGap),
                    verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
                ) {
                    SgPrimaryButton(
                        text = if (state.busy) "Checking…" else "Continue",
                        enabled = !state.busy && AddVisitorLogic.canLookup(state.mobileInput),
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SgSecondaryButton(text = "Cancel", onClick = onCancel, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun BlockedPanel(onClose: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = FormTokens.SectionGap)
            .semantics { contentDescription = AddVisitorLogic.BLOCKED },
        verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(88.dp).clip(CircleShape).background(KioskColors.danger),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", color = androidx.compose.ui.graphics.Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
        }
        Text(
            AddVisitorLogic.BLOCKED,
            color = KioskColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = KioskFont, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = FormTokens.ButtonGap),
        )
        SgSecondaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = FormTokens.SectionGap))
    }
}

/** Screens 22 / 23: blue info banner + visitor card + one action. */
@Composable
private fun InsideBanner(active: ActiveVisit, busy: Boolean, onCheckout: (ActiveVisit) -> Unit, onClose: () -> Unit) {
    val text = if (active.isInside) {
        "Already inside" + listOfNotNull(active.sinceLabel?.let { "since $it" }, active.gateName).joinToString(", ", prefix = if (active.sinceLabel != null || active.gateName != null) " " else "")
    } else {
        AddVisitorLogic.WAITING_HOST
    }
    Column(verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField)) {
        Text(
            text, color = KioskColors.info, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
            modifier = Modifier.fillMaxWidth().clip(ControlShape).background(KioskColors.infoSoft).padding(FormTokens.ScreenHPad),
        )
        Column(
            modifier = Modifier.fillMaxWidth().sgCardSurface().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(active.gateName ?: "Visit", color = KioskColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, modifier = Modifier.weight(1f))
                SgStatusChip(
                    label = if (active.isInside) "Checked in" else "Pending approval",
                    kind = if (active.isInside) SgStatusKind.IN_PROGRESS else SgStatusKind.PENDING,
                )
            }
        }
        if (active.isInside) {
            SgPrimaryButton(
                text = if (busy) "Checking out…" else "Check out",
                enabled = !busy,
                onClick = { onCheckout(active) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        SgSecondaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * Screens 2-5. Which one is shown follows the server answer + the chosen kind:
 * found visitor (A), new visitor (B), new vendor (C), existing vendor direct (D, no selector).
 */
@Composable
fun AddVisitorFormStep(
    state: AddVisitorState,
    draft: RegistrationDraft,
    hosts: List<Staff>,
    hostsLoading: Boolean,
    errors: Map<String, String>,
    onKind: (ProfileKind) -> Unit,
    onName: (String) -> Unit,
    onCompany: (String) -> Unit,
    onPurpose: (String) -> Unit,
    onHost: (String) -> Unit,
    onScheduled: (Long?) -> Unit,
    onUseSavedId: (Boolean) -> Unit,
    onBack: () -> Unit,
    onContinue: () -> Unit,
    onSwitchKind: () -> Unit = {},
    onOtpId: (String?) -> Unit = {},
) {
    var confirmSwitch by remember { mutableStateOf(false) }
    val kind = state.kind
    val returning = state.returning
    Column(Modifier.fillMaxWidth()) {
        FormHeader(
            title = AddVisitorLogic.formTitle(kind, returning),
            subtitle = if (returning) "Saved details are filled in. Review and edit if needed." else "Fill in the details below",
        )
        FormFields {
            if (AddVisitorLogic.showsTypeSelector(returning)) {
                KindSelector(kind = kind, onKind = onKind)
            }
            state.known?.alertMessage?.let { InfoBanner(it, KioskColors.warningSoft, KioskColors.warning) }
            state.known?.todayNote?.let { InfoBanner(it, KioskColors.infoSoft, KioskColors.info) }
            if (returning && state.referencePhoto != null) {
                ReferencePhoto(state.referencePhoto)
            }
            KioskField(
                label = "Mobile number",
                value = draft.mobile,
                onValueChange = {},
                readOnly = true,
                keyboardType = KeyboardType.Phone,
                modifier = Modifier.fillMaxWidth(),
            )
            KioskField(
                label = "Name",
                value = draft.visitorName,
                onValueChange = onName,
                placeholder = "Full name",
                error = errors[FieldKeys.VISITOR_NAME],
                modifier = Modifier.fillMaxWidth(),
            )
            if (kind == ProfileKind.VENDOR) {
                KioskField(
                    label = "Company",
                    value = draft.company,
                    onValueChange = onCompany,
                    placeholder = "Vendor company",
                    error = errors[FieldKeys.COMPANY],
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (draft.savedId != null) {
                SavedIdSection(draft = draft, onUseSavedId = onUseSavedId)
            }
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
                label = AddVisitorLogic.hostLabel(kind),
            )
            // Product ruling 13: the scheduled date/time row is not shown on the form.
        }
        if (returning) {
            Text(
                AddVisitorLogic.SWITCH_TYPE_LINK,
                color = KioskColors.text, fontSize = 13.sp, fontFamily = KioskFont,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier
                    .padding(top = FormTokens.ButtonGap)
                    .heightIn(min = 48.dp)
                    .clickable { confirmSwitch = true },
            )
        }
        if (confirmSwitch) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { confirmSwitch = false },
                title = { Text(AddVisitorLogic.SWITCH_TYPE_LINK) },
                text = { Text(AddVisitorLogic.SWITCH_TYPE_CONFIRM) },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmSwitch = false; onSwitchKind() }) { Text("Switch type") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { confirmSwitch = false }) { Text("Cancel") }
                },
            )
        }
        var otpMayContinue by remember { mutableStateOf(true) }
        com.satcop.smartvisitor.kiosk.ui.otp.VisitorOtpSection(
            mobileTenDigits = draft.mobile.filter { it.isDigit() }.takeLast(10),
            onChangeNumber = onBack,
            onGate = { otpMayContinue = it },
            onVerifyId = onOtpId,
        )
        PanelDivider()
        Column(
            modifier = Modifier.fillMaxWidth().padding(top = FormTokens.SectionGap),
            verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap),
        ) {
            SgPrimaryButton(text = "Continue", onClick = onContinue, enabled = otpMayContinue, modifier = Modifier.fillMaxWidth())
            SgSecondaryButton(text = "Cancel", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun InfoBanner(text: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
        modifier = Modifier.fillMaxWidth().clip(ControlShape).background(bg).padding(FormTokens.ScreenHPad),
    )
}

@Composable
private fun KindSelector(kind: ProfileKind, onKind: (ProfileKind) -> Unit) {
    // Segmented Visitor | Vendor control (mock 18/19).
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(KioskColors.secondaryFill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KindChip("Visitor", kind == ProfileKind.VISITOR, { onKind(ProfileKind.VISITOR) }, Modifier.weight(1f))
        KindChip("Vendor", kind == ProfileKind.VENDOR, { onKind(ProfileKind.VENDOR) }, Modifier.weight(1f))
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = FormTokens.MinTouch)
            .clip(PillShape)
            .background(if (selected) KioskColors.primary else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "Visitor Type $label" + if (selected) ", selected" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) KioskColors.onPrimary else KioskColors.text,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ReferencePhoto(bmp: Bitmap) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(FormTokens.FieldToField)) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Last photo (reference only)",
            modifier = Modifier.size(56.dp).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        Text(
            "Last photo on file. A new live photo is taken every visit.",
            color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SavedIdSection(draft: RegistrationDraft, onUseSavedId: (Boolean) -> Unit) {
    val saved = draft.savedId ?: return
    val stacked = LocalDensity.current.fontScale > 1.3f
    Column(Modifier.fillMaxWidth()) {
        FormLabel("ID number")
        Text(
            saved.display,
            color = KioskColors.inputText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FormTokens.MinTouch)
                .clip(ControlShape)
                .background(KioskColors.inputBg)
                .border(1.dp, KioskColors.inputBorder, ControlShape)
                .padding(horizontal = FormTokens.ControlHPad, vertical = 12.dp)
                .semantics { contentDescription = "Saved ID " + saved.display },
        )
        val opts: @Composable (Modifier) -> Unit = { m ->
            KindChip("Use saved ID", draft.useSavedId, { onUseSavedId(true) }, m)
            KindChip("Enter new ID", !draft.useSavedId, { onUseSavedId(false) }, m)
        }
        Box(Modifier.padding(top = FormTokens.LabelToField).clip(PillShape).background(KioskColors.secondaryFill).padding(4.dp)) {
            if (stacked) {
                Column(verticalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap)) { opts(Modifier.fillMaxWidth()) }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(FormTokens.ButtonGap)) { opts(Modifier.weight(1f)) }
            }
        }
        Text(
            AddVisitorLogic.ID_HELPER,
            color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont,
            modifier = Modifier.padding(top = FormTokens.ErrorGap),
        )
    }
}

private val scheduleFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · hh:mm a", Locale.ENGLISH)

/** "Now (walk-in)" or the picked date/time (IST). Visitor form only. */
fun scheduleLabel(ms: Long?): String =
    if (ms == null) "Now (walk-in)"
    else Instant.ofEpochMilli(ms).atZone(ZoneId.of("Asia/Kolkata")).format(scheduleFmt) + " IST"

@Composable
private fun ScheduleField(scheduledAtMs: Long?, onScheduled: (Long?) -> Unit) {
    val ctx = LocalContext.current
    val zone = ZoneId.of("Asia/Kolkata")
    Column(Modifier.fillMaxWidth()) {
        FormLabel("Scheduled date / time (optional)")
        Text(
            scheduleLabel(scheduledAtMs),
            color = KioskColors.inputText, fontSize = 16.sp, fontFamily = KioskFont,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = FormTokens.MinTouch)
                .clip(ControlShape)
                .background(KioskColors.inputBg)
                .border(1.dp, KioskColors.inputBorder, ControlShape)
                .clickable {
                    val base = scheduledAtMs?.let { Instant.ofEpochMilli(it).atZone(zone) } ?: ZonedDateTime.now(zone)
                    DatePickerDialog(ctx, { _, y, m, d ->
                        TimePickerDialog(ctx, { _, hh, mm ->
                            onScheduled(ZonedDateTime.of(y, m + 1, d, hh, mm, 0, 0, zone).toInstant().toEpochMilli())
                        }, base.hour, base.minute, false).show()
                    }, base.year, base.monthValue - 1, base.dayOfMonth).show()
                }
                .padding(horizontal = FormTokens.ControlHPad, vertical = 12.dp)
                .semantics { contentDescription = "Scheduled date and time. " + scheduleLabel(scheduledAtMs) },
        )
        if (scheduledAtMs != null) {
            KioskGhostButton(
                text = "Clear (walk-in now)",
                onClick = { onScheduled(null) },
                modifier = Modifier.fillMaxWidth().padding(top = FormTokens.LabelToField),
            )
        }
    }
}
