package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    company: String, recipient: String, personName: String, mobile: String, note: String, dept: String,
    busy: Boolean, recent: List<CourierEvent>,
    onCompany: (String) -> Unit, onRecipient: (String) -> Unit, onPerson: (String) -> Unit,
    onMobile: (String) -> Unit, onNote: (String) -> Unit, onDept: (String) -> Unit,
    onReceive: () -> Unit, onHandOver: (String) -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        HeaderRow("Courier log / New courier", "Not a Visit — no host approve for lobby receive", onBack)
        Spacer(Modifier.height(12.dp))
        KioskField(label = "courier_company *", value = company, onValueChange = onCompany, placeholder = "e.g. DTDC, FedEx, Delhivery", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "recipient_name *", value = recipient, onValueChange = onRecipient, placeholder = "e.g. staff / host name", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Dept / host (optional)", value = dept, onValueChange = onDept, placeholder = "Accounts", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Courier person (optional)", value = personName, onValueChange = onPerson, placeholder = "Rider", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Courier mobile (optional)", value = mobile, onValueChange = onMobile, placeholder = "10-digit", keyboardType = KeyboardType.Phone, capitalization = KeyboardCapitalization.None, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Package note (optional)", value = note, onValueChange = onNote, placeholder = "Invoice packet", capitalization = KeyboardCapitalization.Sentences, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        KioskPrimaryButton(text = if (busy) "Saving…" else "Mark Received", onClick = onReceive, enabled = !busy && company.isNotBlank() && recipient.isNotBlank(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Text("Recent couriers", color = KioskColors.text, fontWeight = FontWeight.SemiBold, fontFamily = KioskFont)
        Spacer(Modifier.height(8.dp))
        recent.forEach { c ->
            Column(Modifier.fillMaxWidth().clip(ControlShape).background(KioskColors.sidebar).border(1.dp, KioskColors.border, ControlShape).padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${c.courierCompany} → ${c.recipientName}", color = KioskColors.text, fontFamily = KioskFont, modifier = Modifier.weight(1f))
                    Pill(c.status)
                }
                Text(c.packageNote.orEmpty(), color = KioskColors.textMuted, fontSize = 12.sp, fontFamily = KioskFont)
                if (c.status == CourierStatus.RECEIVED) {
                    Spacer(Modifier.height(8.dp))
                    KioskGhostButton(text = "Hand over", onClick = { onHandOver(c.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp))
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
    description: String, location: String, finder: String, finderMobile: String, foundAt: String, busy: Boolean,
    onDescription: (String) -> Unit, onLocation: (String) -> Unit, onFinder: (String) -> Unit,
    onFinderMobile: (String) -> Unit, onFoundAt: (String) -> Unit, onSubmit: () -> Unit, onBack: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        HeaderRow("Lost & Found", "Create Open · Admin manages later", onBack)
        Spacer(Modifier.height(12.dp))
        KioskField(label = "Item description *", value = description, onValueChange = onDescription, placeholder = "Blue bottle", capitalization = KeyboardCapitalization.Sentences, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Location found *", value = location, onValueChange = onLocation, placeholder = "Main Gate", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Date/time found *", value = foundAt, onValueChange = onFoundAt, placeholder = "ISO time", capitalization = KeyboardCapitalization.None, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Finder name *", value = finder, onValueChange = onFinder, placeholder = "Guard", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        KioskField(label = "Finder mobile (optional)", value = finderMobile, onValueChange = onFinderMobile, keyboardType = KeyboardType.Phone, capitalization = KeyboardCapitalization.None, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text("Photo: media/lost_found/placeholder (camera upload later · required on wire).", color = KioskColors.textDim, fontSize = 12.sp, fontFamily = KioskFont)
        Spacer(Modifier.height(12.dp))
        KioskPrimaryButton(text = if (busy) "Saving…" else "Create Open", onClick = onSubmit, enabled = !busy && description.isNotBlank() && location.isNotBlank(), modifier = Modifier.fillMaxWidth())
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
