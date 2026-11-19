package com.realme.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.realme.data.local.LocalSettingsRepository
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoPostSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
    onClearMessage: () -> Unit,
) {
    SettingsScaffold("自動投稿設定", onBack, state.message, onClearMessage) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("最初の有効な撮影を起点に、操作がなければ下書きを自動公開します。追加撮影で起点は変わりません。")
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("現在の設定: ${state.settings?.autoPostMinutes ?: 5}分", style = MaterialTheme.typography.titleLarge)
                    LocalSettingsRepository.AUTO_POST_CHOICES.chunked(4).forEach { choices ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            choices.forEach { minutes ->
                                FilterChip(
                                    selected = state.settings?.autoPostMinutes == minutes,
                                    onClick = { onSelect(minutes) },
                                    label = { Text("${minutes}分") },
                                    modifier = Modifier.testTag("auto_post_$minutes"),
                                )
                            }
                        }
                    }
                }
            }
            InfoBlock("未編集", "設定値にかかわらず最大15分で公開期限を計算します。")
            InfoBlock("編集を開始", "仕様上は最大45分です。画像編集Stageで、未編集時の期限前にEDITINGを登録した場合だけ延長します。")
            InfoBlock("手動投稿・キャンセル", "手動投稿すると予定を取り消します。キャンセルした下書きは公開しません。")
            Card {
                Text(
                    "ローカルMVPの自動投稿はWorkManagerで動き、秒単位の時刻や端末終了後の到達は保証しません。本番ではSupabase Cronへ置き換えます。",
                    Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacySettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSelectImage: (android.net.Uri) -> Unit,
    onPublish: (String) -> Unit,
    onPublished: () -> Unit,
    onClearMessage: () -> Unit,
) {
    var caption by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSelectImage(uri)
    }
    LaunchedEffect(state.publishedPost?.id) {
        if (state.publishedPost != null) onPublished()
    }
    SettingsScaffold("プライバシー画像", onBack, state.message, onClearMessage) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("ゲームをしていない日や画面を公開したくない日に、事前設定した1枚をPRIVACYとして投稿できます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth().height(320.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val path = state.settings?.privacyImagePath
                    if (path == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(48.dp))
                            Text("画像が未設定です", Modifier.padding(top = 12.dp), textAlign = TextAlign.Center)
                        }
                    } else {
                        SubcomposeAsyncImage(
                            model = File(path),
                            contentDescription = "プライバシー画像プレビュー",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = { CircularProgressIndicator() },
                            error = { Text("画像を読み込めません") },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth().testTag("pick_privacy_image"),
                enabled = !state.isBusy,
            ) { Text(if (state.isBusy) "画像を保存中…" else "Photo Pickerで画像を選択") }
            Text(
                "選択画像は端末内へ圧縮コピーされます。公開済みのゲーム画像を差し替えても、他人が保存済みのコピーやキャッシュの完全な回収は保証できません。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it.take(140) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("キャプション（任意）") },
            )
            Button(
                onClick = { onPublish(caption) },
                enabled = state.settings?.privacyImagePath != null && !state.isBusy,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("publish_privacy_button"),
            ) { Text("この画像で今日の投稿") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureSettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSave: (Int, Float) -> Unit,
    onClearMessage: () -> Unit,
) {
    var size by rememberSaveable { mutableIntStateOf(64) }
    var opacity by rememberSaveable { mutableFloatStateOf(0.85f) }
    LaunchedEffect(state.settings?.userId) {
        state.settings?.let {
            size = it.overlaySizeDp
            opacity = it.overlayOpacity
        }
    }
    SettingsScaffold("フローティングボタン", onBack, state.message, onClearMessage) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(size.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = opacity), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.SportsEsports, contentDescription = "撮影ボタンプレビュー", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Text("サイズ: ${size}dp")
            Slider(
                value = size.toFloat(),
                onValueChange = { size = it.toInt() },
                onValueChangeFinished = { onSave(size, opacity) },
                valueRange = 48f..96f,
            )
            Text("透明度: ${(opacity * 100).toInt()}%")
            Slider(
                value = opacity,
                onValueChange = { opacity = it },
                onValueChangeFinished = { onSave(size, opacity) },
                valueRange = 0.25f..1f,
            )
            Card {
                Text(
                    "位置はボタンをドラッグすると保存されます。サイズと透明度は次回の撮影セッション開始時に適用されます。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSecurityScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSavePin: (String?, String) -> Unit,
    onRemovePin: (String) -> Unit,
    onClearMessage: () -> Unit,
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    SettingsScaffold("端末内PIN", onBack, state.message, onClearMessage) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                if (state.hasPin) {
                    "このローカルアカウントはPINで保護されています。アカウント選択時にPINが必要です。"
                } else {
                    "4〜8桁の数字でPINを設定すると、端末内のアカウント切替時に本人確認を行います。"
                },
            )
            if (state.hasPin) {
                OutlinedTextField(
                    value = currentPin,
                    onValueChange = { currentPin = it.filter(Char::isDigit).take(8) },
                    label = { Text("現在のPIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = newPin,
                onValueChange = { newPin = it.filter(Char::isDigit).take(8) },
                label = { Text(if (state.hasPin) "新しいPIN" else "PIN") },
                supportingText = { Text("4〜8桁の数字") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth().testTag("new_pin_input"),
            )
            Button(
                onClick = {
                    onSavePin(currentPin.ifBlank { null }, newPin)
                    currentPin = ""
                    newPin = ""
                },
                enabled = newPin.length in 4..8 && (!state.hasPin || currentPin.length in 4..8) && !state.isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (state.hasPin) "PINを変更" else "PINを設定") }
            if (state.hasPin) {
                OutlinedButton(
                    onClick = {
                        onRemovePin(currentPin)
                        currentPin = ""
                    },
                    enabled = currentPin.length in 4..8 && !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("PINを解除") }
            }
            Card {
                Text(
                    "PINはこの端末内の切替を保護する簡易機能です。パスワード保護や暗号化されたサーバー認証ではなく、端末内データ自体の暗号化は行いません。",
                    Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteAccountScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onDelete: (String?) -> Unit,
    onClearMessage: () -> Unit,
) {
    var confirmation by rememberSaveable { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    SettingsScaffold("アカウント削除", onBack, state.message, onClearMessage) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding()
                .verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            )) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("この操作は取り消せません", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "プロフィール、投稿、撮影候補、下書き、プライバシー画像、設定、PIN、保存履歴をこの端末から削除します。投稿に紐づかない写真選択時の一時コピーや、他のユーザーが端末へ保存済みの画像は回収できません。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            OutlinedTextField(
                value = confirmation,
                onValueChange = { confirmation = it.take(10) },
                label = { Text("確認のため「削除」またはDELETEと入力") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("delete_confirmation"),
            )
            if (state.hasPin) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("現在のPIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("delete_pin"),
                )
            }
            Button(
                onClick = { onDelete(pin.ifBlank { null }) },
                enabled = (confirmation == "削除" || confirmation == "DELETE") &&
                    (!state.hasPin || pin.length in 4..8) &&
                    !state.isBusy,
                modifier = Modifier.fillMaxWidth().height(52.dp)
                    .testTag("delete_account_button"),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(if (state.isBusy) "削除中…" else "アカウントを完全に削除") }
        }
    }
}

@Composable
private fun InfoBlock(title: String, body: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Text(body, Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    message: String?,
    onClearMessage: () -> Unit,
    content: @Composable (androidx.compose.foundation.layout.PaddingValues) -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            onClearMessage()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        content = content,
    )
}

