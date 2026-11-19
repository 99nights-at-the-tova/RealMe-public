package com.realme

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.realme.core.design.RealMeTheme
import com.realme.feature.auth.AccountUnlockScreen
import com.realme.feature.auth.AuthScreen
import com.realme.feature.auth.AuthViewModel
import com.realme.data.local.DeletionCleanup
import com.realme.data.local.DailyCaptureReminder
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private var captureRequest by mutableIntStateOf(0)
    private var captureToReview by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleReminderIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            RealMeTheme {
                val container = (application as RealMeApplication).container
                val factory = RealMeViewModelFactory(container)
                val authViewModel: AuthViewModel = viewModel(factory = factory)
                val state by authViewModel.uiState.collectAsStateWithLifecycle()
                val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                val mediaPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(state.session?.userId) {
                    val userId = state.session?.userId ?: return@LaunchedEffect
                    container.settingsRepository.touchLastActive(userId, container.timeProvider.now())
                    if (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) {
                        val preferences = getSharedPreferences("notification_permission", MODE_PRIVATE)
                        if (!preferences.getBoolean("asked", false)) {
                            preferences.edit { putBoolean("asked", true) }
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    val mediaPermissionName = if (Build.VERSION.SDK_INT >= 33) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    val mediaGranted = ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        mediaPermissionName,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (!mediaGranted) {
                        // Android 14以降で「一部の写真のみ」を選ばれた場合、新しいスクリーンショットを
                        // 読めず検知できないため、フルアクセスへ戻せるようもう一度だけ確認する。
                        val partialAccess = Build.VERSION.SDK_INT >= 34 &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
                            ) == PackageManager.PERMISSION_GRANTED
                        val preferences = getSharedPreferences("media_permission", MODE_PRIVATE)
                        val askedKey = if (partialAccess) "asked_full" else "asked"
                        if (!preferences.getBoolean(askedKey, false)) {
                            preferences.edit { putBoolean(askedKey, true) }
                            mediaPermission.launch(mediaPermissionName)
                        }
                    }
                }
                val cleanups by container.accountDeletionRepository.cleanups.collectAsStateWithLifecycle(initialValue = emptyList())
                val scope = rememberCoroutineScope()
                Box(Modifier.fillMaxSize()) {
                when {
                    state.isCheckingSession -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                    state.lockedAccount != null -> {
                        AccountUnlockScreen(
                            account = state.lockedAccount!!,
                            isSubmitting = state.isSubmitting,
                            error = state.error,
                            onUnlock = authViewModel::unlock,
                            onSignOut = authViewModel::signOutFromLock,
                        )
                    }
                    state.session == null -> {
                        AuthScreen(
                            state = state,
                            onSignIn = authViewModel::signIn,
                            onSelectAccount = authViewModel::selectAccount,
                            onClearError = authViewModel::clearError,
                            onSubmitPin = authViewModel::submitPin,
                            onCancelPin = authViewModel::cancelPinDialog,
                        )
                    }
                    else -> RealMeNavHost(
                        container = container,
                        factory = factory,
                        sessionUserId = state.session!!.userId,
                        captureRequest = captureRequest,
                        onCaptureRequestHandled = { captureRequest = 0 },
                        captureToReview = captureToReview,
                        onCaptureReviewed = { captureToReview = null },
                    )
                }
                    cleanups.firstOrNull()?.let { cleanup ->
                        DeletionCleanupNotice(
                            cleanup = cleanup,
                            onRetry = { scope.launch { runCatching { container.accountDeletionRepository.retryCleanup() } } },
                            onDismiss = { scope.launch { runCatching { container.accountDeletionRepository.acknowledgeCleanup(cleanup.id) } } },
                            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        when (intent?.action) {
            DailyCaptureReminder.ACTION_OPEN_CAPTURE -> {
                captureToReview = null
                captureRequest += 1
            }
            DailyCaptureReminder.ACTION_REVIEW_CAPTURE -> {
                captureRequest = 0
                captureToReview = intent.getStringExtra(DailyCaptureReminder.EXTRA_CAPTURE_ID)
            }
            else -> return
        }
        intent?.action = null
    }
}

@Composable
fun DeletionCleanupNotice(
    cleanup: DeletionCleanup,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier.fillMaxWidth().testTag("deletion_cleanup_notice")) {
        Column(Modifier.padding(12.dp)) {
            Text(when {
                cleanup.completed -> "アカウントの削除と後片付けが完了しました。"
                cleanup.failed -> "アカウントは削除されましたが、後片付けを完了できませんでした。自動で再試行します。"
                else -> "アカウントは削除されました。画像と自動投稿の後片付けを行っています。"
            })
            Row {
                if (cleanup.completed) {
                    TextButton(onClick = onDismiss, modifier = Modifier.testTag("cleanup_dismiss")) { Text("確認") }
                } else {
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("cleanup_retry")) { Text("再試行") }
                }
            }
        }
    }
}

