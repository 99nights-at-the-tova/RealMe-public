package com.realme.data.local

import com.realme.core.model.AutoPostCalculator
import com.realme.core.model.DailyEvent
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.DraftStatus
import com.realme.core.model.EditState
import com.realme.core.model.ImageType
import com.realme.core.model.JST
import com.realme.core.model.Post
import com.realme.core.model.PostDraft
import com.realme.core.model.PostStatus
import com.realme.core.model.PostTimingPolicy
import com.realme.core.model.UserSettings
import com.realme.data.repository.AutoPostScheduler
import com.realme.data.repository.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.util.UUID

class LocalPostRepository(
    private val store: LocalAppStore,
    private val images: ImageFileStore,
    private val scheduler: AutoPostScheduler,
) : PostRepository {
    override val posts: Flow<List<Post>> = store.snapshots.map { snapshot ->
        snapshot.posts.sortedByDescending { it.postedAt }
    }

    override val drafts: Flow<List<PostDraft>> = store.snapshots.map { it.drafts }

    override suspend fun createGamePost(
        userId: String,
        imagePath: String,
        capturedAt: Instant,
        caption: String,
        now: Instant,
    ): Result<Post> = repositoryResult {
        createGamePostInternal(userId, imagePath, capturedAt, caption, now)
    }

    private suspend fun createGamePostInternal(
        userId: String,
        imagePath: String,
        capturedAt: Instant,
        caption: String,
        now: Instant,
    ): Post {
        require(caption.length <= MAX_CAPTION_LENGTH) { "キャプションは140文字以内で入力してください" }
        val date = capturedAt.atZone(JST).toLocalDate()
        val postId = UUID.randomUUID().toString()
        val copiedImagePath = images.copyToPostImage(imagePath)
        val publishedDraftIds = mutableListOf<String>()
        try {
            val updated = store.update { snapshot ->
                require(snapshot.profiles.any { it.id == userId && !it.isDemo }) { "ローカルアカウントが見つかりません" }
                val event = snapshot.events.firstOrNull { it.eventDateJst == date }
                    ?: DailyEventFactory.forDate(date)
                val status = PostTimingPolicy.resolveStatus(ImageType.GAME, capturedAt, now, event)
                val post = Post(
                    id = postId,
                    userId = userId,
                    eventId = event.id,
                    eventDateJst = date,
                    imagePath = copiedImagePath,
                    imageType = ImageType.GAME,
                    capturedAt = capturedAt,
                    postedAt = now,
                    status = status,
                    caption = caption.trim(),
                )
                val drafts = snapshot.drafts.map { draft ->
                    if (draft.userId == userId && draft.eventId == event.id && draft.status == DraftStatus.ACTIVE) {
                        publishedDraftIds += draft.id
                        draft.copy(status = DraftStatus.PUBLISHED)
                    } else {
                        draft
                    }
                }
                snapshot.copy(
                    events = if (snapshot.events.any { it.id == event.id }) snapshot.events else snapshot.events + event,
                    posts = snapshot.posts + post,
                    drafts = drafts,
                )
            }
            publishedDraftIds.forEach(scheduler::cancel)
            return updated.posts.first { it.id == postId }
        } catch (error: Exception) {
            images.delete(copiedImagePath)
            throw error
        }
    }

    override suspend fun createPrivacyPost(
        userId: String,
        imagePath: String,
        caption: String,
        now: Instant,
    ): Result<Post> = repositoryResult {
        val date = now.atZone(JST).toLocalDate()
        val event = store.current().events.firstOrNull { it.eventDateJst == date }
            ?: DailyEventFactory.forDate(date)
        createPrivacyPostForEvent(userId, imagePath, caption, now, event)
    }

    private suspend fun createPrivacyPostForEvent(
        userId: String,
        imagePath: String,
        caption: String,
        now: Instant,
        event: DailyEvent,
    ): Post {
        require(caption.length <= MAX_CAPTION_LENGTH) { "キャプションは140文字以内で入力してください" }
        require(images.exists(imagePath)) { "プライバシー画像を先に設定してください" }
        require(now.atZone(JST).toLocalDate() == event.eventDateJst) {
            "イベント日と投稿日が一致しません"
        }
        PostTimingPolicy.resolveStatus(ImageType.PRIVACY, null, now, event)
        val postId = UUID.randomUUID().toString()
        val publishedDraftIds = mutableListOf<String>()
        val updated = store.update { snapshot ->
            require(snapshot.profiles.any { it.id == userId && !it.isDemo }) { "ローカルアカウントが見つかりません" }
            val post = Post(
                id = postId,
                userId = userId,
                eventId = event.id,
                eventDateJst = event.eventDateJst,
                imagePath = imagePath,
                imageType = ImageType.PRIVACY,
                capturedAt = null,
                postedAt = now,
                status = PostStatus.PRIVACY,
                caption = caption.trim(),
            )
            val drafts = snapshot.drafts.map { draft ->
                if (draft.userId == userId && draft.eventId == event.id && draft.status == DraftStatus.ACTIVE) {
                    publishedDraftIds += draft.id
                    draft.copy(status = DraftStatus.PUBLISHED)
                } else {
                    draft
                }
            }
            snapshot.copy(
                events = if (snapshot.events.any { it.id == event.id }) snapshot.events else snapshot.events + event,
                posts = snapshot.posts + post,
                drafts = drafts,
            )
        }
        publishedDraftIds.forEach(scheduler::cancel)
        return updated.posts.first { it.id == postId }
    }

    override suspend fun replaceWithPrivacy(userId: String, postId: String): Result<Post> = repositoryResult {
        val settings = store.current().settings[userId] ?: UserSettings(userId)
        val privacyPath = requireNotNull(settings.privacyImagePath) { "プライバシー画像を先に設定してください" }
        require(images.exists(privacyPath)) { "プライバシー画像ファイルが見つかりません" }
        var oldPost: Post? = null
        val updated = store.update { snapshot ->
            val existing = snapshot.posts.firstOrNull { it.id == postId && it.userId == userId }
            requireNotNull(existing) { "自分の投稿が見つかりません" }
            oldPost = existing
            val replacement = existing.copy(
                imagePath = privacyPath,
                imageType = ImageType.PRIVACY,
                capturedAt = null,
                status = PostStatus.PRIVACY,
            )
            snapshot.copy(posts = snapshot.posts.filterNot { it.id == postId } + replacement)
        }
        val previous = oldPost
        if (previous?.imageType == ImageType.GAME) images.delete(previous.imagePath)
        updated.posts.first { it.id == postId }
    }

    override suspend fun deletePost(userId: String, postId: String): Result<Unit> = repositoryResult {
        val existing = store.current().posts.firstOrNull { it.id == postId && it.userId == userId }
        requireNotNull(existing) { "自分の投稿が見つかりません" }
        val removedDraftIds = mutableListOf<String>()
        store.update { snapshot ->
            check(snapshot.posts.any { it.id == postId && it.userId == userId }) { "投稿を削除できません" }
            val remainingDrafts = snapshot.drafts.filterNot { draft ->
                val belongsToPost = draft.userId == userId && draft.eventId == existing.eventId &&
                    draft.status == DraftStatus.PUBLISHED
                if (belongsToPost) removedDraftIds += draft.id
                belongsToPost
            }
            snapshot.copy(
                posts = snapshot.posts.filterNot { it.id == postId && it.userId == userId },
                drafts = remainingDrafts,
            )
        }
        // 投稿を消したら同じイベントの下書きも消し、次回の撮影で作り直せるようにする。
        removedDraftIds.forEach(scheduler::cancel)
        if (existing.imageType == ImageType.GAME) images.delete(existing.imagePath)
    }

    override suspend fun selectDraftCapture(userId: String, captureId: String): Result<PostDraft> = repositoryResult {
        var updated: PostDraft? = null
        var rescheduledDraft: PostDraft? = null
        store.update { snapshot ->
            val capture = snapshot.captures.firstOrNull { it.id == captureId && it.userId == userId }
            requireNotNull(capture) { "投稿に使える撮影画像が見つかりません" }
            val drafts = snapshot.drafts.map { draft ->
                if (draft.userId == userId && draft.eventId == capture.eventId && draft.status == DraftStatus.ACTIVE) {
                    val selectionChanged = draft.selectedCaptureId != captureId
                    val uneditedDeadline = if (selectionChanged) {
                        val event = requireNotNull(snapshot.events.firstOrNull { it.id == draft.eventId }) {
                            "下書きの日次イベントが見つかりません"
                        }
                        AutoPostCalculator.initialDeadline(draft.firstCapturedAt, draft.configuredMinutes, event)
                    } else {
                        draft.autoPublishAt
                    }
                    draft.copy(
                        selectedCaptureId = captureId,
                        editState = if (selectionChanged) EditState.NOT_STARTED else draft.editState,
                        editedImagePath = if (selectionChanged) null else draft.editedImagePath,
                        autoPublishAt = uneditedDeadline,
                    ).also {
                        updated = it
                        if (selectionChanged) rescheduledDraft = it
                    }
                } else {
                    draft
                }
            }
            requireNotNull(updated) { "選択中の自動投稿下書きがありません" }
            snapshot.copy(drafts = drafts)
        }
        rescheduledDraft?.let(scheduler::schedule)
        requireNotNull(updated)
    }

    override suspend fun beginDraftEditing(
        userId: String,
        captureId: String,
        now: Instant,
    ): Result<PostDraft?> = repositoryResult {
        var updated: PostDraft? = null
        store.update { snapshot ->
            val capture = snapshot.captures.firstOrNull {
                it.id == captureId && it.userId == userId
            } ?: return@update snapshot
            val draft = snapshot.drafts.firstOrNull {
                it.userId == userId && it.eventId == capture.eventId && it.status == DraftStatus.ACTIVE
            } ?: return@update snapshot
            // 編集中の画像が自動投稿の選択画像でなければ、手動投稿用の編集として扱う。
            if (draft.selectedCaptureId != captureId) return@update snapshot
            if (draft.editState == EditState.EDITING) {
                updated = draft
                return@update snapshot
            }
            val event = snapshot.events.firstOrNull { it.id == draft.eventId }
                ?: return@update snapshot
            require(now.isBefore(event.deadlineAt)) { "当日の公開期限を過ぎています" }
            val edited = if (draft.editState == EditState.READY) {
                draft.copy(editState = EditState.EDITING, editedImagePath = null)
            } else {
                draft.copy(
                    editState = EditState.EDITING,
                    autoPublishAt = AutoPostCalculator.onEditStarted(draft, now, event),
                )
            }
            updated = edited
            snapshot.copy(drafts = snapshot.drafts.filterNot { it.id == draft.id } + edited)
        }
        updated?.let(scheduler::schedule)
        updated
    }

    override suspend fun markDraftReady(
        userId: String,
        draftId: String,
        editedImagePath: String,
        expectedCaptureId: String?,
    ): Result<PostDraft> = repositoryResult {
        require(images.exists(editedImagePath)) { "編集済み画像が見つかりません" }
        var updated: PostDraft? = null
        store.update { snapshot ->
            val draft = snapshot.drafts.firstOrNull {
                it.id == draftId && it.userId == userId && it.status == DraftStatus.ACTIVE
            }
            requireNotNull(draft) { "編集対象の自動投稿下書きが見つかりません" }
            require(expectedCaptureId == null || draft.selectedCaptureId == expectedCaptureId) {
                "自動投稿の選択画像が変更されたため、この編集結果は適用できません"
            }
            require(draft.editState == EditState.EDITING || draft.editState == EditState.READY) {
                "編集状態が開始されていません"
            }
            val ready = draft.copy(editState = EditState.READY, editedImagePath = editedImagePath)
            updated = ready
            snapshot.copy(drafts = snapshot.drafts.filterNot { it.id == draftId } + ready)
        }
        val result = requireNotNull(updated)
        scheduler.schedule(result)
        result
    }

    override suspend fun publishDraft(draftId: String, now: Instant): Result<Post> = repositoryResult {
        val draft = getDraft(draftId)
        requireNotNull(draft) { "自動投稿の下書きが見つかりません" }
        if (draft.status != DraftStatus.ACTIVE) {
            val existing = store.current().posts.firstOrNull {
                it.userId == draft.userId && it.eventId == draft.eventId
            }
            return@repositoryResult requireNotNull(existing) { "下書きは公開可能な状態ではありません" }
        }
        require(!now.isBefore(draft.autoPublishAt)) { "まだ自動投稿時刻になっていません" }
        val existing = store.current().posts.firstOrNull {
            it.userId == draft.userId && it.eventId == draft.eventId
        }
        if (existing != null) {
            markDraft(draftId, DraftStatus.PUBLISHED)
            return@repositoryResult existing
        }
        val event = store.current().events.firstOrNull { it.id == draft.eventId }
            ?: run {
                markDraft(draftId, DraftStatus.FAILED)
                throw IllegalStateException("下書きの日次イベントが見つかりません")
            }
        val nowDate = now.atZone(JST).toLocalDate()
        if (nowDate != event.eventDateJst || !now.isBefore(event.deadlineAt)) {
            markDraft(draftId, DraftStatus.FAILED)
            throw IllegalStateException("イベントの公開期限を過ぎたため公開しません")
        }
        try {
            val capture = store.current().captures.firstOrNull {
                it.id == draft.selectedCaptureId && it.userId == draft.userId
            }
            requireNotNull(capture) { "自動投稿する撮影画像が見つかりません" }
            val imagePath = when {
                draft.editState == EditState.EDITING && draft.editedImagePath == null ->
                    throw IllegalStateException("未保存の編集内容があるため元画像を公開しません")
                draft.editedImagePath != null -> {
                    require(draft.editState == EditState.READY) { "編集内容が確定していません" }
                    draft.editedImagePath
                }
                else -> capture.filePath
            }
            requireNotNull(imagePath)
            require(images.exists(imagePath)) { "自動投稿する画像ファイルが見つかりません" }
            createGamePostInternal(
                userId = draft.userId,
                imagePath = imagePath,
                capturedAt = capture.capturedAt,
                caption = "",
                now = now,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            val racedPost = store.current().posts.firstOrNull {
                it.userId == draft.userId && it.eventId == draft.eventId
            }
            if (racedPost != null) {
                markDraft(draftId, DraftStatus.PUBLISHED)
                return@repositoryResult racedPost
            }
            val privacyPath = store.current().settings[draft.userId]?.privacyImagePath
            if (privacyPath != null && images.exists(privacyPath)) {
                val fallback = try {
                    createPrivacyPostForEvent(draft.userId, privacyPath, "", now, event)
                } catch (fallbackError: Exception) {
                    if (fallbackError is CancellationException) throw fallbackError
                    null
                }
                if (fallback != null) return@repositoryResult fallback
            }
            val afterFallback = store.current().posts.firstOrNull {
                it.userId == draft.userId && it.eventId == draft.eventId
            }
            if (afterFallback != null) {
                markDraft(draftId, DraftStatus.PUBLISHED)
                return@repositoryResult afterFallback
            }
            markDraft(draftId, DraftStatus.FAILED)
            throw error
        }
    }

    override suspend fun cancelDraft(userId: String, draftId: String): Result<Unit> = repositoryResult {
        val draft = getDraft(draftId)
        requireNotNull(draft) { "下書きが見つかりません" }
        require(draft.userId == userId) { "自分の下書きのみキャンセルできます" }
        require(draft.status == DraftStatus.ACTIVE) { "この下書きはすでに処理済みです" }
        markDraft(draftId, DraftStatus.CANCELLED)
    }

    override suspend fun getDraft(draftId: String): PostDraft? =
        store.current().drafts.firstOrNull { it.id == draftId }

    private suspend fun markDraft(draftId: String, status: DraftStatus) {
        store.update { snapshot ->
            snapshot.copy(
                drafts = snapshot.drafts.map { if (it.id == draftId) it.copy(status = status) else it },
            )
        }
        scheduler.cancel(draftId)
    }

    private companion object {
        const val MAX_CAPTION_LENGTH = 140
    }
}

