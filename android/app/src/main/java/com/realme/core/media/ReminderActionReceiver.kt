package com.realme.core.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.realme.RealMeApplication
import com.realme.core.model.DraftStatus
import com.realme.data.local.DailyCaptureReminder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 通知のアクションから、アプリを開かずに撮影イベントや投稿予定を作成する。
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_POST_SCREENSHOT -> handlePostScreenshot(context, intent)
            ACTION_DISMISS_SCREENSHOT -> handleDismissScreenshot(context, intent)
        }
    }

    private fun handlePostScreenshot(context: Context, intent: Intent) {
        val imageUri = intent.getStringExtra(EXTRA_IMAGE_URI) ?: return
        val pending = goAsync()
        val container = (context.applicationContext as RealMeApplication).container
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val userId = container.authRepository.session.first()?.userId ?: return@launch
                val now = container.timeProvider.now()
                val date = container.timeProvider.todayJst()
                // スクリーンショットを当日の撮影として扱い、自動投稿の下書きを作る。
                container.eventRepository.startNotificationNow(date, now)
                val uri = imageUri.toUri()
                val path = if (uri.scheme == "file") {
                    // クイック設定タイルで撮影した一時ファイルは、再エンコードせずに取り込む。
                    val sourcePath = uri.path ?: return@launch
                    container.imageFiles.copyInto(sourcePath, "captures")
                } else {
                    container.imageFiles.importImage(uri, "captures")
                }
                val capture = container.captureRepository.registerCapture(userId, path, now).getOrNull()
                    ?: return@launch
                val draft = container.postRepository.drafts.first().firstOrNull {
                    it.userId == userId && it.eventId == capture.eventId && it.status == DraftStatus.ACTIVE
                }
                DailyCaptureReminder.showAutoPostScheduled(
                    context.applicationContext,
                    capture.id,
                    draft?.autoPublishAt,
                )
                DailyCaptureReminder.cancelScreenshotPrompt(context.applicationContext)
                if (uri.scheme == "file") deleteOwnedFile(context, uri.path)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleDismissScreenshot(context: Context, intent: Intent) {
        DailyCaptureReminder.cancelScreenshotPrompt(context.applicationContext)
        // クイック設定タイルの一時ファイルだけを削除する（端末のスクリーンショットは残す）。
        val uri = intent.getStringExtra(EXTRA_IMAGE_URI)?.let { runCatching { it.toUri() }.getOrNull() }
        if (uri?.scheme == "file") deleteOwnedFile(context, uri.path)
    }

    private fun deleteOwnedFile(context: Context, path: String?) {
        if (path == null) return
        runCatching {
            val root = context.filesDir.canonicalFile
            val file = java.io.File(path).canonicalFile
            if (file.path.startsWith(root.path + java.io.File.separator)) file.delete()
        }
    }

    companion object {
        const val ACTION_POST_SCREENSHOT = "com.realme.action.POST_SCREENSHOT"
        const val ACTION_DISMISS_SCREENSHOT = "com.realme.action.DISMISS_SCREENSHOT"
        const val EXTRA_IMAGE_URI = "image_uri"
    }
}

