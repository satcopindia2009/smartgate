package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.data.api.ApiConfig
import com.satcop.smartvisitor.kiosk.ui.components.KioskGhostButton
import com.satcop.smartvisitor.kiosk.ui.components.KioskPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.ShieldMark
import com.satcop.smartvisitor.kiosk.ui.theme.CardShape
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.KioskFont

@Composable
fun LoginScreen(
    username: String,
    password: String,
    error: String?,
    busy: Boolean,
    compact: Boolean,
    onUsername: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onFaceLogin: () -> Unit = {},
) {
    val cardHPad = if (compact) 16.dp else 32.dp
    val cardVPad = if (compact) 20.dp else 28.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (compact) 12.dp else 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShieldMark()
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Satcop Smart Visitor",
                    color = KioskColors.text,
                    fontSize = if (compact) 16.sp else 18.sp,
                    fontFamily = KioskFont,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Staff login for Gate · Host · Guard",
                    color = KioskColors.textMuted,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontFamily = KioskFont,
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(24.dp, CardShape, ambientColor = androidx.compose.ui.graphics.Color(0x59000000))
            .clip(CardShape)
            .background(KioskColors.card)
            .border(1.dp, KioskColors.border, CardShape)
            .padding(horizontal = cardHPad, vertical = cardVPad),
        verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 16.dp),
    ) {
        Text(
            text = "Sign in",
            color = KioskColors.text,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = KioskFont,
        )
        Text(
            text = "Staff login for Gate · Host · Guard",
            color = KioskColors.textMuted,
            fontSize = 14.sp,
            fontFamily = KioskFont,
        )
        KioskField(
            label = "Username",
            value = username,
            onValueChange = onUsername,
            placeholder = "pranay.gate",
            keyboardType = KeyboardType.Ascii,
            capitalization = KeyboardCapitalization.None,
            modifier = Modifier.fillMaxWidth(),
        )
        KioskField(
            label = "Password",
            value = password,
            onValueChange = onPassword,
            placeholder = "password",
            keyboardType = KeyboardType.Password,
            capitalization = KeyboardCapitalization.None,
            visualTransformation = PasswordVisualTransformation(),
            error = error,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Faster on campus",
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 8.dp),
        )
        KioskGhostButton(
            text = "Face login",
            onClick = onFaceLogin,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        )
        KioskPrimaryButton(
            text = if (busy) "Signing in…" else "Sign in",
            onClick = onSubmit,
            enabled = !busy,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .heightIn(min = 52.dp),
        )
        Text(
            text = "Need help? Ask school admin",
            color = KioskColors.textMuted,
            fontSize = 12.sp,
            fontFamily = KioskFont,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
