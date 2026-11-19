package com.realme.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.ListItemDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realme.core.design.ProfileAvatar
import com.realme.core.design.ExplanationButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onSave: (String, String) -> Unit,
    onOpenAutoPost: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenCaptureSettings: () -> Unit,
    onOpenSecurity: () -> Unit,
    onOpenDeleteAccount: () -> Unit,
    onSignOut: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var name by rememberSaveable { mutableStateOf("") }
    var bio by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.profile?.id) {
        name = state.profile?.displayName.orEmpty()
        bio = state.profile?.bio.orEmpty()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onClearMessage()
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("プロフィール") },
                actions = {
                    ExplanationButton(
                        title = "連続記録について",
                        message = "連続記録はログイン日ではなく、投稿した日本時間の日付で計算します。",
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ProfileAvatar(state.profile?.displayName ?: "?", Modifier)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(state.profile?.displayName ?: "読み込み中", style = MaterialTheme.typography.headlineMedium)
                    Text("ローカル匿名アカウント", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                StatItem("現在", "${state.streak.current}日", Modifier.weight(1f))
                VerticalDivider(Modifier.height(40.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                StatItem("最高", "${state.streak.best}日", Modifier.weight(1f))
                VerticalDivider(Modifier.height(40.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                StatItem("累計", "${state.streak.total}日", Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(24) },
                modifier = Modifier.fillMaxWidth().testTag("profile_name_input"),
                label = { Text("表示名") },
                singleLine = true,
            )
            OutlinedTextField(
                value = bio,
                onValueChange = { bio = it.take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("自己紹介") },
                minLines = 3,
                maxLines = 4,
            )
            Button(
                onClick = { onSave(name, bio) },
                enabled = name.isNotBlank() && !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (state.isSaving) "保存中…" else "プロフィールを保存") }
            Spacer(Modifier.height(4.dp))
            Text("設定", style = MaterialTheme.typography.titleMedium)
            Column {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                SettingsLink("自動投稿", "初回撮影からの時間・15分上限", Icons.Default.Schedule, onOpenAutoPost)
                SettingsLink("プライバシー画像", "ゲーム画面の代わりに投稿", Icons.Default.Image, onOpenPrivacy)
                SettingsLink("フローティングボタン", "サイズと透明度", Icons.Default.Palette, onOpenCaptureSettings)
                SettingsLink("端末内PIN", "アカウント切替を保護", Icons.Default.Lock, onOpenSecurity)
                SettingsLink("アカウント削除", "端末内の投稿とデータを削除", Icons.Default.DeleteForever, onOpenDeleteAccount)
            }
            TextButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
                Text("ローカルアカウントからサインアウト", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 8.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingsLink(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.clickable(onClick = onClick),
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) },
            leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    }
}
