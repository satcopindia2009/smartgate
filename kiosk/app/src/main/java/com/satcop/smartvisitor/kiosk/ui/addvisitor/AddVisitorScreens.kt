package com.satcop.smartvisitor.kiosk.ui.addvisitor

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.addvisitor.ActiveVisit
import com.satcop.smartvisitor.kiosk.data.addvisitor.AddVisitorLogic
import com.satcop.smartvisitor.kiosk.data.addvisitor.OpenVisitInfo
import com.satcop.smartvisitor.kiosk.data.addvisitor.ProfileKind
import com.satcop.smartvisitor.kiosk.data.model.BlacklistEntry
import com.satcop.smartvisitor.kiosk.data.model.IdType
import com.satcop.smartvisitor.kiosk.data.model.Staff
import com.satcop.smartvisitor.kiosk.data.registration.FieldKeys
import com.satcop.smartvisitor.kiosk.data.registration.RegistrationDraft
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
import com.satcop.smartvisitor.kiosk.ui.theme.kioskTextFieldColors

// ───────────────────────── shared bits ─────────────────────────

/** 56dp top bar: optional back arrow (left), centred title, optional X (right). */
@Composable
fun AvTopBar(title: String, onBack: (() -> Unit)? = null, onClose: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) {
        if (onBack != null) {
            Box(
                Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape).clickable(onClick = onBack)
                    .semantics { contentDescription = "Back" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = KioskColors.text) }
        }
        Text(title, color = KioskColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont, textAlign = TextAlign.Center)
        if (onClose != null) {
            Box(
                Modifier.align(Alignment.CenterEnd).size(48.dp).clip(CircleShape).clickable(onClick = onClose)
                    .semantics { contentDescription = "Close" },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Close, null, tint = KioskColors.text) }
        }
    }
}

@Composable
private fun AvLabel(text: String, required: Boolean) {
    Text(
        buildAnnotatedString {
            append(text)
            if (required) withStyle(SpanStyle(color = KioskColors.danger)) { append(" *") }
        },
        color = KioskColors.inputLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = KioskFont,
        modifier = Modifier.padding(bottom = FormTokens.LabelToField),
    )
}

@Composable
private fun AvErrorOrHelper(error: String?, helper: String? = null) {
    val t = error ?: helper ?: return
    Text(
        t, color = if (error != null) KioskColors.danger else KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont,
        modifier = Modifier.padding(top = FormTokens.ErrorGap),
    )
}

@Composable
private fun AvField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String = "",
    error: String? = null,
    helper: String? = null,
    readOnly: Boolean = false,
    prefix: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Words,
    autoCorrect: Boolean = true,
) {
    Column(modifier) {
        AvLabel(label, required)
        TextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth().heightIn(min = FormTokens.MinTouch)
                .border(1.dp, if (error != null) KioskColors.danger else KioskColors.inputBorder, ControlShape)
                .clip(ControlShape),
            textStyle = TextStyle(color = if (readOnly) KioskColors.textMuted else KioskColors.inputText, fontFamily = KioskFont, fontSize = 15.sp),
            placeholder = { Text(placeholder, color = KioskColors.inputHint, fontFamily = KioskFont, fontSize = 15.sp) },
            prefix = prefix?.let { p -> { Text(p, color = KioskColors.inputText, fontFamily = KioskFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) } },
            singleLine = true,
            isError = error != null,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboardType, capitalization = capitalization, autoCorrectEnabled = autoCorrect,
            ),
            shape = ControlShape,
            colors = kioskTextFieldColors(container = if (readOnly) KioskColors.secondaryFill else KioskColors.inputBg),
        )
        AvErrorOrHelper(error, helper)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdTypeDropdown(value: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        AvLabel("ID type", true)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            TextField(
                value = value, onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().heightIn(min = FormTokens.MinTouch)
                    .border(1.dp, KioskColors.inputBorder, ControlShape).clip(ControlShape)
                    .semantics { contentDescription = "ID type $value" },
                textStyle = TextStyle(color = KioskColors.inputText, fontFamily = KioskFont, fontSize = 15.sp),
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = ControlShape, colors = kioskTextFieldColors(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(KioskColors.card)) {
                IdType.entries.forEach { t ->
                    DropdownMenuItem(
                        text = { Text(t.apiValue, color = KioskColors.text, fontFamily = KioskFont) },
                        onClick = { onSelect(t.apiValue); expanded = false },
                        modifier = Modifier.heightIn(min = FormTokens.MinTouch),
                    )
                }
            }
        }
    }
}

// ───────────────────────── screen 15 / 16: number ─────────────────────────

