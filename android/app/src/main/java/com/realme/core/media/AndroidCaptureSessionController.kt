package com.realme.core.media

import android.content.Context
import android.util.Log
import com.realme.data.repository.CaptureSessionController

/**
 * アカウント削除など、データを消す前に撮影セッションを止めるための実装。
 * スクリーンショット撮影はユーザー補助サービスが所有するため、
 * MediaProjection の解放待ちは不要。実行中の保存・登録の完了だけを待つ。
 */
class AndroidCaptureSessionController(@Suppress("UNUSED_PARAMETER") private val context: Context) :
    CaptureSessionController {
    override suspend fun stopAndAwait(): Boolean {
        val startedAt = System.currentTimeMillis()
        Log.i(TAG, "stopAndAwait start active=${CaptureSessionState.active.value}")
        val stopped = CaptureSessionState.stopAndAwait()
        Log.i(
            TAG,
            "stopAndAwait finished stopped=$stopped elapsedMs=${System.currentTimeMillis() - startedAt} " +
                "active=${CaptureSessionState.active.value}",
        )
        return stopped
    }

    private companion object {
        const val TAG = "RealMeCaptureStop"
    }
}
