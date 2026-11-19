package com.realme.feature.capture

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realme.core.model.Capture
import com.realme.core.model.DailyEvent
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.DraftStatus
import com.realme.core.model.PostDraft
import com.realme.core.util.TimeProvider
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.CaptureRepository
import com.realme.data.repository.EventRepository
import com.realme.data.repository.PostRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CaptureUiState(
    val captures: List<Capture> = emptyList(),
    val activeDraft: PostDraft? = null,
    val event: DailyEvent,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class CaptureViewModel(
    private val captureRepository: CaptureRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val currentDate = MutableStateFlow(timeProvider.todayJst())
    private val message = MutableStateFlow<String?>(null)
    private val currentEvent = currentDate.flatMapLatest(eventRepository::observeEvent)

    init {
        viewModelScope.launch { eventRepository.ensureEvent(currentDate.value) }
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                val date = timeProvider.todayJst()
                if (date != currentDate.value) {
                    currentDate.value = date
                    eventRepository.ensureEvent(date)
                }
            }
        }
    }

    val uiState = combine(
        authRepository.session,
        captureRepository.captures,
        postRepository.drafts,
        currentEvent,
        message,
    ) { session, captures, drafts, event, message ->
        CaptureUiState(
            captures = captures.filter { it.userId == session?.userId },
            activeDraft = drafts.firstOrNull {
                it.userId == session?.userId && it.eventId == event.id && it.status == DraftStatus.ACTIVE
            },
            event = event,
            message = message,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        CaptureUiState(event = DailyEventFactory.forDate(currentDate.value)),
    )

    fun delete(captureId: String) {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            captureRepository.deleteCapture(userId, captureId)
                .onFailure { message.value = it.message }
        }
    }

    fun publishNow(captureId: String) {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            val capture = captureRepository.captures.first()
                .firstOrNull { it.id == captureId && it.userId == userId } ?: return@launch
            postRepository.createGamePost(
                userId = userId,
                imagePath = capture.filePath,
                capturedAt = capture.capturedAt,
                caption = "",
                now = timeProvider.now(),
            )
                .onSuccess {
                    // 投稿画像は posts へコピー済みなので、撮影候補からは削除する。
                    captureRepository.deleteCapture(userId, captureId)
                    message.value = "画像を投稿しました"
                }
                .onFailure { message.value = it.message }
        }
    }

    fun cancelDraft() {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            val draft = uiState.value.activeDraft ?: return@launch
            postRepository.cancelDraft(userId, draft.id)
                .onSuccess { message.value = "自動投稿をキャンセルしました" }
                .onFailure { message.value = it.message }
        }
    }

    fun clearMessage() {
        message.value = null
    }
}
