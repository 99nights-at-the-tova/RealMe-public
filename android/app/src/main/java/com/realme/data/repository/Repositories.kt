package com.realme.data.repository

import com.realme.core.model.AuthSession
import com.realme.core.model.Capture
import com.realme.core.model.DailyEvent
import com.realme.core.model.Post
import com.realme.core.model.PostDraft
import com.realme.core.model.PostStatus
import com.realme.core.model.UserProfile
import com.realme.core.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.LocalDate

interface AuthRepository {
    val session: Flow<AuthSession?>
    val localAccounts: Flow<List<UserProfile>>
    val pinProtectedAccountIds: Flow<Set<String>> get() = flowOf(emptySet())
    fun observeProfile(userId: String): Flow<UserProfile?>
    suspend fun signInAnonymously(displayName: String): Result<UserProfile>
    suspend fun signInExisting(userId: String): Result<UserProfile>
    suspend fun signInExisting(userId: String, pin: String?): Result<UserProfile> =
        if (pin == null) signInExisting(userId) else Result.failure(IllegalArgumentException("PIN未対応です"))

    suspend fun requiresPin(userId: String): Boolean = false
    suspend fun verifyPin(userId: String, pin: String): Result<Unit> =
        Result.failure(IllegalArgumentException("PIN未対応です"))

    suspend fun setAccountPin(userId: String, currentPin: String?, newPin: String?): Result<Unit> =
        Result.failure(IllegalArgumentException("PIN未対応です"))

    suspend fun signOut()
}

interface ProfileRepository {
    val profiles: Flow<List<UserProfile>>
    suspend fun updateProfile(userId: String, displayName: String, bio: String): Result<UserProfile>
}

interface EventRepository {
    fun observeEvent(date: LocalDate): Flow<DailyEvent>
    suspend fun ensureEvent(date: LocalDate): DailyEvent
    suspend fun startDemoEvent(date: LocalDate, status: PostStatus, now: Instant): Result<DailyEvent>
    /** ゲーム検知などの任意のタイミングで、当日イベントの通知時刻を now に更新する。 */
    suspend fun startNotificationNow(date: LocalDate, now: Instant): Result<DailyEvent>
}

interface PostRepository {
    val posts: Flow<List<Post>>
    val drafts: Flow<List<PostDraft>>
    suspend fun createGamePost(
        userId: String,
        imagePath: String,
        capturedAt: Instant,
        caption: String,
        now: Instant,
    ): Result<Post>

    suspend fun createPrivacyPost(
        userId: String,
        imagePath: String,
        caption: String,
        now: Instant,
    ): Result<Post>

    suspend fun replaceWithPrivacy(userId: String, postId: String): Result<Post>
    suspend fun deletePost(userId: String, postId: String): Result<Unit>
    suspend fun selectDraftCapture(userId: String, captureId: String): Result<PostDraft>
    suspend fun beginDraftEditing(userId: String, captureId: String, now: Instant): Result<PostDraft?>
    suspend fun markDraftReady(
        userId: String,
        draftId: String,
        editedImagePath: String,
        expectedCaptureId: String? = null,
    ): Result<PostDraft>
    suspend fun publishDraft(draftId: String, now: Instant): Result<Post>
    suspend fun cancelDraft(userId: String, draftId: String): Result<Unit>
    suspend fun getDraft(draftId: String): PostDraft?
}

interface CaptureRepository {
    val captures: Flow<List<Capture>>
    suspend fun registerCapture(userId: String, filePath: String, capturedAt: Instant): Result<Capture>
    suspend fun deleteCapture(userId: String, captureId: String): Result<Unit>
}

interface SettingsRepository {
    fun observeSettings(userId: String): Flow<UserSettings>
    suspend fun getSettings(userId: String): UserSettings
    suspend fun setAutoPostMinutes(userId: String, minutes: Int): Result<UserSettings>
    suspend fun setOverlayAppearance(userId: String, sizeDp: Int, opacity: Float): Result<UserSettings>
    suspend fun setOverlayPosition(userId: String, x: Int, y: Int)
    suspend fun setPrivacyImage(userId: String, imagePath: String): Result<UserSettings>
    /** アプリ起動時に最終利用時刻を更新する。 */
    suspend fun touchLastActive(userId: String, now: Instant)
}

interface AutoPostScheduler {
    fun schedule(draft: PostDraft)
    fun cancel(draftId: String)
    suspend fun cancelAndWait(draftId: String) = cancel(draftId)
}

interface AccountDeletionRepository {
    suspend fun deleteAccount(userId: String, pin: String?): Result<Unit>
}

fun interface CaptureSessionController {
    suspend fun stopAndAwait(): Boolean
}

object NoOpCaptureSessionController : CaptureSessionController {
    override suspend fun stopAndAwait(): Boolean = true
}

