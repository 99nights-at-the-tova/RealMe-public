package com.realme.feature.posting

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import com.realme.core.design.EmptyState
import com.realme.core.design.InlineError
import com.realme.core.design.ExplanationButton
import com.realme.core.model.JST
import java.io.File
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostingScreen(
    state: PostingUiState,
    onDiscard: () -> Unit,
    onSelectImage: (android.net.Uri) -> Unit,
    onCaptionChange: (String) -> Unit,
    onPublish: () -> Unit,
    onPublished: () -> Unit,
    onEditImage: () -> Unit,
    allowTestImagePicker: Boolean,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onSelectImage(uri)
    }
    LaunchedEffect(state.publishedPost?.id) {
        if (state.publishedPost != null) onPublished()
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("投稿を破棄しますか？") },
            text = {
                Text(
                    if (state.captureId != null) {
                        "写真は後で「ライブラリ」項目から投稿できます。"
                    } else {
                        "選択した画像は破棄されます。"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onDiscard()
                }) { Text("はい") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("いいえ") }
            },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("投稿の確認") },
                navigationIcon = {
                    IconButton(onClick = { showDiscardDialog = true }) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                },
                actions = {
                    ExplanationButton(
                        title = "投稿について",
                        message = "通知から5分以内に撮影した投稿は時間内、それより後の撮影は遅刻マークが付きます。5分を過ぎても投稿できます。" +
                            if (allowTestImagePicker) "\n\nギャラリーからの投稿はデバッグビルド専用の検証機能です。" else "",
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("選択した1枚だけをローカルタイムラインへ公開します", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().aspectRatio(4f / 3f), contentAlignment = Alignment.Center) {
                    val path = state.selectedImagePath
                    if (path == null) {
                        EmptyState("画像を選択", "ローカルMVPではPhoto Pickerの画像をテスト投稿に使えます")
                    } else {
                        SubcomposeAsyncImage(
                            model = File(path),
                            contentDescription = "投稿画像プレビュー",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            loading = { CircularProgressIndicator() },
                            error = { Text("画像を読み込めません", modifier = Modifier.padding(24.dp), textAlign = TextAlign.Center) },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = onEditImage,
                modifier = Modifier.fillMaxWidth().testTag("edit_image_button"),
                enabled = state.selectedImagePath != null && !state.isBusy,
            ) {
                Icon(Icons.Default.Edit, contentDescription = null)
                Text("画像を編集", Modifier.padding(start = 8.dp))
            }
            if (allowTestImagePicker) {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth().testTag("pick_post_image"),
                    enabled = !state.isBusy,
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                    Text(if (pathIsSelected(state)) "別の画像を選び直す" else "テスト画像を選択", Modifier.padding(start = 8.dp))
                }
            }
            state.capturedAt?.let {
                Text(
                    "撮影時刻（JST）: ${DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(JST).format(it)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedTextField(
                value = state.caption,
                onValueChange = onCaptionChange,
                modifier = Modifier.fillMaxWidth().testTag("caption_input"),
                label = { Text("キャプション") },
                supportingText = { Text("${state.caption.length}/140") },
                minLines = 2,
                maxLines = 4,
            )
            state.error?.let { InlineError(it) }
            Button(
                onClick = onPublish,
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("publish_button"),
                enabled = state.selectedImagePath != null && state.capturedAt != null && !state.isBusy,
            ) {
                if (state.isBusy) CircularProgressIndicator() else Text("この画像を公開")
            }
        }
    }
}

private fun pathIsSelected(state: PostingUiState) = state.selectedImagePath != null
