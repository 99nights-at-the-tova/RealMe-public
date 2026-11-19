package com.realme.data.local

import com.realme.core.model.AutoPostCalculator
import com.realme.core.model.Capture
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.DraftStatus
import com.realme.core.model.EditState
import com.realme.core.model.JST
import com.realme.core.model.PostDraft
import com.realme.core.model.UserSettings
import com.realme.data.repository.AutoPostScheduler
import com.realme.data.repository.CaptureRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class LocalCaptureRepository(
    private val store: LocalAppStore,
    private val images: ImageFileStore,
    private val scheduler: AutoPostScheduler,
) : CaptureRepository {
    override val captures: Flow<List<Capture>> = store.snapshots.map { snapshot ->
        snapshot.captures.sortedByDescending { it.capturedAt }
    }

    /** Remove expired capture candidates after the next reminder, retaining active drafts and shared files. */
    suspend fun cleanupExpiredCaptures(userId: String, currentDate: LocalDate) {
        val expiredDraftIds = mutableListOf<String>()
        store.update { snapshot ->
            if (snapshot.session?.userId != userId) return@update snapshot
            val cutoff = currentDate.atStartOfDay(JST).toInstant()
            val drafts = snapshot.drafts.map { draft ->
                val event = snapshot.events.firstOrNull { it.id == draft.eventId }
                if (draft.userId == userId && draft.status == DraftStatus.ACTIVE && event != null && !event.deadlineAt.isAfter(cutoff)) {
                    expiredDraftIds += draft.id
                    draft.copy(status = DraftStatus.FAILED)
                } else draft
            }
            val activeEvents = drafts.filter { it.userId == userId && it.status == DraftStatus.ACTIVE }
                .map { it.eventId }.toSet()
            val expired = snapshot.captures.filter {
                it.userId == userId && it.capturedAt.atZone(JST).toLocalDate() < currentDate && it.eventId !in activeEvents
            }
            val expiredIds = expired.map { it.id }.toSet()
            val remaining = snapshot.captures.filterNot { it.id in expiredIds }
            val referencedPaths = remaining.map { it.filePath }.toSet() +
                snapshot.posts.mapNotNull { it.imagePath } +
                snapshot.drafts.mapNotNull { it.editedImagePath } +
                snapshot.settings.values.mapNotNull { it.privacyImagePath }
            val deletable = expired.map { it.filePath }.filterNot { it in referencedPaths }
            snapshot.copy(
                captures = remaining,
                drafts = drafts,
                expiredCaptureCleanupPaths = snapshot.expiredCaptureCleanupPaths + deletable,
            )
        }
        expiredDraftIds.forEach(scheduler::cancel)
        for (path in store.current().expiredCaptureCleanupPaths) {
            val latest = store.current()
            val referenced = latest.captures.any { it.filePath == path } || latest.posts.any { it.imagePath == path } ||
                latest.drafts.any { it.editedImagePath == path } || latest.settings.values.any { it.privacyImagePath == path }
            if (!referenced) images.delete(path)
            store.update { it.copy(expiredCaptureCleanupPaths = it.expiredCaptureCleanupPaths - path) }
        }
    }

    override suspend fun registerCapture(
        userId: String,
        filePath: String,
        capturedAt: Instant,
    ): Result<Capture> = repositoryResult {
        require(images.exists(filePath)) { "撮影画像を保存できませんでした" }
        val date = capturedAt.atZone(JST).toLocalDate()
        val captureId = UUID.randomUUID().toString()
        var newDraft: PostDraft? = null
        var discardedEditedImage: String? = null
        var capture = Capture(
            id = captureId,
            userId = userId,
            eventId = "local-$date",
            filePath = filePath,
            capturedAt = capturedAt,
            eligible = false,
        )
        store.update { snapshot ->
            require(snapshot.profiles.any { it.id == userId && !it.isDemo }) { "ローカルアカウントが見つかりません" }
            val event = snapshot.events.firstOrNull { it.eventDateJst == date }
                ?: DailyEventFactory.forDate(date)
            capture = capture.copy(
                eventId = event.id,
                // 通知前の画像も投稿できるため、撮影画像は常に投稿対象にする。
                eligible = true,
            )
            // 進行中の自動投稿だけをブロックする。投稿を削除した PUBLISHED 下書きは
            // hasPost=false なら新しい下書きを作り直せるようにする。
            val hasLiveDraft = snapshot.drafts.any {
                it.userId == userId && it.eventId == event.id && it.status == DraftStatus.ACTIVE
            }
            val drafts = snapshot.drafts.toMutableList()
            val activeIndex = drafts.indexOfFirst {
                it.userId == userId && it.eventId == event.id && it.status == DraftStatus.ACTIVE
            }
            if (activeIndex >= 0) {
                // 取り直しは進行中の下書きの画像を最新の撮影へ差し替える。
                val active = drafts[activeIndex]
                discardedEditedImage = active.editedImagePath
                drafts[activeIndex] = active.copy(
                    selectedCaptureId = capture.id,
                    editState = EditState.NOT_STARTED,
                    editedImagePath = null,
                )
            } else if (!hasLiveDraft) {
                val settings = snapshot.settings[userId] ?: UserSettings(userId)
                newDraft = PostDraft(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    eventId = event.id,
                    selectedCaptureId = capture.id,
                    configuredMinutes = settings.autoPostMinutes,
                    firstCapturedAt = capturedAt,
                    autoPublishAt = AutoPostCalculator.initialDeadline(
                        capturedAt,
                        settings.autoPostMinutes,
                        event,
                    ),
                )
                drafts += newDraft
            }
            snapshot.copy(
                events = if (snapshot.events.any { it.id == event.id }) snapshot.events else snapshot.events + event,
                captures = snapshot.captures + capture,
                drafts = drafts,
            )
        }
        newDraft?.let(scheduler::schedule)
        discardedEditedImage?.let { images.delete(it) }
        capture
    }

    override suspend fun deleteCapture(userId: String, captureId: String): Result<Unit> = repositoryResult {
        val existing = store.current().captures.firstOrNull { it.id == captureId && it.userId == userId }
        requireNotNull(existing) { "撮影画像が見つかりません" }
        var cancelledDraftId: String? = null
        var rescheduledDraft: PostDraft? = null
        var discardedEditedImage: String? = null
        store.update { snapshot ->
            val remaining = snapshot.captures.filterNot { it.id == captureId }
            val drafts = snapshot.drafts.map { draft ->
                if (draft.selectedCaptureId != captureId || draft.status != DraftStatus.ACTIVE) {
                    draft
                } else {
                    discardedEditedImage = draft.editedImagePath
                    val replacement = remaining
                        .filter { it.userId == userId && it.eventId == draft.eventId }
                        .maxByOrNull { it.capturedAt }
                    if (replacement == null) {
                        cancelledDraftId = draft.id
                        draft.copy(
                            status = DraftStatus.CANCELLED,
                            editState = EditState.NOT_STARTED,
                            editedImagePath = null,
                        )
                    } else {
                        val event = snapshot.events.firstOrNull { it.id == draft.eventId }
                        val uneditedDeadline = event?.let {
                            AutoPostCalculator.initialDeadline(
                                draft.firstCapturedAt,
                                draft.configuredMinutes,
                                it,
                            )
                        } ?: draft.autoPublishAt
                        draft.copy(
                            selectedCaptureId = replacement.id,
                            editState = EditState.NOT_STARTED,
                            editedImagePath = null,
                            autoPublishAt = uneditedDeadline,
                        ).also { rescheduledDraft = it }
                    }
                }
            }
            snapshot.copy(captures = remaining, drafts = drafts)
        }
        cancelledDraftId?.let(scheduler::cancel)
        rescheduledDraft?.let(scheduler::schedule)
        images.delete(existing.filePath)
        discardedEditedImage?.let { images.delete(it) }
    }
}

