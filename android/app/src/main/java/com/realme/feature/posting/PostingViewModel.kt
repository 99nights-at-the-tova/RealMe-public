package com.realme.feature.posting

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realme.core.model.DraftStatus
import com.realme.core.model.Post
import com.realme.core.util.TimeProvider
import com.realme.data.local.ImageFileStore
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.CaptureRepository
import com.realme.data.repository.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

data class PostingUiState(
    val selectedImagePath: String? = null,
    val captureId: String? = null,
    val capturedAt: Instant? = null,
    val caption: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val publishedPost: Post? = null,
)

class PostingViewModel(
    private val captureRepository: CaptureRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val images: ImageFileStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PostingUiState())
    val uiState: StateFlow<PostingUiState> = _uiState

    fun prepareNew() {
        _uiState.value = PostingUiState()
    }

    fun loadCapture(captureId: String) {
        _uiState.value = PostingUiState()
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            val capture = captureRepository.captures.first().firstOrNull {
                it.id == captureId && it.userId == userId
            }
            _uiState.value = if (capture == null) {
                PostingUiState(error = "撮影画像が見つかりません")
            } else {
                PostingUiState(
                    selectedImagePath = capture.filePath,
                    captureId = capture.id,
                    capturedAt = capture.capturedAt,
                    error = null,
                )
            }
        }
    }

    fun selectImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, error = null, publishedPost = null)
            try {
                val path = images.importImage(uri)
                _uiState.value = _uiState.value.copy(
                    selectedImagePath = path,
                    captureId = null,
                    capturedAt = timeProvider.now(),
                    isBusy = false,
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    error = error.message ?: "画像を読み込めませんでした",
                )
            }
        }
    }

    fun applyEditedImage(path: String) {
        _uiState.value = _uiState.value.copy(
            selectedImagePath = path,
            isBusy = false,
            error = null,
        )
    }

    fun updateCaption(value: String) {
        _uiState.value = _uiState.value.copy(caption = value.take(140))
    }

    fun publish() {
        val state = _uiState.value
        if (state.isBusy) return
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            val imagePath = state.selectedImagePath
            val capturedAt = state.capturedAt
            if (imagePath == null || capturedAt == null) {
                _uiState.value = _uiState.value.copy(error = "投稿する画像を選択してください")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isBusy = true, error = null)
            postRepository.createGamePost(userId, imagePath, capturedAt, state.caption, timeProvider.now())
                .onSuccess { post ->
                    // 投稿画像は posts へコピー済みなので、撮影候補からは削除する。
                    state.captureId?.let { captureRepository.deleteCapture(userId, it) }
                    _uiState.value = _uiState.value.copy(isBusy = false, publishedPost = post)
                }
                .onFailure { error ->
                    _uiState.value = _uiState.value.copy(
                        isBusy = false,
                        error = error.message ?: "投稿できませんでした",
                    )
                }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /** 編集画面を閉じるとき、進行中の自動投稿だけを取り消して撮影候補は残す。 */
    fun discardAutoPost() {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            val captureId = _uiState.value.captureId ?: return@launch
            val capture = captureRepository.captures.first().firstOrNull {
                it.id == captureId && it.userId == userId
            } ?: return@launch
            val draft = postRepository.drafts.first().firstOrNull {
                it.userId == userId && it.eventId == capture.eventId && it.status == DraftStatus.ACTIVE
            } ?: return@launch
            postRepository.cancelDraft(userId, draft.id)
        }
    }
}
