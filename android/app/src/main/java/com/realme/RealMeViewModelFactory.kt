package com.realme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.realme.feature.auth.AuthViewModel
import com.realme.feature.capture.CaptureViewModel
import com.realme.feature.editor.ImageEditorViewModel
import com.realme.feature.posting.PostingViewModel
import com.realme.feature.profile.ProfileViewModel
import com.realme.feature.settings.SettingsViewModel
import com.realme.feature.timeline.TimelineViewModel

class RealMeViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        AuthViewModel::class.java -> AuthViewModel(container.authRepository)
        TimelineViewModel::class.java -> TimelineViewModel(
            container.postRepository,
            container.profileRepository,
            container.authRepository,
            container.eventRepository,
            container.timeProvider,
        )
        CaptureViewModel::class.java -> CaptureViewModel(
            container.captureRepository,
            container.postRepository,
            container.authRepository,
            container.eventRepository,
            container.timeProvider,
        )
        ProfileViewModel::class.java -> ProfileViewModel(
            container.authRepository,
            container.profileRepository,
            container.postRepository,
            container.timeProvider,
        )
        PostingViewModel::class.java -> PostingViewModel(
            container.captureRepository,
            container.postRepository,
            container.authRepository,
            container.imageFiles,
            container.timeProvider,
        )
        ImageEditorViewModel::class.java -> ImageEditorViewModel(
            container.postRepository,
            container.authRepository,
            container.imageFiles,
            container.timeProvider,
        )
        SettingsViewModel::class.java -> SettingsViewModel(
            container.settingsRepository,
            container.postRepository,
            container.authRepository,
            container.accountDeletionRepository,
            container.imageFiles,
            container.timeProvider,
        )
        else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
    } as T
}

