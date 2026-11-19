package com.realme.data.local

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.realme.core.model.PostDraft
import com.realme.data.repository.AutoPostScheduler
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WorkManagerAutoPostScheduler(private val context: Context) : AutoPostScheduler {
    override fun schedule(draft: PostDraft) {
        val delay = Duration.between(Instant.now(), draft.autoPublishAt).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<AutoPostWorker>()
            .setInputData(Data.Builder().putString(AutoPostWorker.KEY_DRAFT_ID, draft.id).build())
            .setInitialDelay(Duration.ofMillis(delay))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(draft.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    override fun cancel(draftId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(draftId))
    }

    override suspend fun cancelAndWait(draftId: String) = withContext(Dispatchers.IO) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(draftId)).result.get()
        Unit
    }

    companion object {
        fun workName(draftId: String) = "auto-post-$draftId"
    }
}
