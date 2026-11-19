package com.realme.core.media

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.ContentUris
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.VelocityTracker
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.realme.RealMeApplication
import com.realme.core.model.DraftStatus
import com.realme.core.model.JST
import com.realme.core.model.UserSettings
import com.realme.data.local.DailyCaptureReminder
import com.realme.data.repository.CaptureRepository
import com.realme.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * ユーザー補助サービスとして画面のスクリーンショットを取得する。
 *
 * MediaProjection のような「画面共有の承認」は不要で、初回だけ
 * 設定 > ユーザー補助 で本サービスを有効にすればよい。
 * 撮影ボタンは [WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY] で
 * 表示するため、「他のアプリの上に表示」の許可も不要。
 */
class ScreenshotAccessibilityService : AccessibilityService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container by lazy { (application as RealMeApplication).container }
    private val captureRepository: CaptureRepository by lazy { container.captureRepository }
    private val settingsRepository: SettingsRepository by lazy { container.settingsRepository }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val capturing = AtomicBoolean(false)

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var userId: String? = null
    private var settings: UserSettings? = null
    private var lastUserId: String? = null
    private var lastEventId: String? = null
    private var forcedEventId: String? = null
    private var currentForegroundPackage: String? = null
    private var screenshotObserver: ContentObserver? = null
    private val processedScreenshotIds = LinkedHashSet<Long>()
    private val screenshotLock = Any()
    private var checkingScreenshot = false
    private var sessionAttached = false
    @Volatile
    private var stopped = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        stopped = false
        instance = this
        _connected.value = true
        Log.i(TAG, "onServiceConnected connected=true sdk=${Build.VERSION.SDK_INT}")
        windowManager = getSystemService(WindowManager::class.java)
        attachSession()
        registerScreenshotObserver()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            serviceScope.launch { observeCaptureWindow() }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        handleForegroundPackage(pkg)
    }

    /**
     * スクリーンショット検知のために、いま前面にあるアプリを記録する。
     * システムUIの一時表示は無視し、ランチャーやRealMe自身で文脈を終了する。
     */
    private fun handleForegroundPackage(pkg: String) {
        currentForegroundPackage = when {
            pkg == packageName || pkg in CLEAR_CONTEXT_PACKAGES -> null
            pkg in TRANSIENT_OVERLAY_PACKAGES -> currentForegroundPackage
            else -> pkg
        }
    }

    private fun registerScreenshotObserver() {
        if (screenshotObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                serviceScope.launch { checkLatestScreenshot() }
            }
        }
        runCatching {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer,
            )
            screenshotObserver = observer
            Log.i(TAG, "screenshot observer registered")
        }.onFailure { Log.w(TAG, "screenshot observer registration failed", it) }
    }

    private suspend fun checkLatestScreenshot() {
        synchronized(screenshotLock) {
            if (checkingScreenshot) return
            checkingScreenshot = true
        }
        try {
            collectRecentScreenshots()
        } finally {
            synchronized(screenshotLock) { checkingScreenshot = false }
        }
    }

    private suspend fun collectRecentScreenshots() {
        val now = container.timeProvider.now()
        val sinceSeconds = now.epochSecond - SCREENSHOT_QUERY_WINDOW_SECONDS
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED,
        )
        val cursor = withContext(Dispatchers.IO) {
            runCatching {
                contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    "${MediaStore.Images.Media.DATE_ADDED} >= ?",
                    arrayOf(sinceSeconds.toString()),
                    "${MediaStore.Images.Media.DATE_ADDED} ASC",
                )
            }.getOrNull()
        } ?: return
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                if (!markScreenshotProcessed(id)) continue
                val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)) ?: ""
                val relative = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)) ?: ""
                val addedMs = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)) * 1000L
                val isScreenshot = relative.contains("Screenshot", ignoreCase = true) ||
                    name.startsWith("Screenshot", ignoreCase = true) ||
                    name.startsWith("スクリーンショット")
                if (!isScreenshot) continue
                if (now.toEpochMilli() - addedMs > SCREENSHOT_FRESH_MS) continue
                if (!isGameContext()) {
                    Log.i(TAG, "screenshot ignored id=$id name=$name reason=not-game current=$currentForegroundPackage")
                    // ゲーム外のスクリーンショットでは、残っている投稿確認も消す。
                    DailyCaptureReminder.cancelScreenshotPrompt(applicationContext)
                    continue
                }
                val uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                Log.i(TAG, "screenshot detected id=$id name=$name package=$currentForegroundPackage")
                DailyCaptureReminder.showScreenshotPrompt(applicationContext, uri.toString())
            }
        }
    }

    /**
     * スクリーンショットを投稿確認の対象にするか。
     * 撮影時点で前面のアプリがゲーム（Androidの appCategory=game）のときだけ対象にする。
     */
    private fun isGameContext(): Boolean {
        val pkg = currentForegroundPackage ?: return false
        return isGamePackage(pkg)
    }

    /** Androidのアプリカテゴリが game のパッケージかどうか。 */
    private fun isGamePackage(pkg: String): Boolean = runCatching {
        val info = packageManager.getApplicationInfo(pkg, 0)
        Log.i(TAG, "game check pkg=$pkg category=${info.category}")
        info.category == ApplicationInfo.CATEGORY_GAME
    }.onFailure { Log.w(TAG, "game check failed pkg=$pkg", it) }.getOrDefault(false)

    /** 同じ画像を何度も処理しない。処理済みIDは上限件数まで保持する。 */
    private fun markScreenshotProcessed(id: Long): Boolean {
        synchronized(screenshotLock) {
            if (!processedScreenshotIds.add(id)) return false
            while (processedScreenshotIds.size > MAX_TRACKED_SCREENSHOTS) {
                val oldest = processedScreenshotIds.first()
                processedScreenshotIds.remove(oldest)
            }
        }
        return true
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun attachSession() {
        if (sessionAttached) return
        sessionAttached = true
        CaptureSessionState.attach { stopCaptureAndAwait() }
    }

    private fun detachSession() {
        if (!sessionAttached) return
        sessionAttached = false
        CaptureSessionState.detach()
    }

    private fun teardown() {
        Log.i(TAG, "teardown")
        stopped = true
        screenshotObserver?.let { runCatching { contentResolver.unregisterContentObserver(it) } }
        screenshotObserver = null
        hideOverlay()
        detachSession()
        instance = null
        _connected.value = false
    }

    /**
     * ライブラリから手動で表示した撮影ボタンだけを維持する。
     * イベント通知は廃止したため、通知に合わせた自動表示は行わない。
     */
    private suspend fun observeCaptureWindow() {
        Log.i(TAG, "observeCaptureWindow start")
        while (!stopped && currentCoroutineContext().isActive) {
            try {
                val session = container.authRepository.session.first()
                val currentUserId = session?.userId
                if (currentUserId == null) {
                    userId = null
                    hideOverlay()
                    delay(POLL_INTERVAL_MS)
                    continue
                }
                if (currentUserId != lastUserId) {
                    lastUserId = currentUserId
                    forcedEventId = null
                }
                val event = container.eventRepository
                    .observeEvent(container.timeProvider.todayJst())
                    .first()
                lastEventId = event.id
                if (event.id == forcedEventId) {
                    val currentSettings = settingsRepository.getSettings(currentUserId)
                    userId = currentUserId
                    settings = currentSettings
                    showOverlay(currentSettings)
                } else {
                    hideOverlay()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "capture window update failed", error)
                hideOverlay()
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun showOverlay(settings: UserSettings) {
        if (overlayView != null) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val density = resources.displayMetrics.density
        val sizePx = (settings.overlaySizeDp * density).roundToInt().coerceAtLeast(1)
        val view = OverlayCaptureView(this).apply {
            text = "●"
            textSize = 25f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            contentDescription = "ゲーム画面を撮影"
            alpha = settings.overlayOpacity
            elevation = 8 * density
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(53, 93, 77))
            }
            setOnClickListener { captureScreenshot() }
        }
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.overlayX
            y = settings.overlayY
        }
        attachDragAndTap(view, params)
        try {
            windowManager?.addView(view, params)
            overlayView = view
            overlayParams = params
            CaptureSessionState.setActive(true)
        } catch (error: Exception) {
            toast("撮影ボタンを表示できません: ${error.message}")
        }
    }

    private fun hideOverlay() {
        val view = overlayView ?: run {
            CaptureSessionState.setActive(false)
            return
        }
        overlayView = null
        overlayParams = null
        runCatching { windowManager?.removeView(view) }
        CaptureSessionState.setActive(false)
    }

    /** 撮影セッションを終了する。 */
    private fun endSession() {
        forcedEventId = null
        hideOverlay()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragAndTap(view: TextView, params: WindowManager.LayoutParams) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        val velocityTracker = VelocityTracker.obtain()
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        view.setOnTouchListener { _, event ->
            velocityTracker.addMovement(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    if (moved) {
                        // 画面外へスワイプできるよう、ドラッグ中は表示位置を制限しない。
                        params.x = startX + dx.roundToInt()
                        params.y = startY + dy.roundToInt()
                        windowManager?.updateViewLayout(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    velocityTracker.computeCurrentVelocity(1000)
                    if (moved &&
                        shouldDismissBySwipe(params, velocityTracker.xVelocity, velocityTracker.yVelocity)
                    ) {
                        toast("撮影ボタンを消しました")
                        endSession()
                    } else if (moved) {
                        snapIntoBounds(params)
                        windowManager?.updateViewLayout(view, params)
                        val id = userId
                        if (id != null) {
                            serviceScope.launch {
                                settingsRepository.setOverlayPosition(id, params.x, params.y)
                            }
                        }
                    } else {
                        view.performClick()
                    }
                    velocityTracker.clear()
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    velocityTracker.clear()
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 画面外へ運ぶか、画面中央から外側へ素早くスワイプした場合に撮影ボタンを閉じる。
     */
    private fun shouldDismissBySwipe(
        params: WindowManager.LayoutParams,
        velocityX: Float,
        velocityY: Float,
    ): Boolean {
        val metrics = resources.displayMetrics
        val centerX = params.x + params.width / 2f
        val centerY = params.y + params.height / 2f
        val offScreen = centerX < 0f || centerX > metrics.widthPixels ||
            centerY < 0f || centerY > metrics.heightPixels
        if (offScreen) return true

        val speed = hypot(velocityX, velocityY)
        if (speed < SWIPE_DISMISS_VELOCITY) return false
        val outwardX = (centerX - metrics.widthPixels / 2f) * velocityX
        val outwardY = (centerY - metrics.heightPixels / 2f) * velocityY
        return outwardX + outwardY > 0f
    }

    private fun snapIntoBounds(params: WindowManager.LayoutParams) {
        val metrics = resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - params.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - params.height).coerceAtLeast(0))
    }

    /**
     * ボタン自体がスクリーンショットに写り込まないよう、いったん非表示にしてから撮影する。
     */
    @SuppressLint("NewApi")
    private fun captureScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            toast("スクリーンショット撮影にはAndroid 11以降が必要です")
            return
        }
        if (!capturing.compareAndSet(false, true)) return
        val view = overlayView
        view?.visibility = View.INVISIBLE
        mainHandler.postDelayed({
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            restoreOverlay(view)
                            val bitmap = try {
                                screenshot.toSoftwareBitmap()
                            } catch (error: Exception) {
                                capturing.set(false)
                                toast(error.message ?: "スクリーンショットを読み取れませんでした")
                                return
                            }
                            saveCapture(bitmap)
                        }

                        override fun onFailure(errorCode: Int) {
                            restoreOverlay(view)
                            capturing.set(false)
                            toast(describeScreenshotFailure(errorCode))
                        }
                    },
                )
            } catch (error: Exception) {
                restoreOverlay(view)
                capturing.set(false)
                toast("スクリーンショットを取得できません: ${error.message}")
            }
        }, HIDE_BEFORE_CAPTURE_MS)
    }

    /**
     * クイック設定タイルから、投稿確認用のスクリーンショットを撮る。
     * 撮影後は「スクリーンショットを投稿しますか？」の通知を出す。
     */
    @SuppressLint("NewApi")
    private fun capturePromptScreenshot() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            toast("スクリーンショット撮影にはAndroid 11以降が必要です")
            return
        }
        if (!capturing.compareAndSet(false, true)) return
        val view = overlayView
        view?.visibility = View.INVISIBLE
        // クイック設定パネルが閉じて、ゲーム画面に戻るのを待ってから撮影する。
        mainHandler.postDelayed({
            try {
                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    mainExecutor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshot: ScreenshotResult) {
                            restoreOverlay(view)
                            val bitmap = try {
                                screenshot.toSoftwareBitmap()
                            } catch (error: Exception) {
                                capturing.set(false)
                                toast(error.message ?: "スクリーンショットを読み取れませんでした")
                                return
                            }
                            savePromptScreenshot(bitmap)
                        }

                        override fun onFailure(errorCode: Int) {
                            restoreOverlay(view)
                            capturing.set(false)
                            toast(describeScreenshotFailure(errorCode))
                        }
                    },
                )
            } catch (error: Exception) {
                restoreOverlay(view)
                capturing.set(false)
                toast("スクリーンショットを取得できません: ${error.message}")
            }
        }, TILE_CAPTURE_DELAY_MS)
    }

    /** 撮影した画像を一時保存し、「投稿しますか？」の通知を出す。 */
    private fun savePromptScreenshot(bitmap: Bitmap) {
        serviceScope.launch {
            try {
                cleanupPromptFiles()
                val path = container.imageFiles.saveBitmap(bitmap, PROMPT_FOLDER)
                runCatching {
                    DailyCaptureReminder.showScreenshotPrompt(
                        applicationContext,
                        Uri.fromFile(File(path)).toString(),
                    )
                }.onFailure { Log.w(TAG, "screenshot prompt failed", it) }
            } catch (error: Exception) {
                toast(error.message ?: "スクリーンショットを保存できませんでした")
            } finally {
                bitmap.recycle()
                capturing.set(false)
            }
        }
    }

    /** 応答されなかった古い一時ファイルを削除する。 */
    private fun cleanupPromptFiles() {
        runCatching {
            val directory = File(filesDir, PROMPT_FOLDER)
            val cutoff = System.currentTimeMillis() - PROMPT_FILE_TTL_MS
            directory.listFiles()?.forEach { file ->
                if (file.lastModified() < cutoff) file.delete()
            }
        }
    }

    private fun restoreOverlay(view: View?) {
        mainHandler.post { view?.visibility = View.VISIBLE }
    }

    private fun saveCapture(bitmap: Bitmap) {
        serviceScope.launch {
            var stored = false
            try {
                val capturedAt = container.timeProvider.now()
                val path = container.imageFiles.saveBitmap(bitmap)
                var registered = false
                try {
                    val id = userId ?: throw IllegalStateException("アカウントを確認できません")
                    val capture = captureRepository.registerCapture(id, path, capturedAt).getOrThrow()
                    registered = true
                    val draft = container.postRepository.drafts.first().firstOrNull {
                        it.userId == id && it.eventId == capture.eventId && it.status == DraftStatus.ACTIVE
                    }
                    val alreadyPosted = container.postRepository.posts.first().any {
                        it.userId == id && it.eventDateJst == capturedAt.atZone(JST).toLocalDate()
                    }
                    runCatching {
                        DailyCaptureReminder.showAutoPostScheduled(
                            this@ScreenshotAccessibilityService,
                            capture.id,
                            draft?.autoPublishAt,
                            alreadyPosted = alreadyPosted,
                        )
                    }
                } finally {
                    if (!registered) {
                        withContext(NonCancellable) {
                            runCatching { container.imageFiles.delete(path) }
                        }
                    }
                }
                overlayView?.text = "✓"
                stored = true
                // 1枚撮影したらセッションを終了し、アイコンを消す。
                mainHandler.postDelayed({ endSession() }, CAPTURE_DONE_HIDE_DELAY_MS)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                overlayView?.text = "!"
                toast(error.message ?: "撮影を保存できませんでした")
            } finally {
                bitmap.recycle()
                capturing.set(false)
                if (!stored) {
                    mainHandler.postDelayed({ overlayView?.text = "●" }, 600)
                }
            }
        }
    }

    private suspend fun stopCaptureAndAwait() {
        forcedEventId = null
        mainHandler.post { hideOverlay() }
        // 保存中の画像を登録前に消さないよう、実行中の撮影完了を待つ。
        val timeoutAt = System.currentTimeMillis() + STOP_WAIT_MS
        while (capturing.get() && System.currentTimeMillis() < timeoutAt) {
            delay(20)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun ScreenshotResult.toSoftwareBitmap(): Bitmap {
        val hardware = hardwareBuffer
        try {
            val wrapped = Bitmap.wrapHardwareBuffer(hardware, colorSpace)
                ?: throw IllegalStateException("画面の内容を読み取れませんでした")
            return try {
                wrapped.copy(Bitmap.Config.ARGB_8888, false)
                    ?: throw IllegalStateException("画像を変換できませんでした")
            } finally {
                wrapped.recycle()
            }
        } finally {
            hardware.close()
        }
    }

    private fun describeScreenshotFailure(errorCode: Int): String = when (errorCode) {
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT ->
            "連続で撮影しすぎです。少し待ってください"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY ->
            "画面を取得できませんでした"
        AccessibilityService.ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS ->
            "ユーザー補助の権限を確認してください"
        else -> "スクリーンショットを取得できませんでした ($errorCode)"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private class OverlayCaptureView(context: Context) : TextView(context) {
        override fun performClick(): Boolean = super.performClick()
    }

    companion object {
        private const val TAG = "RealMeScreenshot"
        private const val POLL_INTERVAL_MS = 1_000L
        private const val HIDE_BEFORE_CAPTURE_MS = 120L
        private const val CAPTURE_DONE_HIDE_DELAY_MS = 700L
        private const val SWIPE_DISMISS_VELOCITY = 1_200f
        private const val SCREENSHOT_FRESH_MS = 60_000L
        private const val SCREENSHOT_QUERY_WINDOW_SECONDS = 120L
        private const val MAX_TRACKED_SCREENSHOTS = 200
        /** 前面に来たらゲームの文脈を終了するパッケージ。 */
        private val CLEAR_CONTEXT_PACKAGES = setOf(
            "com.android.settings",
            "com.google.android.apps.nexuslauncher",
            "com.android.launcher",
            "com.android.launcher3",
            "com.sec.android.app.launcher",
            "com.miui.home",
        )
        /** スクリーンショットのプレビューなど、一瞬だけ前面に出るパッケージ。文脈は消さない。 */
        private val TRANSIENT_OVERLAY_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.permissioncontroller",
            "com.google.android.gms",
        )
        private const val STOP_WAIT_MS = 8_000L
        private const val TILE_CAPTURE_DELAY_MS = 900L
        private const val PROMPT_FOLDER = "screenshotprompt"
        private const val PROMPT_FILE_TTL_MS = 30 * 60 * 1000L

        @Volatile
        private var instance: ScreenshotAccessibilityService? = null

        private val _connected = MutableStateFlow(false)
        /** ユーザー補助サービスが接続され、スクリーンショット撮影が可能かどうか。 */
        val connected: StateFlow<Boolean> = _connected.asStateFlow()

        val isEnabled: Boolean get() = instance != null

        /** クイック設定タイルから、投稿確認付きのスクリーンショットを撮る。無効ならfalse。 */
        fun captureForPrompt(): Boolean {
            val service = instance ?: return false
            // ゲーム中だけ投稿確認を出す。ホーム画面などでの誤操作は何もしない。
            if (!service.isGameContext()) {
                return true
            }
            service.capturePromptScreenshot()
            return true
        }

        /** 手動で撮影ボタンを表示する。 */
        fun requestOverlay(context: Context) {
            instance?.let { service ->
                service.forcedEventId = service.lastEventId
                service.settings?.let(service::showOverlay)
            }
        }

        fun hideOverlay(context: Context) {
            instance?.let { service ->
                service.forcedEventId = null
                service.hideOverlay()
            }
        }

        fun openAccessibilitySettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }
}



