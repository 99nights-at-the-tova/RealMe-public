package com.realme.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.realme.core.model.Post
import com.realme.core.model.UserSettings
import com.realme.core.util.TimeProvider
import com.realme.data.local.ImageFileStore
import com.realme.data.repository.AccountDeletionRepository
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.PostRepository
import com.realme.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: UserSettings? = null,
    val hasPin: Boolean = false,
    val isBusy: Boolean = false,
    val message: String? = null,
    val publishedPost: Post? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val accountDeletionRepository: AccountDeletionRepository,
    private val images: ImageFileStore,
    private val timeProvider: TimeProvider,
) : ViewModel() {
    private val action = MutableStateFlow(ActionState())
    private val settings = authRepository.session.flatMapLatest { session ->
        if (session == null) flowOf(null) else settingsRepository.observeSettings(session.userId)
    }
    private val hasPin = authRepository.session.flatMapLatest { session ->
        if (session == null) {
            flowOf(false)
        } else {
            authRepository.pinProtectedAccountIds.map { session.userId in it }
        }
    }
    val uiState = combine(settings, hasPin, action) { settings, hasPin, action ->
        SettingsUiState(
            settings = settings,
            hasPin = hasPin,
            isBusy = action.busy,
            message = action.message,
            publishedPost = action.post,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    fun setAutoPostMinutes(minutes: Int) {
        launchWithUser { userId ->
            settingsRepository.setAutoPostMinutes(userId, minutes)
                .onSuccess { action.value = ActionState(message = "自動投稿時間を保存しました。進行中の下書きの時刻は変わりません。") }
                .onFailure { action.value = ActionState(message = it.message) }
        }
    }

    fun setOverlayAppearance(sizeDp: Int, opacity: Float) {
        launchWithUser { userId ->
            settingsRepository.setOverlayAppearance(userId, sizeDp, opacity)
                .onFailure { action.value = ActionState(message = it.message) }
        }
    }

    fun selectPrivacyImage(uri: Uri) {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            action.value = ActionState(busy = true)
            try {
                val path = images.importImage(uri, "privacy")
                settingsRepository.setPrivacyImage(userId, path)
                    .onSuccess { action.value = ActionState(message = "プライバシー画像を保存しました") }
                    .onFailure { action.value = ActionState(message = it.message) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                action.value = ActionState(message = error.message ?: "画像を保存できませんでした")
            }
        }
    }

    fun publishPrivacy(caption: String) {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            val imagePath = uiState.value.settings?.privacyImagePath
            if (imagePath == null) {
                action.value = ActionState(message = "先にプライバシー画像を選択してください")
                return@launch
            }
            action.value = ActionState(busy = true)
            postRepository.createPrivacyPost(userId, imagePath, caption, timeProvider.now())
                .onSuccess { action.value = ActionState(post = it, message = "PRIVACYとして投稿しました") }
                .onFailure { action.value = ActionState(message = it.message) }
        }
    }

    fun saveAccountPin(currentPin: String?, newPin: String) {
        launchWithUser { userId ->
            action.value = ActionState(busy = true)
            authRepository.setAccountPin(userId, currentPin, newPin)
                .onSuccess { action.value = ActionState(message = "端末内PINを保存しました") }
                .onFailure { action.value = ActionState(message = it.message) }
        }
    }

    fun removeAccountPin(currentPin: String) {
        launchWithUser { userId ->
            action.value = ActionState(busy = true)
            authRepository.setAccountPin(userId, currentPin, null)
                .onSuccess { action.value = ActionState(message = "端末内PINを解除しました") }
                .onFailure { action.value = ActionState(message = it.message) }
        }
    }

    fun deleteAccount(pin: String?) {
        launchWithUser { userId ->
            action.value = ActionState(busy = true)
            accountDeletionRepository.deleteAccount(userId, pin)
                .onSuccess { action.value = ActionState(message = "アカウントを削除しました") }
                .onFailure { action.value = ActionState(message = it.message) }
        }
    }

    fun consumePublished() {
        action.value = action.value.copy(post = null)
    }

    fun clearMessage() {
        action.value = action.value.copy(message = null)
    }

    private fun launchWithUser(block: suspend (String) -> Unit) {
        viewModelScope.launch {
            val userId = authRepository.session.first()?.userId ?: return@launch
            block(userId)
        }
    }

    private data class ActionState(
        val busy: Boolean = false,
        val message: String? = null,
        val post: Post? = null,
    )
}
