package com.realme.feature.editor

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realme.core.util.TimeProvider
import com.realme.data.local.ImageFileStore
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

data class ImageEditorUiState(
    val isLoading: Boolean = true,
    val sourcePath: String? = null,
    val basePreview: Bitmap? = null,
    val config: EditorConfig = EditorConfig(),
    val canUndo: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
    val savedPath: String? = null,
    val autoPublishAt: Instant? = null,
)

class ImageEditorViewModel(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val imageFiles: ImageFileStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val engine = ImageEditorEngine(imageFiles)
    private val mutex = Mutex()
    private val _uiState = MutableStateFlow(ImageEditorUiState())
    val uiState: StateFlow<ImageEditorUiState> = _uiState
    private val history = ArrayDeque<EditorConfig>()
    private val nextId = AtomicLong(1)
    private var sourceBitmap: Bitmap? = null
    private var captureId: String? = null
    private var draftId: String? = null
    private var editingStarted = false

    fun load(sourcePath: String, captureId: String?) {
        this.captureId = captureId
        draftId = null
        editingStarted = false
        history.clear()
        sourceBitmap?.recycle()
        sourceBitmap = null
        _uiState.value = ImageEditorUiState(sourcePath = sourcePath)
        viewModelScope.launch {
            try {
                val loaded = engine.load(sourcePath)
                sourceBitmap = loaded
                val preview = engine.renderBase(loaded, EditorConfig())
                _uiState.value = ImageEditorUiState(
                    isLoading = false,
                    sourcePath = sourcePath,
                    basePreview = preview,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = ImageEditorUiState(
                    isLoading = false,
                    sourcePath = sourcePath,
                    error = error.message ?: "画像を読み込めませんでした",
                )
            }
        }
    }

    fun setFilter(filter: EditorFilter) = mutate(
        baseChanges = true,
        transform = { it.copy(filter = filter) },
    )

    fun setCrop(crop: CropRatio) = mutate(
        baseChanges = true,
        transform = { it.copy(cropRatio = crop) },
    )

    fun setFreeCrop(rect: NormalizedRect) = mutate(
        baseChanges = true,
        transform = { it.copy(cropRatio = CropRatio.FREE, freeCrop = normalizeRect(rect)) },
    )

    fun setEffect(effect: EditorEffect) = mutate(
        baseChanges = true,
        transform = { it.copy(effect = effect) },
    )

    fun setEffectRect(rect: NormalizedRect) = mutate(
        baseChanges = true,
        transform = { it.copy(effectRect = normalizeRect(rect)) },
    )

    fun addText(text: String, x: Float, y: Float) {
        if (text.isBlank()) return
        mutate(baseChanges = false) {
            it.copy(
                texts = it.texts + EditorText(
                    id = nextId.getAndIncrement(),
                    text = text.take(80),
                    xFraction = x.coerceIn(0.05f, 0.95f),
                    yFraction = y.coerceIn(0.05f, 0.95f),
                    colorIndex = 0,
                ),
            )
        }
    }

    fun cycleTextColor() = mutate(baseChanges = false) {
        val target = it.texts.lastOrNull() ?: return@mutate it
        it.copy(
            texts = it.texts.map { text ->
                if (text.id == target.id) {
                    text.copy(colorIndex = (text.colorIndex + 1) % ImageEditorEngine.TEXT_COLORS.size)
                } else {
                    text
                }
            },
        )
    }

    fun moveText(id: Long, x: Float, y: Float) {
        val current = _uiState.value
        if (current.isLoading || current.isBusy) return
        val target = current.config.texts.firstOrNull { it.id == id } ?: return
        if (target.xFraction == x.coerceIn(0.05f, 0.95f) && target.yFraction == y.coerceIn(0.05f, 0.95f)) return
        recordMoveHistory()
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(
                texts = _uiState.value.config.texts.map {
                    if (it.id == id) it.copy(
                        xFraction = x.coerceIn(0.05f, 0.95f),
                        yFraction = y.coerceIn(0.05f, 0.95f),
                    ) else it
                },
            ),
        )
    }

    fun addStamp(symbol: String, x: Float, y: Float) = mutate(baseChanges = false) {
        it.copy(
            stamps = it.stamps + EditorStamp(
                id = nextId.getAndIncrement(),
                symbol = symbol,
                xFraction = x.coerceIn(0.05f, 0.95f),
                yFraction = y.coerceIn(0.05f, 0.95f),
            ),
        )
    }

    fun scaleLastStamp(factor: Float) = mutate(baseChanges = false) {
        val last = it.stamps.lastOrNull() ?: return@mutate it
        it.copy(
            stamps = it.stamps.map { stamp ->
                if (stamp.id == last.id) stamp.copy(scale = (stamp.scale * factor).coerceIn(0.3f, 3f)) else stamp
            },
        )
    }

    fun rotateLastStamp(degrees: Float) = mutate(baseChanges = false) {
        val last = it.stamps.lastOrNull() ?: return@mutate it
        it.copy(
            stamps = it.stamps.map { stamp ->
                if (stamp.id == last.id) stamp.copy(rotation = (stamp.rotation + degrees) % 360f) else stamp
            },
        )
    }

    fun moveStamp(id: Long, x: Float, y: Float) {
        val current = _uiState.value
        if (current.isLoading || current.isBusy) return
        val target = current.config.stamps.firstOrNull { it.id == id } ?: return
        if (target.xFraction == x.coerceIn(0.05f, 0.95f) && target.yFraction == y.coerceIn(0.05f, 0.95f)) return
        recordMoveHistory()
        _uiState.value = _uiState.value.copy(
            config = _uiState.value.config.copy(
                stamps = _uiState.value.config.stamps.map {
                    if (it.id == id) it.copy(
                        xFraction = x.coerceIn(0.05f, 0.95f),
                        yFraction = y.coerceIn(0.05f, 0.95f),
                    ) else it
                },
            ),
        )
    }

    private fun recordMoveHistory() {
        history.addLast(_uiState.value.config)
        while (history.size > MAX_HISTORY) history.removeFirst()
        _uiState.value = _uiState.value.copy(canUndo = true)
    }

    fun undo() {
        val previous = history.removeLastOrNull() ?: return
        val needsBase = previous.filter != _uiState.value.config.filter ||
            previous.cropRatio != _uiState.value.config.cropRatio ||
            previous.freeCrop != _uiState.value.config.freeCrop ||
            previous.effect != _uiState.value.config.effect ||
            previous.effectRect != _uiState.value.config.effectRect
        _uiState.value = _uiState.value.copy(
            config = previous,
            canUndo = history.isNotEmpty(),
            isBusy = needsBase,
        )
        if (needsBase) {
            viewModelScope.launch {
                mutex.withLock {
                    try {
                        renderBase(previous)
                    } finally {
                        _uiState.value = _uiState.value.copy(isBusy = false)
                    }
                }
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            mutex.withLock {
                val state = _uiState.value
                val source = sourceBitmap ?: return@withLock
                if (state.isBusy) return@withLock
                _uiState.value = state.copy(isBusy = true, error = null)
                try {
                    val finalBitmap = engine.renderFinal(source, state.config)
                    val path = try {
                        engine.save(finalBitmap)
                    } finally {
                        finalBitmap.recycle()
                    }
                    val id = draftId
                    if (id != null) {
                        val userId = authRepository.session.first()?.userId
                            ?: throw IllegalStateException("アカウントを確認できません")
                        postRepository.markDraftReady(userId, id, path, captureId).getOrThrow()
                    }
                    _uiState.value = _uiState.value.copy(isBusy = false, savedPath = path)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = error.message ?: "編集画像を保存できませんでした",
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun mutate(baseChanges: Boolean, transform: (EditorConfig) -> EditorConfig) {
        viewModelScope.launch {
            mutex.withLock {
                val state = _uiState.value
                if (state.isLoading || state.isBusy || sourceBitmap == null) return@withLock
                _uiState.value = state.copy(isBusy = true, error = null)
                try {
                    val previous = state.config
                    val config = transform(previous)
                    if (config == previous) {
                        _uiState.value = _uiState.value.copy(isBusy = false)
                        return@withLock
                    }
                    if (!editingStarted) {
                        val userId = authRepository.session.first()?.userId
                            ?: throw IllegalStateException("アカウントを確認できません")
                        val capture = captureId
                        if (capture != null) {
                            val draft = postRepository.beginDraftEditing(
                                userId,
                                capture,
                                timeProvider.now(),
                            ).getOrThrow()
                            draftId = draft?.id
                            _uiState.value = _uiState.value.copy(autoPublishAt = draft?.autoPublishAt)
                        }
                        editingStarted = true
                    }
                    history.addLast(previous)
                    while (history.size > MAX_HISTORY) history.removeFirst()
                    _uiState.value = _uiState.value.copy(
                        config = config,
                        canUndo = true,
                    )
                    if (baseChanges) renderBase(config)
                    _uiState.value = _uiState.value.copy(isBusy = false)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = error.message ?: "編集を開始できませんでした",
                    )
                }
            }
        }
    }

    private suspend fun renderBase(config: EditorConfig) {
        val source = sourceBitmap ?: return
        val preview = engine.renderBase(source, config)
        _uiState.value = _uiState.value.copy(basePreview = preview)
    }

    private fun normalizeRect(rect: NormalizedRect): NormalizedRect {
        val left = rect.left.coerceIn(0f, 0.9f)
        val top = rect.top.coerceIn(0f, 0.9f)
        val right = rect.right.coerceIn(left + 0.05f, 1f)
        val bottom = rect.bottom.coerceIn(top + 0.05f, 1f)
        return NormalizedRect(left, top, right, bottom)
    }

    private companion object {
        const val MAX_HISTORY = 20
    }
}
