package com.realme.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realme.core.model.StreakCalculator
import com.realme.core.model.StreakStats
import com.realme.core.model.UserProfile
import com.realme.core.util.TimeProvider
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.PostRepository
import com.realme.data.repository.ProfileRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val profile: UserProfile? = null,
    val streak: StreakStats = StreakStats(0, 0, 0),
    val isSaving: Boolean = false,
    val message: String? = null,
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val profileRepository: ProfileRepository,
    private val postRepository: PostRepository,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val action = MutableStateFlow(ActionState())
    private val currentDate = MutableStateFlow(timeProvider.todayJst())

    init {
        viewModelScope.launch {
            while (isActive) {
                delay(30_000)
                currentDate.value = timeProvider.todayJst()
            }
        }
    }

    val uiState = combine(
        authRepository.session,
        profileRepository.profiles,
        postRepository.posts,
        currentDate,
        action,
    ) { session, profiles, posts, date, action ->
        val profile = profiles.firstOrNull { it.id == session?.userId }
        ProfileUiState(
            profile = profile,
            streak = profile?.let {
                StreakCalculator.calculate(posts.filter { post -> post.userId == it.id }, date)
            } ?: StreakStats(0, 0, 0),
            isSaving = action.saving,
            message = action.message,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, ProfileUiState())

    fun save(displayName: String, bio: String) {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            action.value = ActionState(saving = true)
            profileRepository.updateProfile(userId, displayName, bio)
                .onSuccess { action.value = ActionState(message = "プロフィールを保存しました") }
                .onFailure { action.value = ActionState(message = it.message) }
        }
    }

    fun signOut() {
        viewModelScope.launch { authRepository.signOut() }
    }

    fun clearMessage() {
        action.value = action.value.copy(message = null)
    }

    private data class ActionState(val saving: Boolean = false, val message: String? = null)
}