/** Board 15/16: back arrow, "Mobile number" headline, +91 prefix field, Continue. No Cancel button. */
@Composable
fun AddVisitorNumberStep(
    state: AddVisitorState,
    onMobile: (String) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    onCloseNotice: () -> Unit,
    onCheckout: (ActiveVisit) -> Unit,
    onCheckInOpen: (OpenVisitInfo) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        AvTopBar("Add visitor", onBack = onCancel)
        when (val n = state.notice) {
            AvNotice.Blocked -> BlockedPanel(onClose = onCloseNotice)
            is AvNotice.Open -> OpenVisitCard(n.open, state.referencePhoto, state.mobileInput, state.checkoutBusy, onCheckInOpen, onCloseNotice)
            is AvNotice.CheckedIn -> CheckedInCard(n.hostName, onCloseNotice)
            is AvNotice.Inside -> InsideCard(n.active, state.checkoutBusy, onCheckout, onCloseNotice)
            else -> {
                Text(
                    "Mobile number", color = KioskColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont,
                    modifier = Modifier.padding(top = FormTokens.FieldToField),
                )
                Text(
                    "We will check if this visitor has been here before.", color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont,
                    modifier = Modifier.padding(top = 4.dp, bottom = FormTokens.HeaderToForm),
                )
                AvField(
                    label = "Mobile number", required = true,
                    value = state.mobileInput, onValueChange = onMobile,
                    prefix = "+91  ", placeholder = "98765 43210",
                    error = state.mobileError,
                    keyboardType = KeyboardType.Phone, capitalization = KeyboardCapitalization.None, autoCorrect = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                (n as? AvNotice.Failed)?.let {
                    Text(it.message, color = KioskColors.danger, fontSize = 14.sp, fontFamily = KioskFont, modifier = Modifier.padding(top = FormTokens.ButtonGap))
                }
                SgPrimaryButton(
                    text = if (state.busy) "Checking…" else "Continue",
                    enabled = !state.busy && AddVisitorLogic.canLookup(state.mobileInput),
                    onClick = onContinue,
                    modifier = Modifier.fillMaxWidth().padding(top = FormTokens.SectionGap),
                )
            }
        }
    }
}

@Composable
private fun BlockedPanel(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = FormTokens.SectionGap).semantics { contentDescription = AddVisitorLogic.BLOCKED },
        verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(KioskColors.danger), contentAlignment = Alignment.Center) {
            Text("✕", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont)
        }
        Text(
            AddVisitorLogic.BLOCKED, color = KioskColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            fontFamily = KioskFont, textAlign = TextAlign.Center, modifier = Modifier.padding(top = FormTokens.ButtonGap),
        )
        SgSecondaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth().padding(top = FormTokens.SectionGap))
    }
}

@Composable
private fun InfoBar(text: String, bg: Color, fg: Color) {
    Text(
        text, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
        modifier = Modifier.fillMaxWidth().clip(ControlShape).background(bg).padding(FormTokens.ScreenHPad),
    )
}

