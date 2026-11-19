package com.realme.core.media

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.realme.R

/**
 * クイック設定からいつでもスクリーンショットを撮るタイル。
 * 撮影後は「スクリーンショットを投稿しますか？」の通知を出し、「はい」で投稿予定を作る。
 */
class ScreenshotTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    // targetSdk 34以降の端末では PendingIntent 版だけを使う（下の分岐）。
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        // 通知シェードを閉じてから撮影するため、透明なActivityを挟む。
        // ユーザー補助が無効なときは有効化の設定を開く。
        val intent = if (ScreenshotAccessibilityService.isEnabled) {
            Intent(this, ScreenshotTriggerActivity::class.java)
        } else {
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val ready = ScreenshotAccessibilityService.isEnabled
        tile.state = if (ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.screenshot_tile_label)
        tile.icon = Icon.createWithResource(this, R.drawable.ic_screenshot_tile)
        tile.subtitle = if (ready) {
            "タップでスクリーンショット"
        } else {
            "ユーザー補助を有効にしてください"
        }
        tile.updateTile()
    }
}
