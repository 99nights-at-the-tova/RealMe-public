package com.realme.feature.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realme.core.model.AuthSession
import com.realme.core.model.DailyEvent
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.JST
import com.realme.core.model.Post
import com.realme.core.model.PostStatus
import com.realme.core.model.StreakCalculator
import com.realme.core.model.StreakStats
import com.realme.core.model.UserProfile
import com.realme.core.util.TimeProvider
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.EventRepository
import com.realme.data.repository.PostRepository
import com.realme.data.repository.ProfileRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant

data class TimelineEntry(
    val posts: List<Post>,
    val profile: UserProfile,
    val streak: StreakStats,
) {
    val post: Post get() = posts.last()
}

data class TimelineUiState(
    val isLoading: Boolean = true,
    val entries: List<TimelineEntry> = emptyList(),
    val currentUserId: String? = null,
    val event: DailyEvent,
    val now: Instant,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(
    private val postsRepository: PostRepository,
    private val profileRepository: ProfileRepository,
    private val authRepository: AuthRepository,
    private val eventRepository: EventRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val currentDate = MutableStateFlow(timeProvider.todayJst())
    private val clock = MutableStateFlow(timeProvider.now())
    private val message = MutableStateFlow<String?>(null)
    private val currentEvent = currentDate.flatMapLatest(eventRepository::observeEvent)

    init {
        viewModelScope.launch { eventRepository.ensureEvent(currentDate.value) }
    }

    val uiState = combine(
        postsRepository.posts,
        profileRepository.profiles,
        authRepository.session,
        currentEvent,
        combine(clock, message) { now, text -> now to text },
    ) { posts, profiles, session: AuthSession?, event, state ->
        val entries = posts
            .groupBy { it.userId to it.eventDateJst }
            .values
            .map { group -> group.sortedBy { it.postedAt } }
            .sortedByDescending { group -> group.maxOf { it.postedAt } }
            .mapNotNull { group ->
                val latest = group.last()
                val profile = profiles.firstOrNull { it.id == latest.userId } ?: return@mapNotNull null
                TimelineEntry(
                    posts = group,
                    profile = profile,
                    streak = StreakCalculator.calculate(
                        posts.filter { it.userId == latest.userId },
                        event.eventDateJst,
                    ),
                )
            }
        TimelineUiState(
            isLoading = false,
            entries = entries,
            currentUserId = session?.userId,
            event = event,
            now = state.first,
            message = state.second,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        TimelineUiState(
            event = DailyEventFactory.forDate(currentDate.value),
            now = timeProvider.now(),
        ),
    )

    fun refreshClock() {
        updateDateAndClock(timeProvider.now())
    }

    fun startDemo(status: PostStatus) {
        val now = timeProvider.now()
        val date = updateDateAndClock(now)
        viewModelScope.launch {
            eventRepository.startDemoEvent(date, status, now)
                .onFailure { message.value = it.message }
        }
    }

    private fun updateDateAndClock(now: Instant): java.time.LocalDate {
        clock.value = now
        val date = now.atZone(JST).toLocalDate()
        if (date != currentDate.value) {
            currentDate.value = date
            message.value = "日付が変わりました。今日のイベントを読み込みました。"
            viewModelScope.launch { eventRepository.ensureEvent(date) }
        }
        return date
    }

    fun delete(postId: String) {
        viewModelScope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            postsRepository.deletePost(userId, postId)
                .onSuccess { message.value = "投稿を削除しました" }
                .onFailure { message.value = it.message }
        }
    }

    fun replaceWithPrivacy(postId: String) {
        viewModelScope.launch {
            val userId = uiState.value.currentUserId ?: return@launch
            postsRepository.replaceWithPrivacy(userId, postId)
                .onSuccess { message.value = "プライバシー画像へ差し替えました。保存済みのコピーは回収できません。" }
                .onFailure { message.value = it.message }
            }
    }

    fun clearMessage() {
        message.value = null
    }
}