/** Board 22: already inside (only for status inside). */
@Composable
private fun InsideCard(active: ActiveVisit, busy: Boolean, onCheckout: (ActiveVisit) -> Unit, onClose: () -> Unit) {
    val text = "Already inside" + listOfNotNull(active.sinceLabel?.let { "since $it" }, active.gateName)
        .joinToString(", ", prefix = if (active.sinceLabel != null || active.gateName != null) " " else "")
    Column(Modifier.padding(top = FormTokens.FieldToField), verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField)) {
        InfoBar(text, KioskColors.infoSoft, KioskColors.info)
        Row(
            Modifier.fillMaxWidth().sgCardSurface().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(active.gateName ?: "Visit", color = KioskColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, modifier = Modifier.weight(1f))
            SgStatusChip("Checked in", SgStatusKind.IN_PROGRESS)
        }
        if (active.isInside) {
            SgPrimaryButton(text = if (busy) "Checking out…" else "Check out", enabled = !busy, onClick = { onCheckout(active) }, modifier = Modifier.fillMaxWidth())
        }
        SgSecondaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CheckedInCard(hostName: String?, onClose: () -> Unit) {
    Column(Modifier.padding(top = FormTokens.FieldToField), verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField)) {
        InfoBar("Checked in" + (hostName?.takeIf { it.isNotBlank() }?.let { " · visiting $it" } ?: ""), KioskColors.successSoft, KioskColors.success)
        SgPrimaryButton(text = "Done", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * D15 / AC-AV7 (boards 22/23 style). pending: "Waiting for host approval" + Close.
 * approved: "Approved for {host}" + "Check in now" (checks in THAT visit) + Close. Never a second visit.
 */
@Composable
private fun OpenVisitCard(open: OpenVisitInfo, photo: Bitmap?, typedMobile: String, busy: Boolean, onCheckIn: (OpenVisitInfo) -> Unit, onClose: () -> Unit) {
    val host = open.hostName?.takeIf { it.isNotBlank() }
    // The guard just typed this number, so a masked form of it is known even if the server sent none.
    val masked = open.mobileMasked ?: AddVisitorLogic.maskedMobileForForm(typedMobile).takeIf { it.isNotBlank() }
    val line = listOfNotNull(masked, open.purpose).joinToString(" · ")
    Column(Modifier.padding(top = FormTokens.FieldToField), verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField)) {
        InfoBar(
            text = if (open.isApproved) AddVisitorLogic.APPROVED_FOR + (host ?: "the host") else AddVisitorLogic.WAITING_HOST,
            bg = KioskColors.infoSoft, fg = KioskColors.info,
        )
        Row(
            Modifier.fillMaxWidth().sgCardSurface().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(KioskColors.brandSoft), contentAlignment = Alignment.Center) {
                if (photo != null) {
                    Image(photo.asImageBitmap(), null, Modifier.size(48.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Text(
                        initials(open.visitorName), color = KioskColors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                open.visitorName?.let { Text(it, color = KioskColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = KioskFont) }
                if (line.isNotBlank()) Text(line, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
            }
            if (open.isApproved) SgStatusChip("Approved", SgStatusKind.COMPLETED) else SgStatusChip("Pending approval", SgStatusKind.PENDING)
        }
        Column(Modifier.fillMaxWidth().sgCardSurface().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OpenRow("Host", host ?: "—")
            open.askedAtLabel?.let { OpenRow("Asked at", it) }
        }
        if (open.isApproved) {
            SgPrimaryButton(text = if (busy) "Checking in…" else "Check in now", enabled = !busy, onClick = { onCheckIn(open) }, modifier = Modifier.fillMaxWidth())
        }
        SgSecondaryButton(text = "Close", onClick = onClose, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun OpenRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = KioskColors.textMuted, fontSize = 14.sp, fontFamily = KioskFont)
        Text(value, color = KioskColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
    }
}

private fun initials(name: String?): String =
    name.orEmpty().trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "V" }

// ───────────────────────── boards 17 / 18 / 18b / 19 / 20: ONE form ─────────────────────────

/**
 * ONE scrolling form. Visitor|Vendor selector (new numbers only), Name, Mobile (read-only, masked), ID type,
 * ID number, Purpose, Host, Company (vendor), OTP card (when the school enables it), Photo + ID photo tiles,
 * notice line, Submit / Cancel. Camera is a full-screen overlay that returns here.
 */
@Composable
fun AddVisitorFormStep(
    state: AddVisitorState,
    draft: RegistrationDraft,
    hosts: List<Staff>,
    hostsLoading: Boolean,
    errors: Map<String, String>,
    livePhoto: Bitmap?,
    idImage: Bitmap?,
    submitting: Boolean,
    blocked: Boolean,
    blacklistHit: BlacklistEntry?,
    onKind: (ProfileKind) -> Unit,
    onName: (String) -> Unit,
    onCompany: (String) -> Unit,
    onPurpose: (String) -> Unit,
    onHost: (String) -> Unit,
    onIdType: (String) -> Unit,
    onIdNumber: (String) -> Unit,
    onIdTypeName: (String) -> Unit,
    onUseSavedId: (Boolean) -> Unit,
    onLivePhoto: (Bitmap?) -> Unit,
    onIdImage: (Bitmap?) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onSwitchKind: () -> Unit = {},
    onOtpId: (String?) -> Unit = {},
) {
    var confirmSwitch by remember { mutableStateOf(false) }
    var cameraTarget by remember { mutableStateOf<AvCameraTarget?>(null) }
    cameraTarget?.let { t ->
        AddVisitorCamera(
            target = t,
            onCaptured = { bmp -> if (t == AvCameraTarget.ID) onIdImage(bmp) else onLivePhoto(bmp); cameraTarget = null },
            onCancel = { cameraTarget = null },
        )
        return
    }
    val kind = state.kind
    val returning = state.returning
    val savedShown = draft.savedId != null && draft.useSavedId
    Column(Modifier.fillMaxWidth()) {
        AvTopBar(AddVisitorLogic.formTitle(kind, returning), onBack = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(FormTokens.FieldToField)) {
            if (blocked && blacklistHit != null) {
                InfoBar("Entry not allowed. Call the security head.", KioskColors.dangerSoft, KioskColors.danger)
            } else if (blacklistHit?.severity == "Alert") {
                InfoBar("Watch-list alert. Call the security head.", KioskColors.warningSoft, KioskColors.warning)
            }
            if (AddVisitorLogic.showsTypeSelector(returning)) KindSelector(kind = kind, onKind = onKind)
            state.known?.alertMessage?.let { InfoBar(it, KioskColors.warningSoft, KioskColors.warning) }
            state.known?.todayNote?.let { InfoBar(it, KioskColors.infoSoft, KioskColors.info) }
            if (returning) ReturningHeader(kind, state.referencePhoto, state.known?.lastVisitLabel)

            AvField("Name", draft.visitorName, onName, required = true, placeholder = "Full name", error = errors[FieldKeys.VISITOR_NAME], modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(FormTokens.FieldToField), verticalAlignment = Alignment.Top) {
                AvField("Mobile number", AddVisitorLogic.maskedMobileForForm(draft.mobile), {}, readOnly = true, modifier = Modifier.weight(1f))
                if (kind == ProfileKind.VENDOR) {
                    AvField("Company", draft.company, onCompany, required = true, placeholder = "Company", error = errors[FieldKeys.COMPANY], modifier = Modifier.weight(1f))
                } else if (!savedShown) {
                    IdTypeDropdown(draft.idType, onIdType, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }

            if (savedShown) {
                val saved = draft.savedId!!
                Column(Modifier.fillMaxWidth()) {
                    AvLabel("ID number", true)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = FormTokens.MinTouch).clip(ControlShape).background(KioskColors.secondaryFill)
                            .border(1.dp, KioskColors.inputBorder, ControlShape).padding(horizontal = FormTokens.ControlHPad, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text(saved.display, color = KioskColors.inputText, fontSize = 15.sp, fontFamily = KioskFont, modifier = Modifier.weight(1f).semantics { contentDescription = "Saved ID " + saved.display })
                        Text("Use saved ID", color = KioskColors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
                        Text("Edit", color = KioskColors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
                            modifier = Modifier.heightIn(min = 40.dp).clickable { onUseSavedId(false) }.padding(top = 10.dp))
                    }
                    AvErrorOrHelper(errors[FieldKeys.ID], AddVisitorLogic.ID_HELPER)
                }
            } else {
                if (kind == ProfileKind.VENDOR) {
                    Row(horizontalArrangement = Arrangement.spacedBy(FormTokens.FieldToField), verticalAlignment = Alignment.Top) {
                        IdTypeDropdown(draft.idType, onIdType, Modifier.weight(1f))
                        AvField("ID number", draft.idNumber, onIdNumber, required = true, placeholder = "Enter ID number",
                            error = errors[FieldKeys.ID], capitalization = KeyboardCapitalization.Characters, autoCorrect = false, modifier = Modifier.weight(1f))
                    }
                    Text(AddVisitorLogic.ID_HELPER, color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
                } else {
                    AvField("ID number", draft.idNumber, onIdNumber, required = true, placeholder = "Enter ID number",
                        error = errors[FieldKeys.ID], helper = AddVisitorLogic.ID_HELPER,
                        capitalization = KeyboardCapitalization.Characters, autoCorrect = false, modifier = Modifier.fillMaxWidth())
                }
                if (draft.idType == IdType.Other.apiValue) {
                    AvField("ID name", draft.idTypeName, onIdTypeName, required = true, placeholder = "e.g. Ration card",
                        error = errors[FieldKeys.ID_TYPE_NAME], modifier = Modifier.fillMaxWidth())
                }
                if (draft.savedId != null) {
                    Text("Use saved ID (${draft.savedId.display})", color = KioskColors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
                        modifier = Modifier.heightIn(min = 40.dp).clickable { onUseSavedId(true) })
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(FormTokens.FieldToField), verticalAlignment = Alignment.Top) {
                AvField("Purpose", draft.purpose, onPurpose, required = true, placeholder = "e.g. Parent visit",
                    error = errors[FieldKeys.PURPOSE], capitalization = KeyboardCapitalization.Sentences, modifier = Modifier.weight(1f))
                HostDropdown(
                    hosts = hosts, selectedId = draft.hostId, error = errors[FieldKeys.HOST_ID], loading = hostsLoading,
                    onSelect = onHost, label = AddVisitorLogic.hostLabel(kind) + " *", modifier = Modifier.weight(1f),
                )
            }

            var otpMayContinue by remember { mutableStateOf(true) }
            com.satcop.smartvisitor.kiosk.ui.otp.VisitorOtpSection(
                mobileTenDigits = draft.mobile.filter { it.isDigit() }.takeLast(10),
                onChangeNumber = onBack,
                onGate = { otpMayContinue = it },
                onVerifyId = onOtpId,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(FormTokens.FieldToField), verticalAlignment = Alignment.Top) {
                CaptureTile("Photo", "Take photo", livePhoto, errors[FieldKeys.LIVE_PHOTO], { cameraTarget = AvCameraTarget.PHOTO }, isId = false, modifier = Modifier.weight(1f))
                CaptureTile("ID photo", "Take ID photo", idImage, errors[FieldKeys.ID_IMAGE], { cameraTarget = AvCameraTarget.ID }, isId = true, modifier = Modifier.weight(1f))
            }

            if (returning) {
                Text(
                    AddVisitorLogic.SWITCH_TYPE_LINK, color = KioskColors.primary, fontSize = 13.sp, fontFamily = KioskFont,
                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                    modifier = Modifier.heightIn(min = 40.dp).clickable { confirmSwitch = true },
                )
            }

            Text(
                AddVisitorLogic.CONSENT_NOTICE_EN + "\n" + AddVisitorLogic.CONSENT_NOTICE_HI,
                color = KioskColors.textMuted, fontSize = 12.sp, lineHeight = 16.sp, fontFamily = KioskFont,
                modifier = Modifier.padding(top = 4.dp),
            )
            SgPrimaryButton(
                text = if (submitting) "Submitting…" else "Submit",
                enabled = !submitting && !blocked && otpMayContinue,
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Cancel", color = KioskColors.textMuted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onCancel).padding(vertical = 12.dp),
            )
        }
    }
    if (confirmSwitch) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmSwitch = false },
            title = { Text(AddVisitorLogic.SWITCH_TYPE_LINK) },
            text = { Text(AddVisitorLogic.SWITCH_TYPE_CONFIRM) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false; onSwitchKind() }) { Text("Switch type") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmSwitch = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ReturningHeader(kind: ProfileKind, photo: Bitmap?, lastVisit: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (photo != null) {
            Image(photo.asImageBitmap(), "Last photo (reference only)", Modifier.size(56.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Column {
            SgStatusChip(if (kind == ProfileKind.VENDOR) "Returning vendor" else "Returning visitor", SgStatusKind.BRAND)
            Text(
                listOfNotNull(lastVisit, "last photo (reference only)").joinToString(" · "),
                color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont, modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun KindSelector(kind: ProfileKind, onKind: (ProfileKind) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(PillShape).background(KioskColors.secondaryFill).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KindChip("Visitor", kind == ProfileKind.VISITOR, { onKind(ProfileKind.VISITOR) }, Modifier.weight(1f))
        KindChip("Vendor", kind == ProfileKind.VENDOR, { onKind(ProfileKind.VENDOR) }, Modifier.weight(1f))
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier.heightIn(min = FormTokens.MinTouch).clip(PillShape)
            .background(if (selected) KioskColors.primary else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics { contentDescription = "Visitor Type $label" + if (selected) ", selected" else "" },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (selected) KioskColors.onPrimary else KioskColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, textAlign = TextAlign.Center)
    }
}

/** Dashed-border camera tile (boards 17-20). Shows the captured thumbnail; tapping again retakes. */
@Composable
private fun CaptureTile(label: String, prompt: String, bmp: Bitmap?, error: String?, onClick: () -> Unit, isId: Boolean, modifier: Modifier) {
    val edge = if (error != null) KioskColors.danger else KioskColors.primary
    Column(modifier) {
        AvLabel(label, true)
        Column(
            Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(RoundedCornerShape(16.dp)).background(KioskColors.inputBg)
                .drawBehind {
                    drawRoundRect(edge, cornerRadius = CornerRadius(16.dp.toPx()), style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))))
                }
                .clickable(onClick = onClick).padding(12.dp).semantics { contentDescription = if (bmp != null) "$label captured. Tap to retake." else prompt },
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
        ) {
            if (bmp != null) {
                Image(
                    bmp.asImageBitmap(), label,
                    Modifier.height(72.dp).let { if (isId) it.fillMaxWidth(0.8f) else it.size(72.dp) }.clip(if (isId) RoundedCornerShape(8.dp) else CircleShape),
                    contentScale = ContentScale.Crop,
                )
                Text("Retake", color = KioskColors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, modifier = Modifier.padding(top = 6.dp))
            } else {
                Icon(if (isId) Icons.Outlined.Badge else Icons.Outlined.PhotoCamera, null, tint = KioskColors.primary, modifier = Modifier.size(28.dp))
                Text(prompt, color = KioskColors.primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            }
        }
        AvErrorOrHelper(error)
    }
}
