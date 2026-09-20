package com.satcop.smartvisitor.kiosk.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import android.Manifest
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.data.model.CourierEvent
import com.satcop.smartvisitor.kiosk.data.model.CourierStatus
import com.satcop.smartvisitor.kiosk.data.model.GuardHistoryEvent
import com.satcop.smartvisitor.kiosk.data.model.HistoryFilter
import com.satcop.smartvisitor.kiosk.data.model.InsideVisit
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.ChipShape
import com.satcop.smartvisitor.kiosk.ui.theme.ControlShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont
import java.io.ByteArrayOutputStream

@Composable
fun GuardToolRow(onHistory: () -> Unit, onCourier: () -> Unit, onCheckout: () -> Unit, onLostFound: () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("History" to onHistory, "Courier" to onCourier, "Checkout" to onCheckout, "Lost & Found" to onLostFound).forEach { (label, action) ->
            KioskGhostButton(text = label, onClick = action, modifier = Modifier.heightIn(min = 44.dp))
        }
    }
}

@Composable
fun HistoryScreen(
    events: List<GuardHistoryEvent>, filterToday: Boolean, filterKind: String, filterStatus: String,
    busy: Boolean, selected: GuardHistoryEvent?,
    onToggleToday: () -> Unit, onKind: (String) -> Unit, onStatus: (String) -> Unit,
    onSelect: (GuardHistoryEvent) -> Unit, onClearDetail: () -> Unit, onRefresh: () -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        HeaderRow("History", "Gate log · newest first", onBack)
        Spacer(Modifier.height(10.dp))
        ChipRow(if (filterToday) "today" else "all dates", listOf("today", "all dates")) { onToggleToday() }
        Spacer(Modifier.height(8.dp)); ChipRow(filterKind, HistoryFilter.TYPES, onKind)
        Spacer(Modifier.height(8.dp)); ChipRow(filterStatus, HistoryFilter.STATUSES.take(6), onStatus)
        Spacer(Modifier.height(8.dp))
        KioskGhostButton(text = if (busy) "Refreshing…" else "Refresh", onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp))
        Spacer(Modifier.height(12.dp))
        if (selected != null) {
            DetailCard(selected, onClearDetail); Spacer(Modifier.height(12.dp))
        }
        if (events.isEmpty()) Text("No entries for this filter", color = KioskColors.textMuted, fontFamily = KioskFont)
        else events.forEach { ev -> EventRow(ev) { onSelect(ev) }; Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun HeaderRow(title: String, sub: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = KioskColors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
            Text(sub, color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
        }
        KioskGhostButton(text = "Back", onClick = onBack, modifier = Modifier.heightIn(min = 44.dp))
    }
}

@Composable private fun DetailCard(ev: GuardHistoryEvent, onClose: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(ControlShape).background(KioskColors.cardHover).border(1.dp, KioskColors.purple, ControlShape).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Detail", color = KioskColors.purpleBright, fontFamily = KioskFont, fontWeight = FontWeight.SemiBold)
            Text("Close", color = KioskColors.cyan, fontFamily = KioskFont, modifier = Modifier.clickable(onClick = onClose))
        }
        Spacer(Modifier.height(8.dp))
        Text(ev.title, color = KioskColors.text, fontSize = 16.sp, fontFamily = KioskFont, fontWeight = FontWeight.Medium)
        Text(ev.subtitle.orEmpty(), color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
        Text("Type · ${ev.kind} · Status · ${ev.status}", color = KioskColors.textDim, fontSize = 12.sp, fontFamily = KioskFont)
        Text("When · ${ev.occurredAt}", color = KioskColors.textDim, fontSize = 12.sp, fontFamily = KioskFont)
        if (!ev.mobile.isNullOrBlank()) Text("Mobile · ${ev.mobile}", color = KioskColors.textDim, fontSize = 12.sp, fontFamily = KioskFont)
        if (!ev.company.isNullOrBlank()) Text("Company · ${ev.company}", color = KioskColors.textDim, fontSize = 12.sp, fontFamily = KioskFont)
        if (!ev.gateId.isNullOrBlank()) Text("Gate · ${ev.gateId}", color = KioskColors.textDim, fontSize = 12.sp, fontFamily = KioskFont)
    }
}

