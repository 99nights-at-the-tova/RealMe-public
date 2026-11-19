package com.realme.data.local

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.realme.MainActivity
import com.realme.R
import com.realme.core.media.ReminderActionReceiver
import com.realme.core.model.JST
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * 撮影結果のお知らせ。
 * イベント通知（ランダム通知・勧誘通知）は廃止し、スクリーンショットの投稿確認と
 * 撮影後の「画像を保存しました」通知だけを扱う。
 */
object DailyCaptureReminder {
    private const val CHANNEL = "daily_capture_reminder"
    private const val NOTIFICATION_ID_CAPTURE_READY = 4102
    private const val NOTIFICATION_ID_SCREENSHOT_PROMPT = 4104
    const val ACTION_OPEN_CAPTURE = "com.realme.action.OPEN_CAPTURE_FROM_REMINDER"
    const val ACTION_REVIEW_CAPTURE = "com.realme.action.REVIEW_CAPTURE"
    const val EXTRA_CAPTURE_ID = "capture_id"
    private const val DEFAULT_CAPTURE_NOTIFICATION_TIMEOUT_MS = 10 * 60 * 1000L
    private const val SCREENSHOT_PROMPT_TIMEOUT_MS = 5 * 60 * 1000L

    internal fun autoPostNotificationText(autoPublishAt: Instant?, alreadyPosted: Boolean = false): String {
        val publishTime = autoPublishAt?.let {
            DateTimeFormatter.ofPattern("H:mm").withZone(JST).format(it)
        }
        return when {
            publishTime != null -> "${publishTime}に自動投稿されます"
            alreadyPosted -> "今日は投稿済みです"
            else -> "画像を保存しました"
        }
    }

    /** ゲーム中にスクリーンショットが撮れたときの「投稿しますか？」通知。 */
    fun showScreenshotPrompt(context: Context, imageUri: String): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "撮影のお知らせ", NotificationManager.IMPORTANCE_HIGH))
        if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        val yes = PendingIntent.getBroadcast(
            context,
            4301,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_POST_SCREENSHOT
                putExtra(ReminderActionReceiver.EXTRA_IMAGE_URI, imageUri)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val no = PendingIntent.getBroadcast(
            context,
            4302,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_DISMISS_SCREENSHOT
                putExtra(ReminderActionReceiver.EXTRA_IMAGE_URI, imageUri)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val open = PendingIntent.getActivity(
            context,
            4303,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.realme_logo)
            .setContentTitle("スクリーンショットを投稿しますか？")
            .setContentText("「はい」で自動投稿の予定を作成します")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(SCREENSHOT_PROMPT_TIMEOUT_MS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "はい", yes)
            .addAction(0, "いいえ", no)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_SCREENSHOT_PROMPT, notification)
        return true
    }

    fun cancelScreenshotPrompt(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_SCREENSHOT_PROMPT)
    }

    fun showAutoPostScheduled(
        context: Context,
        captureId: String,
        autoPublishAt: Instant?,
        alreadyPosted: Boolean = false,
    ) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "撮影のお知らせ", NotificationManager.IMPORTANCE_HIGH))

        val text = autoPostNotificationText(autoPublishAt, alreadyPosted)
        val timeoutMs = autoPublishAt?.let {
            (Duration.between(Instant.now(), it).toMillis() + 120_000L).coerceAtLeast(60_000L)
        } ?: DEFAULT_CAPTURE_NOTIFICATION_TIMEOUT_MS

        val reviewIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_REVIEW_CAPTURE
            putExtra(EXTRA_CAPTURE_ID, captureId)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val reviewPending = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_CAPTURE_READY,
            reviewIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.realme_logo)
            .setContentTitle("画像を保存しました")
            .setContentText(text)
            .setContentIntent(reviewPending)
            .setAutoCancel(true)
            .setTimeoutAfter(timeoutMs)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_CAPTURE_READY, notification)
    }
}
