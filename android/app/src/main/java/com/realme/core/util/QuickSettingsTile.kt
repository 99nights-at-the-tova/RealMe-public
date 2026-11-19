package com.realme.core.util

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Icon
import android.os.Build
import com.realme.R
import com.realme.core.media.ScreenshotTileService
import java.util.function.Consumer

/** クイック設定にスクリーンショットタイルを追加する。 */
object QuickSettingsTile {
    /** 追加ダイアログを表示できたらtrue。Android 13未満は手動追加が必要。 */
    fun requestAdd(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 33) return false
        val manager = context.getSystemService(StatusBarManager::class.java) ?: return false
        val component = ComponentName(context, ScreenshotTileService::class.java)
        val icon = Icon.createWithResource(context, R.drawable.ic_screenshot_tile)
        return runCatching {
            manager.requestAddTileService(
                component,
                context.getString(R.string.screenshot_tile_label),
                icon,
                context.mainExecutor,
                Consumer<Int> { },
            )
            true
        }.getOrDefault(false)
    }
}
