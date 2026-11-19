package com.realme.core.media

import com.realme.BuildConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * スクリーンショット撮影セッションの状態と停止要求を、
 * [ScreenshotAccessibilityService] とアカウント削除処理の間で共有する。
 *
 * 画面共有(MediaProjection)の同意トークンは保持しない。停止要求は
 * 実行中の撮影と後片付けが終わるまで待機してから完了を返す。
 */
object CaptureSessionState {
    private val _active = MutableStateFlow(false)
    /** 撮影ボタン(オーバーレイ)が表示され、撮影セッションが有効かどうか。 */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val lock = Any()
    private var stopHandler: (suspend () -> Unit)? = null

    @Volatile
    internal var testCleanupCompleted = false
        private set

    fun attach(handler: suspend () -> Unit) {
        synchronized(lock) { stopHandler = handler }
    }

    fun detach() {
        synchronized(lock) { stopHandler = null }
        _active.value = false
    }

    fun setActive(value: Boolean) {
        _active.value = value
    }

    /**
     * Stop the active capture session and wait for in-flight work and cleanup.
     * Returns true when the session is stopped (or was not running).
     */
    suspend fun stopAndAwait(): Boolean {
        val handler = synchronized(lock) { stopHandler } ?: return true
        return withTimeoutOrNull(STOP_TIMEOUT_MS) {
            handler()
            true
        } ?: false
    }

    /** Debug instrumentation only: exercises stop waiting without system consent. */
    fun startTestSession() {
        check(BuildConfig.DEBUG) { "Test capture sessions are debug-only" }
        testCleanupCompleted = false
        _active.value = true
        synchronized(lock) {
            stopHandler = {
                delay(300)
                testCleanupCompleted = true
                synchronized(lock) { stopHandler = null }
                _active.value = false
            }
        }
    }

    private const val STOP_TIMEOUT_MS = 10_000L
}
