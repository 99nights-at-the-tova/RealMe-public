package com.realme.core.util

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.realme.MainActivity
import com.realme.R

/** ホーム画面にRealMeを開くショートカットを追加する。 */
object HomeShortcut {
    private const val SHORTCUT_ID = "realme_home"

    fun requestPin(context: Context) {
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID)
            .setShortLabel("RealMe")
            .setLongLabel("RealMe を開く")
            .setIcon(IconCompat.createWithResource(context, R.drawable.realme_logo))
            .setIntent(
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    addCategory(Intent.CATEGORY_LAUNCHER)
                },
            )
            .build()
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }
}
