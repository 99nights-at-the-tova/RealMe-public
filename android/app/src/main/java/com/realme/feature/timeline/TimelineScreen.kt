package com.realme.feature.timeline

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.realme.core.design.EmptyState
import com.realme.core.design.PostImage
import com.realme.core.design.ProfileAvatar
import com.realme.core.design.StatusBadge
import com.realme.core.design.ExplanationButton
import com.realme.core.design.RealMeLogo
import com.realme.core.model.JST
import com.realme.core.model.Post
import com.realme.core.model.PostStatus
import kotlinx.coroutines.isActive
import java.time.format.DateTimeFormatter

private val postedAtFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(JST)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(
    state: TimelineUiState,
    isDebug: Boolean,
    onCreatePost: () -> Unit,
    onStartDemo: (PostStatus) -> Unit,
    onDelete: (String) -> Unit,
    onReplaceWithPrivacy: (String) -> Unit,
    onClearMessage: () -> Unit,
    onRefreshClock: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<Post?>(null) }
    var privacyTarget by remember { mutableStateOf<Post?>(null) }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onClearMessage()
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(30_000)
            onRefreshClock()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RealMeLogo(Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("RealMe", fontWeight = FontWeight.Bold)
                    }
                },
            )
        },
        floatingActionButton = {
            if (isDebug) {
                ExtendedFloatingActionButton(
                    onClick = onCreatePost,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("画像を投稿") },
                    modifier = Modifier.testTag("create_post_button"),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("timeline_list"),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 104.dp),
        ) {
            if (isDebug) {
                item {
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("デバッグイベント", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                ExplanationButton("デバッグイベント", "実際の通知を待たずに撮影時刻の判定を試せます。")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { onStartDemo(PostStatus.ON_TIME) }) { Text("時間内のデモ") }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    }
                }
            }
            if (!state.isLoading && state.entries.isEmpty()) {
                item {
                    EmptyState(
                        title = "最初の瞬間を共有しよう",
                        description = "右下のボタンから画像を選ぶか、ライブラリタブでゲーム画面をキャプチャできます。",
                    )
                }
            }
            items(state.entries, key = { "${it.post.userId}-${it.post.eventDateJst}" }) { entry ->
                PostItem(
                    entry = entry,
                    isOwnPost = entry.post.userId == state.currentUserId,
                    onDelete = { deleteTarget = it },
                    onReplaceWithPrivacy = { privacyTarget = it },
                )
            }
        }
    }
    deleteTarget?.let { post ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("投稿を削除しますか？") },
            text = { Text("ローカルタイムラインとアプリ内の画像ファイルから削除します。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(post.id)
                    deleteTarget = null
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") } },
        )
    }
    privacyTarget?.let { post ->
        AlertDialog(
            onDismissRequest = { privacyTarget = null },
            title = { Text("プライバシー画像へ差し替え") },
            text = { Text("投稿IDと連続記録を維持し、元のゲーム画像を削除します。他のユーザーが保存済みの画像やキャッシュは回収できません。") },
            confirmButton = {
                TextButton(onClick = {
                    onReplaceWithPrivacy(post.id)
                    privacyTarget = null
                }) { Text("差し替える") }
            },
            dismissButton = { TextButton(onClick = { privacyTarget = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun PostItem(
    entry: TimelineEntry,
    isOwnPost: Boolean,
    onDelete: (Post) -> Unit,
    onReplaceWithPrivacy: (Post) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(
        initialPage = (entry.posts.size - 1).coerceAtLeast(0),
        pageCount = { entry.posts.size },
    )
    val currentPost = entry.posts.getOrNull(pagerState.currentPage) ?: entry.post
    Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(entry.profile.displayName)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.profile.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        postedAtFormatter.format(currentPost.postedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusBadge(currentPost.status)
                if (isOwnPost) {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "投稿メニュー")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (currentPost.status != PostStatus.PRIVACY) {
                                DropdownMenuItem(
                                    text = { Text("プライバシー画像へ差し替え") },
                                    onClick = {
                                        menuExpanded = false
                                        onReplaceWithPrivacy(currentPost)
                                    },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("投稿を削除") },
                                onClick = {
                                    menuExpanded = false
                                    onDelete(currentPost)
                                },
                            )
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                ) { page ->
                    PostImage(entry.posts[page], Modifier.fillMaxSize())
                }
                if (entry.posts.size > 1) {
                    Text(
                        "${pagerState.currentPage + 1}/${entry.posts.size}",
                        modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                if (currentPost.caption.isNotBlank()) {
                    Text(currentPost.caption, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${entry.streak.current}日連続・累計${entry.streak.total}日",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    }
}


