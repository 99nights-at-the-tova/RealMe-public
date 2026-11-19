package com.realme.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.realme.core.design.InlineError
import com.realme.core.design.Mint
import com.realme.core.model.UserProfile

@Composable
fun AccountUnlockScreen(
    account: UserProfile,
    isSubmitting: Boolean,
    error: String?,
    onUnlock: (String) -> Unit,
    onSignOut: () -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier.size(76.dp).background(Mint, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(38.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("PINロック", style = MaterialTheme.typography.headlineMedium)
            Text(
                account.displayName,
                Modifier.padding(top = 6.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "このアカウントはPINで保護されています。",
                Modifier.padding(top = 8.dp, bottom = 20.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = pin,
                onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                modifier = Modifier.fillMaxWidth().testTag("unlock_pin_input"),
                label = { Text("PIN") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = {
                    if (pin.length in 4..8 && !isSubmitting) onUnlock(pin)
                }),
                singleLine = true,
                enabled = !isSubmitting,
            )
            error?.let {
                Spacer(Modifier.height(10.dp))
                InlineError(it)
            }
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { onUnlock(pin) },
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("unlock_button"),
                enabled = pin.length in 4..8 && !isSubmitting,
            ) {
                if (isSubmitting) CircularProgressIndicator(Modifier.size(22.dp)) else Text("ロックを解除")
            }
            TextButton(onClick = onSignOut, enabled = !isSubmitting) {
                Text("別のアカウントを選ぶ")
            }
        }
    }
}
