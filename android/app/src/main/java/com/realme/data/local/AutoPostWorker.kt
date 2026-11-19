package com.realme.data.local

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.realme.RealMeApplication
import com.realme.core.model.DraftStatus

class AutoPostWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val draftId = inputData.getString(KEY_DRAFT_ID) ?: return Result.failure()
        val container = (applicationContext as RealMeApplication).container
        val draft = container.postRepository.getDraft(draftId) ?: return Result.success()
        if (draft.status != DraftStatus.ACTIVE) return Result.success()
        val now = container.timeProvider.now()
        if (now.isBefore(draft.autoPublishAt)) {
            container.autoPostScheduler.schedule(draft)
            return Result.success()
        }
        container.postRepository.publishDraft(draftId, now)
        return Result.success()
    }

    companion object {
        const val KEY_DRAFT_ID = "draft_id"
    }
}
