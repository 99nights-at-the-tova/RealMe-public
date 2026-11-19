package com.realme.feature.capture

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.realme.core.design.EmptyState
import com.realme.core.media.CaptureSessionState
import com.realme.core.media.ScreenshotAccessibilityService
import com.realme.core.model.JST
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    state: CaptureUiState,
    startFromReminder: Boolean = false,
    onReminderHandled: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onOpenCapture: (String) -> Unit,
    onDelete: (String) -> Unit,
    onPublishNow: (String) -> Unit,
    onCancelDraft: () -> Unit,
    onAddHomeShortcut: () -> Unit,
    onAddQuickSettingsTile: () -> Unit,
    onClearMessage: () -> Unit,
) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val accessibilityEnabled by ScreenshotAccessibilityService.connected.collectAsStateWithLifecycle()
    val running by CaptureSessionState.active.collectAsStateWithLifecycle()
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val requestCapture = {
        if (ScreenshotAccessibilityService.isEnabled) {
            ScreenshotAccessibilityService.requestOverlay(context)
        } else {
            ScreenshotAccessibilityService.openAccessibilitySettings(context)
            permissionMessage = ENABLE_ACCESSIBILITY_MESSAGE
        }
    }
    LaunchedEffect(startFromReminder) {
        if (startFromReminder) {
            onReminderHandled()
            if (!ScreenshotAccessibilityService.isEnabled) {
                ScreenshotAccessibilityService.openAccessibilitySettings(context)
                permissionMessage = ENABLE_ACCESSIBILITY_MESSAGE
            } else {
                ScreenshotAccessibilityService.requestOverlay(context)
            }
        }
    }
    LaunchedEffect(permissionMessage) {
        permissionMessage?.let {
            snackbar.showSnackbar(it)
            permissionMessage = null
        }
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
                title = { Text("ゲーム撮影") },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, contentDescription = "ボタン設定") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize().padding(padding).testTag("capture_list"),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                        Text("撮影のお知らせを受け取るには、通知を許可してください。", color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }) { Text("通知設定を開く") }
                    }
                    Button(
                        onClick = requestCapture,
                        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("start_capture_button"),
                    ) {
                        Text(
                            when {
                                !accessibilityEnabled -> "ユーザー補助を有効にする"
                                state.captures.isNotEmpty() -> "もう一度撮影する"
                                else -> "撮影ボタンを表示"
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { scope.launch { CaptureSessionState.stopAndAwait() } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = running,
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null)
                        Text("撮影ボタンを隠す", Modifier.padding(start = 8.dp))
                    }
                    Text(
                        if (accessibilityEnabled) {
                            "「撮影ボタンを表示」で画面上に撮影ボタンを出せます。撮影すると、自動投稿の時刻を知らせる通知が出ます。取り直す場合は、その時刻までにこの画面で「もう一度撮影する」を押してください。"
                        } else {
                            "初回のみ「設定 > ユーザー補助 > RealMe 画面撮影」をオンにしてください。以降は撮影ボタンを表示でき、画面共有の承認は不要です。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = onAddHomeShortcut,
                        modifier = Modifier.testTag("add_home_shortcut"),
                    ) {
                        Text("ホーム画面にショートカットを追加")
                    }
                    TextButton(
                        onClick = onAddQuickSettingsTile,
                        modifier = Modifier.testTag("add_qs_tile"),
                    ) {
                        Text("クイック設定に撮影タイルを追加")
                    }
                    Text(
                        "クイック設定のタイルは、ゲーム中でも通知シェードからいつでも撮影できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            state.activeDraft?.let { draft ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    DraftCard(draft.autoPublishAt, onCancelDraft)
                }
            }

            val todayDate = state.event.eventDateJst
            val todays = state.captures.filter { it.capturedAt.atZone(JST).toLocalDate() == todayDate }
            val archive = state.captures.filter { it.capturedAt.atZone(JST).toLocalDate() != todayDate }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("あとで編集（${todays.size}）", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "今日の撮影画像です。チェックで即時投稿、確認から編集できます。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (todays.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(
                        title = "今日の撮影画像はまだありません",
                        description = "通知後に撮影すると、ここに追加されます。",
                    )
                }
            }
            items(todays, key = { it.id }) { capture ->
                CaptureCard(
                    capture = capture,
                    canPublish = true,
                    label = "投稿候補",
                    onPublishNow = onPublishNow,
                    onOpenCapture = onOpenCapture,
                    onDelete = onDelete,
                )
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("過去のアーカイブ（${archive.size}）", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (archive.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        "過去の撮影画像はまだありません。",
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(archive, key = { it.id }) { capture ->
                    CaptureCard(
                        capture = capture,
                        canPublish = false,
                        label = "アーカイブ",
                        onPublishNow = onPublishNow,
                        onOpenCapture = onOpenCapture,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureCard(
    capture: com.realme.core.model.Capture,
    canPublish: Boolean,
    label: String,
    onPublishNow: (String) -> Unit,
    onOpenCapture: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                SubcomposeAsyncImage(
                    model = File(capture.filePath),
                    contentDescription = "撮影画像 ${capture.capturedAt}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    error = { Text("読み込みエラー", Modifier.padding(8.dp)) },
                )
                Text(
                    label,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                    color = if (label == "投稿候補") {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                DateTimeFormatter.ofPattern("HH:mm:ss").withZone(JST).format(capture.capturedAt),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = { onPublishNow(capture.id) }, enabled = canPublish) {
                    Icon(Icons.Default.Check, contentDescription = "この画像を投稿")
                }
                TextButton(onClick = { onOpenCapture(capture.id) }) { Text("確認") }
                IconButton(onClick = { onDelete(capture.id) }) {
                    Icon(Icons.Default.Delete, contentDescription = "撮影画像を削除")
                }
            }
        }
    }
}

@Composable
private fun DraftCard(autoPublishAt: Instant, onCancel: () -> Unit) {
    var now by remember(autoPublishAt) { mutableStateOf(Instant.now()) }
    LaunchedEffect(autoPublishAt) {
        while (isActive) {
            now = Instant.now()
            delay(1_000)
        }
    }
    val seconds = Duration.between(now, autoPublishAt).seconds.coerceAtLeast(0)
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text("自動投稿まで %d:%02d".format(seconds / 60, seconds % 60), style = MaterialTheme.typography.titleLarge)
            Text("最初の撮影時刻を起点にしています。チェックボタンを押すと、その画像をすぐに投稿できます。", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("自動投稿をキャンセル") }
        }
    }
}

private const val ENABLE_ACCESSIBILITY_MESSAGE =
    "初回のみ、設定 > ユーザー補助 > RealMe 画面撮影 をオンにしてください。"
