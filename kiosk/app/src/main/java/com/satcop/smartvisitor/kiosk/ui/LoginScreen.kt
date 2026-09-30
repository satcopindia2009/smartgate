package com.satcop.smartvisitor.kiosk.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.satcop.smartvisitor.kiosk.ui.components.KioskField
import com.satcop.smartvisitor.kiosk.ui.components.SgPrimaryButton
import com.satcop.smartvisitor.kiosk.ui.components.SgSecondaryButton
import com.satcop.smartvisitor.kiosk.ui.theme.KioskColors
import com.satcop.smartvisitor.kiosk.ui.theme.SgType

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
    // Teal restyle (STYLE-GUIDE / LIGHT/02-login): teal header band + rounded white sheet. Layout only.
    val band = KioskColors.primary
    Column(Modifier.fillMaxWidth().background(band)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .drawBehind {
                    drawCircle(Color.White.copy(alpha = 0.08f), radius = size.width * 0.42f, center = Offset(size.width * 0.95f, size.height * 0.15f))
                    drawCircle(Color.White.copy(alpha = 0.06f), radius = size.width * 0.35f, center = Offset(size.width * 0.05f, size.height * 0.95f))
                }
                .statusBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Row(Modifier.align(Alignment.TopStart), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Satcop Smart Gate", color = Color.White, style = SgType.SectionTitle)
                    Text("Smart Visitor", color = Color.White.copy(alpha = 0.85f), style = SgType.Caption)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = (LocalConfiguration.current.screenHeightDp - 220).dp)
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(KioskColors.card)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Login", color = KioskColors.text, style = SgType.ScreenTitle.copy(fontSize = 28.sp, lineHeight = 34.sp))
                Text("Sign in to your account", color = KioskColors.textMuted, style = SgType.Body)
            }
            KioskField(
                label = "User ID",
                value = username,
                onValueChange = onUsername,
                placeholder = "Enter your User ID",
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                imeAction = ImeAction.Next,
                modifier = Modifier.fillMaxWidth(),
            )
            KioskField(
                label = "Password",
                value = password,
                onValueChange = onPassword,
                placeholder = "Enter your password",
                keyboardType = KeyboardType.Password,
                capitalization = KeyboardCapitalization.None,
                autoCorrect = false,
                passwordToggle = true,
                imeAction = ImeAction.Go,
                onImeAction = onSubmit,
                error = error,
                modifier = Modifier.fillMaxWidth(),
            )
            com.satcop.smartvisitor.kiosk.ui.otp.ForgotPasswordEntry()
            SgPrimaryButton(
                text = if (busy) "Signing in…" else "Login",
                onClick = onSubmit,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            SgSecondaryButton(
                text = "Face login",
                onClick = onFaceLogin,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
