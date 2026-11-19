package com.realme.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.realme.core.design.InlineError
import com.realme.core.design.Lavender
import com.realme.core.design.RealMeLogo

@Composable
fun AuthScreen(
    state: AuthUiState,
    onSignIn: (String) -> Unit,
    onSelectAccount: (String) -> Unit,
    onClearError: () -> Unit,
    onSubmitPin: (String) -> Unit = {},
    onCancelPin: () -> Unit = {},
) {
    var displayName by rememberSaveable { mutableStateOf("") }
    var pinInput by remember(state.pendingPinAccount?.id) { mutableStateOf("") }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            RealMeLogo(modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                "RealMe",
                modifier = Modifier.testTag("app_title"),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                "その瞬間のゲームを、みんなと。",
                modifier = Modifier.padding(top = 8.dp),
                color = Lavender,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                "外部サービスを使わないローカルMVPです。作成したアカウントと投稿は、この端末内だけに保存されます。",
                modifier = Modifier.padding(top = 20.dp, bottom = 28.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (state.localAccounts.isNotEmpty()) {
                Text("この端末のアカウント", style = MaterialTheme.typography.titleMedium)
                Text(
                    "端末内で切り替えられます。PIN設定済みのアカウントは選択時にPINが必要です。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.localAccounts.forEach { account ->
                    OutlinedButton(
                        onClick = { onSelectAccount(account.id) },
                        modifier = Modifier.fillMaxWidth().testTag("local_account_${account.id}"),
                        enabled = !state.isSubmitting,
                    ) {
                        if (account.id in state.pinProtectedAccountIds) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "PIN保護",
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                        }
                        Text("${account.displayName} (${account.id.take(8)})")
                    }
                }
                Spacer(Modifier.height(20.dp))
                Text("新しいアカウントを作成", style = MaterialTheme.typography.titleMedium)
            }
            OutlinedTextField(
                value = displayName,
                onValueChange = {
                    displayName = it.take(24)
                    onClearError()
                },
                modifier = Modifier.fillMaxWidth().testTag("display_name_input"),
                label = { Text("表示名") },
                placeholder = { Text("例：Player One") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSignIn(displayName) }),
            )
            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                InlineError(state.error)
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSignIn(displayName) },
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("sign_in_button"),
                enabled = displayName.isNotBlank() && !state.isSubmitting,
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("ローカルアカウントで始める")
                }
            }
            Row(Modifier.padding(top = 16.dp)) {
                Text("匿名セッション・登録無料", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    state.pendingPinAccount?.let { account ->
        AlertDialog(
            onDismissRequest = { if (!state.isSubmitting) onCancelPin() },
            title = { Text("${account.displayName}のPIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("この端末内アカウントはPINで保護されています。")
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { value -> pinInput = value.filter(Char::isDigit).take(8) },
                        modifier = Modifier.fillMaxWidth().testTag("pin_input"),
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = {
                            if (pinInput.length in 4..8 && !state.isSubmitting) onSubmitPin(pinInput)
                        }),
                    )
                    state.error?.let { InlineError(it) }
                }
            },
            confirmButton = {
                Button(
                    onClick = { onSubmitPin(pinInput) },
                    modifier = Modifier.testTag("pin_submit"),
                    enabled = pinInput.length in 4..8 && !state.isSubmitting,
                ) { Text(if (state.isSubmitting) "確認中…" else "開く") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = onCancelPin, enabled = !state.isSubmitting) { Text("キャンセル") }
            },
        )
    }
}
