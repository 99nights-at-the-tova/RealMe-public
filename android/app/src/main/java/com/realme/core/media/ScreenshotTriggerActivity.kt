package com.realme.core.media

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings

/**
 * クイック設定タイルから通知シェードを閉じるための透明なActivity。
 * 画面は表示せず、ユーザー補助サービスへ撮影を依頼してすぐ終了する。
 */
class ScreenshotTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!ScreenshotAccessibilityService.captureForPrompt()) {
            startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        finish()
    }
}
