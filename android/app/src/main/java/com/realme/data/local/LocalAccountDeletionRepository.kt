package com.realme.data.local

import android.util.Log
import com.realme.data.repository.AccountDeletionRepository
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.AutoPostScheduler
import com.realme.data.repository.CaptureSessionController
import com.realme.data.repository.NoOpCaptureSessionController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class LocalAccountDeletionRepository(
    private val store: LocalAppStore,
    private val images: ImageFileStore,
    private val authRepository: AuthRepository,
    private val scheduler: AutoPostScheduler,
    private val captureSessionController: CaptureSessionController = NoOpCaptureSessionController,
    private val enqueueCleanup: () -> Unit = {},
) : AccountDeletionRepository {
    private val cleanupMutex = Mutex()
    val cleanups = store.snapshots.map { it.deletionCleanups }

    override suspend fun deleteAccount(userId: String, pin: String?): Result<Unit> = repositoryResult {
        val session = authRepository.session.first()
        require(session?.userId == userId) { "自分のアカウントだけ削除できます" }
        require(store.current().profiles.any { it.id == userId && !it.isDemo }) {
            "ローカルアカウントが見つかりません"
        }
        val initialPin = store.current().accountPins.firstOrNull { it.userId == userId }
        if (initialPin != null) {
            requireNotNull(pin) { "現在のPINを入力してください" }
            authRepository.verifyPin(userId, pin).getOrThrow()
        }
        val expectedPin = store.current().accountPins.firstOrNull { it.userId == userId }
        require(if (initialPin == null) expectedPin == null else
            expectedPin != null && expectedPin.salt == initialPin.salt && expectedPin.pinHash == initialPin.pinHash
        ) { "PINが変更されました。もう一度お試しください" }
        val captureStopped = captureSessionController.stopAndAwait()
        Log.i(TAG, "deleteAccount captureStopped=$captureStopped")
        require(captureStopped) {
            "撮影サービスを停止できませんでした。撮影を停止してから再試行してください"
        }
        val removedImages = mutableSetOf<String>()
        val removedDraftIds = mutableListOf<String>()
        store.update { latest ->
            require(latest.session?.userId == userId) { "サインイン状態が変更されました" }
            require(latest.accountPins.firstOrNull { it.userId == userId } == expectedPin) {
                "PINが変更されました。もう一度お試しください"
            }
            require(latest.profiles.any { it.id == userId && !it.isDemo }) {
                "ローカルアカウントが見つかりません"
            }
            val posts = latest.posts.filter { it.userId == userId }
            val postIds = posts.map { it.id }.toSet()
            val captures = latest.captures.filter { it.userId == userId }
            val drafts = latest.drafts.filter { it.userId == userId }
            val settings = latest.settings[userId]
            posts.mapNotNullTo(removedImages) { it.imagePath }
            captures.mapTo(removedImages) { it.filePath }
            drafts.mapNotNullTo(removedImages) { it.editedImagePath }
            settings?.privacyImagePath?.let(removedImages::add)
            val retainedImages = mutableSetOf<String>()
            latest.posts.filterNot { it.userId == userId }.mapNotNullTo(retainedImages) { it.imagePath }
            latest.captures.filterNot { it.userId == userId }.mapTo(retainedImages) { it.filePath }
            latest.drafts.filterNot { it.userId == userId }.mapNotNullTo(retainedImages) { it.editedImagePath }
            latest.settings.filterKeys { it != userId }.values.mapNotNullTo(retainedImages) { it.privacyImagePath }
            removedImages.removeAll(retainedImages)
            drafts.mapTo(removedDraftIds) { it.id }
            latest.copy(
                session = null,
                profiles = latest.profiles.filterNot { it.id == userId },
                posts = latest.posts.filterNot { it.userId == userId },
                captures = latest.captures.filterNot { it.userId == userId },
                drafts = latest.drafts.filterNot { it.userId == userId },
                settings = latest.settings - userId,
                accountPins = latest.accountPins.filterNot { it.userId == userId },
                deliveredCaptureReminders = latest.deliveredCaptureReminders.filterNot { it.startsWith("$userId|") }.toSet(),
                imageSaves = latest.imageSaves.filterNot {
                    it.savedByUserId == userId || it.postId in postIds
                },
                notifications = latest.notifications.filterNot {
                    it.userId == userId || it.postId in postIds || it.actorUserId == userId
                },
                deletionCleanups = latest.deletionCleanups + DeletionCleanup(
                    id = UUID.randomUUID().toString(),
                    imagePaths = removedImages.toList(),
                    draftIds = removedDraftIds.toList(),
                ),
            )
        }
        Log.i(TAG, "deleteAccount data removed, starting cleanup")
        runCatching { enqueueCleanup() }
        retryCleanup()
        Log.i(TAG, "deleteAccount completed")
        return@repositoryResult Unit
    }

    suspend fun enqueuePendingCleanup() {
        if (store.current().deletionCleanups.any { !it.completed }) enqueueCleanup()
    }

    suspend fun retryCleanup(): Boolean = cleanupMutex.withLock {
        val pending = store.current().deletionCleanups.filterNot { it.completed }
        for (cleanup in pending) {
            var failed = false
            for (draftId in cleanup.draftIds) {
                try {
                    scheduler.cancelAndWait(draftId)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed = true
                }
            }
            for (path in cleanup.imagePaths) {
                try {
                    val snapshot = store.current()
                    val referenced = snapshot.posts.any { it.imagePath == path } ||
                        snapshot.captures.any { it.filePath == path } ||
                        snapshot.drafts.any { it.editedImagePath == path } ||
                        snapshot.settings.values.any { it.privacyImagePath == path }
                    if (!referenced) images.delete(path)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    failed = true
                }
            }
            store.update { snapshot ->
                snapshot.copy(deletionCleanups = snapshot.deletionCleanups.map {
                    if (it.id == cleanup.id) it.copy(completed = !failed, failed = failed) else it
                })
            }
        }
        store.current().deletionCleanups.none { !it.completed }
    }

    suspend fun acknowledgeCleanup(id: String) {
        store.update { snapshot ->
            snapshot.copy(deletionCleanups = snapshot.deletionCleanups.filterNot { it.id == id && it.completed })
        }
    }

    private companion object {
        const val TAG = "RealMeDelete"
    }
}