@Composable private fun EventRow(ev: GuardHistoryEvent, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(ControlShape).background(KioskColors.sidebar).border(1.dp, KioskColors.border, ControlShape).clickable(onClick = onClick).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(ev.title, color = KioskColors.text, fontFamily = KioskFont, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Pill(ev.status)
        }
        Text(ev.subtitle.orEmpty(), color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${ev.kind} · ${ev.occurredAt.take(16).replace('T', ' ')}", color = KioskColors.textDim, fontSize = 11.sp, fontFamily = KioskFont)
    }
}

@Composable
fun CourierLogScreen(
    company: String,
    tracking: String,
    packageType: String,
    gateLabel: String,
    collectedBy: String,
    note: String,
    busy: Boolean,
    recent: List<CourierEvent>,
    packagePhotoJpeg: ByteArray? = null,
    onCompany: (String) -> Unit,
    onTracking: (String) -> Unit,
    onPackageType: (String) -> Unit,
    onGateLabel: (String) -> Unit,
    onCollectedBy: (String) -> Unit,
    onNote: (String) -> Unit,
    onPackagePhoto: (ByteArray) -> Unit = {},
    onClearPackagePhoto: () -> Unit = {},
    onReceive: () -> Unit,
    onHandOver: (String) -> Unit,
    onBack: () -> Unit,
) {
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap: Bitmap? ->
        if (bitmap == null) return@rememberLauncherForActivityResult
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        onPackagePhoto(stream.toByteArray())
    }
    val preview = remember(packagePhotoJpeg) {
        packagePhotoJpeg?.let { bytes ->
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }
    val packageTypes = listOf("Envelope", "Box", "Parcel", "Other")
    val canSave = !busy &&
        company.isNotBlank() &&
        tracking.isNotBlank() &&
        packageType.isNotBlank() &&
        gateLabel.isNotBlank() &&
        collectedBy.isNotBlank()

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        HeaderRow("Courier log / New courier", "Not a Visit — no host approve for lobby receive", onBack)
        Spacer(Modifier.height(12.dp))
        KioskField(
            label = "courier_company *",
            value = company,
            onValueChange = onCompany,
            placeholder = "e.g. DTDC, FedEx, Delhivery",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = "tracking_number *",
            value = tracking,
            onValueChange = onTracking,
            placeholder = "AWB / tracking ID",
            capitalization = KeyboardCapitalization.Characters,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text("package_type *", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            packageTypes.forEach { opt ->
                val on = opt.equals(packageType, ignoreCase = true)
                Box(
                    Modifier
                        .clip(ChipShape)
                        .background(if (on) KioskColors.cyanDim else KioskColors.sidebar)
                        .border(1.dp, if (on) KioskColors.cyan else KioskColors.border, ChipShape)
                        .clickable { onPackageType(opt) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(opt, color = if (on) KioskColors.cyanBright else KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = "gate *",
            value = gateLabel,
            onValueChange = onGateLabel,
            placeholder = "Main Gate",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = "collected_by *",
            value = collectedBy,
            onValueChange = onCollectedBy,
            placeholder = "Guard / desk name",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = "package_note (optional)",
            value = note,
            onValueChange = onNote,
            placeholder = "Invoice packet",
            capitalization = KeyboardCapitalization.Sentences,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("Package photo (optional)", color = KioskColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = KioskFont)
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(ControlShape)
                .background(KioskColors.sidebar)
                .border(1.dp, KioskColors.cyan.copy(alpha = 0.45f), ControlShape)
                .clickable(enabled = !busy) { takePicture.launch(null) },
            contentAlignment = Alignment.Center,
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview,
                    contentDescription = "Package photo",
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentScale = ContentScale.Crop,
                )
                TextButton(onClick = onClearPackagePhoto, modifier = Modifier.align(Alignment.TopEnd)) {
                    Icon(Icons.Default.Close, null, tint = KioskColors.red, modifier = Modifier.size(20.dp))
                }
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CameraAlt, null, tint = KioskColors.cyanBright, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(6.dp))
                    Text("Tap to capture photo", color = KioskColors.text, fontSize = 13.sp, fontFamily = KioskFont)
                    Text("Optional package evidence", color = KioskColors.textMuted, fontSize = 11.sp, fontFamily = KioskFont)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        KioskPrimaryButton(
            text = if (busy) "Saving…" else "Mark Received",
            onClick = onReceive,
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Text("Recent couriers", color = KioskColors.text, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Spacer(Modifier.height(8.dp))
        recent.forEach { c ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(ControlShape)
                    .background(KioskColors.sidebar)
                    .border(1.dp, KioskColors.border, ControlShape)
                    .padding(12.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "${c.courierCompany} · ${c.trackingNumber ?: c.recipientName}",
                        color = KioskColors.text,
                        fontFamily = KioskFont,
                        modifier = Modifier.weight(1f),
                    )
                    Pill(c.status)
                }
                val sub = listOfNotNull(
                    c.packageType,
                    c.collectedBy?.let { "by $it" },
                    c.packageNote,
                ).joinToString(" · ")
                if (sub.isNotBlank()) {
                    Text(sub, color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
                }
                if (c.status == CourierStatus.RECEIVED) {
                    Spacer(Modifier.height(8.dp))
                    KioskGhostButton(
                        text = "Hand over",
                        onClick = { onHandOver(c.id) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun CheckoutVerifyScreen(inside: List<InsideVisit>, selectedId: String?, busy: Boolean, onSelect: (String) -> Unit, onConfirm: () -> Unit, onBack: () -> Unit, onRefresh: () -> Unit) {
    val selected = inside.firstOrNull { it.id == selectedId }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        HeaderRow("Checkout verify", "Pick inside · confirm · exit", onBack)
        Spacer(Modifier.height(8.dp))
        KioskGhostButton(text = "Refresh inside", onClick = onRefresh, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp))
        Spacer(Modifier.height(12.dp))
        if (inside.isEmpty()) Text("No one inside right now", color = KioskColors.textMuted, fontFamily = KioskFont)
        else inside.forEach { row ->
            val sel = row.id == selectedId
            Column(Modifier.fillMaxWidth().clip(ControlShape).background(if (sel) KioskColors.purpleDim else KioskColors.sidebar).border(1.dp, if (sel) KioskColors.purple else KioskColors.border, ControlShape).clickable { onSelect(row.id) }.padding(12.dp)) {
                Text(row.visitorName ?: row.id, color = KioskColors.text, fontFamily = KioskFont, fontWeight = FontWeight.Medium)
                Text("${row.visitorType ?: "?"} · ${row.status} · gate ${row.gateId ?: "?"}", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (selected != null) {
            Text("Confirm checkout for ${selected.visitorName ?: selected.id}?", color = KioskColors.text, fontFamily = KioskFont)
            Spacer(Modifier.height(8.dp))
            KioskPrimaryButton(text = if (busy) "Checking out…" else "Confirm checkout", onClick = onConfirm, enabled = !busy, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun LostFoundCreateScreen(
    description: String,
    location: String,
    finder: String,
    finderMobile: String,
    foundAt: String,
    itemType: String,
    photo: Bitmap?,
    busy: Boolean,
    onDescription: (String) -> Unit,
    onLocation: (String) -> Unit,
    onFinder: (String) -> Unit,
    onFinderMobile: (String) -> Unit,
    onFoundAt: (String) -> Unit,
    onItemType: (String) -> Unit,
    onPhoto: (Bitmap?) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val isLost = itemType.equals("Lost", ignoreCase = true)
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bmp ->
        onPhoto(bmp)
    }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) camera.launch(null) else onPhoto(null)
    }
    fun capturePhoto() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) camera.launch(null) else cameraPermission.launch(Manifest.permission.CAMERA)
    }
    val canSubmit = !busy && description.isNotBlank() && location.isNotBlank() &&
        itemType in setOf("Lost", "Found") && photo != null

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        HeaderRow(
            "Lost & Found",
            if (isLost) "Lost report · photo required · Admin claim later" else "Found item · photo required · Admin manages later",
            onBack,
        )
        Spacer(Modifier.height(12.dp))
        Text("Item type *", color = KioskColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = KioskFont)
        Spacer(Modifier.height(6.dp))
        ChipRow(selected = itemType, options = listOf("Found", "Lost"), onSelect = onItemType)
        Spacer(Modifier.height(12.dp))
        KioskField(
            label = "Item description *",
            value = description,
            onValueChange = onDescription,
            placeholder = if (isLost) "Blue bottle · last seen" else "Blue bottle",
            capitalization = KeyboardCapitalization.Sentences,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = if (isLost) "Last seen location *" else "Location found *",
            value = location,
            onValueChange = onLocation,
            placeholder = "Main Gate",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = if (isLost) "Date/time lost *" else "Date/time found *",
            value = foundAt,
            onValueChange = onFoundAt,
            placeholder = "ISO time",
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = if (isLost) "Reporter name *" else "Finder name *",
            value = finder,
            onValueChange = onFinder,
            placeholder = "Guard",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        KioskField(
            label = if (isLost) "Reporter mobile (optional)" else "Finder mobile (optional)",
            value = finderMobile,
            onValueChange = onFinderMobile,
            keyboardType = KeyboardType.Phone,
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Text("Item photo *", color = KioskColors.text, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFamily = KioskFont)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(ControlShape)
                .background(if (photo != null) KioskColors.cyanDim else KioskColors.sidebar)
                .border(1.dp, if (photo != null) KioskColors.cyan else KioskColors.border, ControlShape)
                .clickable(enabled = !busy) { capturePhoto() },
            contentAlignment = Alignment.Center,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo.asImageBitmap(),
                    contentDescription = "L&F item photo",
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tap to capture photo", color = KioskColors.text, fontSize = 13.sp, fontFamily = KioskFont)
                    Text("Camera only · required (AC-LF1)", color = KioskColors.textDim, fontSize = 11.sp, fontFamily = KioskFont)
                }
            }
        }
        if (photo != null) {
            Spacer(Modifier.height(6.dp))
            Text("Thumb ready · will upload on save", color = KioskColors.cyanBright, fontSize = 11.sp, fontFamily = KioskFont)
            Spacer(Modifier.height(4.dp))
            KioskGhostButton(text = "Retake photo", onClick = { onPhoto(null) }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp))
        }
        Spacer(Modifier.height(12.dp))
        KioskPrimaryButton(
            text = if (busy) "Saving…" else if (isLost) "File Lost report" else "Create Found item",
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun FaceLoginScaffoldScreen(enrolled: Boolean, consentAgreed: Boolean, busy: Boolean, message: String?, onToggleConsent: () -> Unit, onEnroll: () -> Unit, onFaceLogin: () -> Unit, onUsePassword: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Guard face login", color = KioskColors.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Text("UNLOCK scaffold · visitor face OUT · password always available", color = KioskColors.textMuted, fontSize = 13.sp, fontFamily = KioskFont)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(ControlShape).background(KioskColors.sidebar).border(1.dp, KioskColors.border, ControlShape).padding(14.dp)) {
            Text(if (enrolled) "Template: enrolled (local stub)" else "Template: not enrolled", color = if (enrolled) KioskColors.greenBright else KioskColors.orange, fontFamily = KioskFont, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text("Consent (EN+HI): I agree to enroll my face for gate duty login only. मैं गेट ड्यूटी लॉगिन के लिए सहमत हूँ।", color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clip(ChipShape).background(if (consentAgreed) KioskColors.greenDim else KioskColors.borderSubtle).clickable(onClick = onToggleConsent).padding(10.dp)) {
                Text(if (consentAgreed) "✓ Consent recorded" else "Tap to agree consent", color = KioskColors.text, fontFamily = KioskFont)
            }
        }
        Spacer(Modifier.height(12.dp))
        KioskPrimaryButton(text = if (busy) "Working…" else if (enrolled) "Re-enroll face (stub)" else "Enroll face (stub)", onClick = onEnroll, enabled = !busy && consentAgreed, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskGhostButton(text = "Unlock with face (stub)", onClick = onFaceLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        Spacer(Modifier.height(8.dp))
        KioskGhostButton(text = "Use password instead", onClick = onUsePassword, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp))
        if (!message.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(message, color = KioskColors.cyanBright, fontSize = 12.sp, fontFamily = KioskFont) }
    }
}

@Composable private fun ChipRow(selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { opt ->
            val on = opt.equals(selected, true)
            Box(Modifier.clip(ChipShape).background(if (on) KioskColors.purpleDim else KioskColors.sidebar).border(1.dp, if (on) KioskColors.purple else KioskColors.border, ChipShape).clickable { onSelect(opt) }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(opt, color = if (on) KioskColors.purpleBright else KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
            }
        }
    }
}

@Composable private fun Pill(status: String) {
    val color = when (status.lowercase()) {
        "inside", "received", "open" -> KioskColors.cyan
        "completed", "handedover" -> KioskColors.green
        "pending" -> KioskColors.orange
        else -> KioskColors.textMuted
    }
    Box(Modifier.clip(ChipShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(status, color = color, fontSize = 11.sp, fontFamily = KioskFont)
    }
}
