package com.realme

import android.content.Context
import com.realme.core.media.AndroidCaptureSessionController
import com.realme.core.util.SystemTimeProvider
import com.realme.core.util.TimeProvider
import com.realme.data.local.ImageFileStore
import com.realme.data.local.LocalAccountDeletionRepository
import com.realme.data.local.LocalAppStore
import com.realme.data.local.LocalAuthRepository
import com.realme.data.local.LocalCaptureRepository
import com.realme.data.local.LocalEventRepository
import com.realme.data.local.LocalPostRepository
import com.realme.data.local.LocalProfileRepository
import com.realme.data.local.LocalSettingsRepository
import com.realme.data.local.WorkManagerAutoPostScheduler
import com.realme.data.local.DeletionCleanupWorker

class AppContainer(context: Context) {
    val timeProvider: TimeProvider = SystemTimeProvider
    val imageFiles = ImageFileStore(context)
    private val store = LocalAppStore(context)
    val autoPostScheduler = WorkManagerAutoPostScheduler(context)

    val authRepository = LocalAuthRepository(store, timeProvider)
    val profileRepository = LocalProfileRepository(store)
    val eventRepository = LocalEventRepository(store)
    val settingsRepository = LocalSettingsRepository(store, imageFiles)
    val postRepository = LocalPostRepository(store, imageFiles, autoPostScheduler)
    val captureRepository = LocalCaptureRepository(store, imageFiles, autoPostScheduler)
    val accountDeletionRepository = LocalAccountDeletionRepository(
        store,
        imageFiles,
        authRepository,
        autoPostScheduler,
        captureSessionController = AndroidCaptureSessionController(context.applicationContext),
        enqueueCleanup = { DeletionCleanupWorker.enqueue(context.applicationContext) },
    )
}



