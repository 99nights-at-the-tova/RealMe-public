package com.realme.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.DraftStatus
import com.realme.core.model.JST
import com.realme.core.model.PostDraft
import com.realme.core.model.PostStatus
import com.realme.core.model.UserProfile
import com.realme.core.model.UserSettings
import com.realme.data.repository.AutoPostScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun nextReminderCleansOnlyExpiredUnreferencedCaptures() = withFixture { fixture ->
        val userId = "local-user"
        val oldImage = fixture.image("captures")
        val sharedImage = fixture.image("captures")
        val activeImage = fixture.image("captures")
        val currentImage = fixture.image("captures")
        fixture.seed(userId, sharedImage)
        val now = fixture.notification.plusSeconds(60)
        fixture.store.update { snapshot ->
            snapshot.copy(
                session = com.realme.core.model.AuthSession(userId),
                captures = listOf(
                    com.realme.core.model.Capture("old", userId, "old-event", oldImage, now.minusSeconds(86_400), true),
                    com.realme.core.model.Capture("shared", userId, "shared-event", sharedImage, now.minusSeconds(86_400), true),
                    com.realme.core.model.Capture("active", userId, "active-event", activeImage, now.minusSeconds(86_400), true),
                    com.realme.core.model.Capture("current", userId, fixture.event.id, currentImage, now, true),
                ),
                drafts = listOf(PostDraft("active-draft", userId, "active-event", "active", firstCapturedAt = now.minusSeconds(86_400), autoPublishAt = now)),
            )
        }
        fixture.captures.cleanupExpiredCaptures(userId, fixture.today)
        val snapshot = fixture.store.current()
        assertEquals(setOf("active", "current"), snapshot.captures.map { it.id }.toSet())
        assertFalse(File(oldImage).exists())
        assertTrue(File(sharedImage).exists())
        assertTrue(File(activeImage).exists())
        assertTrue(File(currentImage).exists())
        assertTrue(snapshot.expiredCaptureCleanupPaths.isEmpty())
    }

    @Test
    fun nextReminderCancelsExpiredDraftBeforeRemovingItsCapture() = withFixture { fixture ->
        val userId = "local-user"
        val oldImage = fixture.image("captures")
        fixture.seed(userId, fixture.image("privacy"))
        val oldEvent = DailyEventFactory.forDate(fixture.today.minusDays(1), fixture.notification.minusSeconds(86_400))
        val oldTime = oldEvent.notificationAt.plusSeconds(60)
        fixture.store.update { snapshot ->
            snapshot.copy(
                session = com.realme.core.model.AuthSession(userId),
                events = snapshot.events + oldEvent,
                captures = listOf(com.realme.core.model.Capture("old", userId, oldEvent.id, oldImage, oldTime, true)),
                drafts = listOf(PostDraft("expired", userId, oldEvent.id, "old", firstCapturedAt = oldTime, autoPublishAt = oldTime.plusSeconds(300))),
            )
        }
        fixture.captures.cleanupExpiredCaptures(userId, fixture.today)
        assertTrue(fixture.store.current().captures.isEmpty())
        assertEquals(DraftStatus.FAILED, fixture.store.current().drafts.single().status)
        assertTrue(fixture.scheduler.cancelled.contains("expired"))
        assertFalse(File(oldImage).exists())
    }

    @Test
    fun reminderIsRecordedOnceAndRequiresTheCurrentSession() = withFixture { fixture ->
        val userId = "local-user"
        fixture.seed(userId, fixture.image("privacy"))
        fixture.store.update { it.copy(session = com.realme.core.model.AuthSession(userId)) }
        val repository = LocalEventRepository(fixture.store)
        var delivered = 0
        assertFalse(repository.deliverReminderOnce(userId, fixture.event) { false })
        assertFalse(repository.hasDeliveredReminder(userId, fixture.event))
        assertTrue(repository.deliverReminderOnce(userId, fixture.event) { delivered++; true })
        assertFalse(repository.deliverReminderOnce(userId, fixture.event) { delivered++; true })
        assertEquals(1, delivered)
        fixture.store.update { it.copy(session = null) }
        val next = DailyEventFactory.forDate(fixture.today.plusDays(1))
        repository.ensureEvent(next.eventDateJst)
        assertFalse(repository.deliverReminderOnce(userId, next) { delivered++; true })
        assertEquals(1, delivered)
    }

    @Test
    fun dailyPostIsUniqueAndPrivacyReplacementKeepsId() = withFixture { fixture ->
        val userId = "local-user"
        val game = fixture.image("captures")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy)

        val first = fixture.posts.createGamePost(
            userId,
            game,
            fixture.notification.plusSeconds(120),
            "first",
            fixture.notification.plusSeconds(180),
        )
        assertTrue(first.isSuccess)
        assertEquals(PostStatus.ON_TIME, first.getOrThrow().status)

        val duplicate = fixture.posts.createGamePost(
            userId,
            game,
            fixture.notification.plusSeconds(600),
            "duplicate",
            fixture.notification.plusSeconds(601),
        )
        assertTrue(duplicate.isFailure)
        assertEquals(1, fixture.store.current().posts.size)

        val originalId = first.getOrThrow().id
        val originalImage = first.getOrThrow().imagePath!!
        val replaced = fixture.posts.replaceWithPrivacy(userId, originalId).getOrThrow()
        assertEquals(originalId, replaced.id)
        assertEquals(PostStatus.PRIVACY, replaced.status)
        assertEquals(1, fixture.store.current().posts.size)
        assertFalse(File(originalImage).exists())

        assertTrue(fixture.posts.deletePost("another-user", originalId).isFailure)
        assertEquals(1, fixture.store.current().posts.size)
        assertTrue(fixture.posts.deletePost(userId, originalId).isSuccess)
        assertTrue(File(privacy).exists())
    }

    @Test
    fun firstCaptureStartsOnlyOneTimerAndCancellationIsRespected() = withFixture { fixture ->
        val userId = "local-user"
        val firstImage = fixture.image("captures")
        val secondImage = fixture.image("captures")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy)
        val firstCaptureAt = fixture.notification.plusSeconds(60)

        fixture.captures.registerCapture(userId, firstImage, firstCaptureAt).getOrThrow()
        val draft = fixture.store.current().drafts.single()
        assertEquals(firstCaptureAt.plusSeconds(300), draft.autoPublishAt)
        assertEquals(listOf(draft.id), fixture.scheduler.scheduled.map { it.id })

        fixture.captures.registerCapture(userId, secondImage, firstCaptureAt.plusSeconds(60)).getOrThrow()
        assertEquals(1, fixture.store.current().drafts.size)
        assertEquals(firstCaptureAt, fixture.store.current().drafts.single().firstCapturedAt)

        val secondCapture = fixture.store.current().captures.single { it.filePath == secondImage }
        fixture.posts.selectDraftCapture(userId, secondCapture.id).getOrThrow()
        assertEquals(secondCapture.id, fixture.store.current().drafts.single().selectedCaptureId)
        assertEquals(draft.autoPublishAt, fixture.store.current().drafts.single().autoPublishAt)

        fixture.posts.cancelDraft(userId, draft.id).getOrThrow()
        assertEquals(DraftStatus.CANCELLED, fixture.store.current().drafts.single().status)
        assertTrue(fixture.scheduler.cancelled.contains(draft.id))
        assertTrue(fixture.posts.publishDraft(draft.id, draft.autoPublishAt).isFailure)
        assertEquals(0, fixture.store.current().posts.size)
    }

    @Test
    fun confirmedEditedImageIsUsedForAutomaticPublishing() = withFixture { fixture ->
        val userId = "local-user"
        val captureImage = fixture.image("captures")
        val editedImage = fixture.image("edits")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy, autoPostMinutes = 45)
        val firstCaptureAt = fixture.notification.plusSeconds(60)
        fixture.captures.registerCapture(userId, captureImage, firstCaptureAt).getOrThrow()

        val draft = fixture.posts.beginDraftEditing(
            userId,
            fixture.store.current().captures.single().id,
            firstCaptureAt.plusSeconds(600),
        ).getOrThrow()!!
        assertEquals(firstCaptureAt.plusSeconds(2700), draft.autoPublishAt)

        val captureId = fixture.store.current().captures.single().id
        fixture.posts.markDraftReady(userId, draft.id, editedImage, captureId).getOrThrow()
        val published = fixture.posts.publishDraft(draft.id, draft.autoPublishAt).getOrThrow()

        assertEquals(PostStatus.ON_TIME, published.status)
        assertNotEquals(captureImage, published.imagePath)
        assertEquals(fixture.store.current().drafts.single().editState, com.realme.core.model.EditState.READY)
    }

    @Test
    fun changingSelectedCaptureClearsThePreviousEditedImage() = withFixture { fixture ->
        val userId = "local-user"
        val captureA = fixture.image("captures")
        val captureB = fixture.image("captures")
        val editedA = fixture.image("edits")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy, autoPostMinutes = 45)
        val captureAAt = fixture.notification.plusSeconds(60)
        val captureBAt = fixture.notification.plusSeconds(120)
        fixture.captures.registerCapture(userId, captureA, captureAAt).getOrThrow()
        fixture.captures.registerCapture(userId, captureB, captureBAt).getOrThrow()
        val initial = fixture.store.current()
        val draft = initial.drafts.single()
        fixture.posts.beginDraftEditing(
            userId,
            initial.captures.single { it.filePath == captureA }.id,
            captureAAt.plusSeconds(600),
        ).getOrThrow()
        fixture.posts.markDraftReady(userId, draft.id, editedA).getOrThrow()
        assertEquals(captureAAt.plusSeconds(2700), fixture.scheduler.scheduled.single().autoPublishAt)

        val captureBId = fixture.store.current().captures.single { it.filePath == captureB }.id
        val changed = fixture.posts.selectDraftCapture(userId, captureBId).getOrThrow()
        assertEquals(captureBId, changed.selectedCaptureId)
        assertEquals(com.realme.core.model.EditState.NOT_STARTED, changed.editState)
        assertEquals(null, changed.editedImagePath)
        assertEquals(captureAAt.plusSeconds(900), changed.autoPublishAt)
        assertEquals(changed, fixture.scheduler.scheduled.single())

        // If the shortened deadline has already passed, publishing remains possible immediately.
        val published = fixture.posts.publishDraft(draft.id, changed.autoPublishAt.plusSeconds(1)).getOrThrow()
        assertEquals(captureBAt, published.capturedAt)
        assertNotEquals(editedA, published.imagePath)
        assertEquals(PostStatus.ON_TIME, published.status)
    }

    @Test
    fun selectingCurrentCaptureKeepsEditedImageAndDeadline() = withFixture { fixture ->
        val userId = "local-user"
        val captureImage = fixture.image("captures")
        val editedImage = fixture.image("edits")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy, autoPostMinutes = 45)
        val capturedAt = fixture.notification.plusSeconds(60)
        val capture = fixture.captures.registerCapture(userId, captureImage, capturedAt).getOrThrow()
        val draft = fixture.posts.beginDraftEditing(userId, capture.id, capturedAt.plusSeconds(600)).getOrThrow()!!
        val ready = fixture.posts.markDraftReady(userId, draft.id, editedImage, capture.id).getOrThrow()
        val scheduleCalls = fixture.scheduler.scheduleCalls

        val selected = fixture.posts.selectDraftCapture(userId, capture.id).getOrThrow()

        assertEquals(ready, selected)
        assertEquals(capturedAt.plusSeconds(2700), selected.autoPublishAt)
        assertEquals(editedImage, selected.editedImagePath)
        assertEquals(scheduleCalls, fixture.scheduler.scheduleCalls)
    }

    @Test
    fun deletingEditedSelectedCaptureSwitchesToFreshCaptureAndDeadline() = withFixture { fixture ->
        val userId = "local-user"
        val captureA = fixture.image("captures")
        val captureB = fixture.image("captures")
        val editedA = fixture.image("edits")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy, autoPostMinutes = 45)
        val captureAAt = fixture.notification.plusSeconds(60)
        val captureBAt = fixture.notification.plusSeconds(600)
        fixture.captures.registerCapture(userId, captureA, captureAAt).getOrThrow()
        fixture.captures.registerCapture(userId, captureB, captureBAt).getOrThrow()
        val initial = fixture.store.current()
        val captureAId = initial.captures.single { it.filePath == captureA }.id
        val draft = initial.drafts.single()
        fixture.posts.beginDraftEditing(userId, captureAId, captureAAt.plusSeconds(600)).getOrThrow()
        fixture.posts.markDraftReady(userId, draft.id, editedA, captureAId).getOrThrow()

        fixture.captures.deleteCapture(userId, captureAId).getOrThrow()

        val updated = fixture.store.current()
        val changed = updated.drafts.single()
        val captureBId = updated.captures.single().id
        assertEquals(captureBId, changed.selectedCaptureId)
        assertEquals(com.realme.core.model.EditState.NOT_STARTED, changed.editState)
        assertEquals(null, changed.editedImagePath)
        assertEquals(captureAAt.plusSeconds(900), changed.autoPublishAt)
        assertFalse(File(editedA).exists())

        val published = fixture.posts.publishDraft(draft.id, changed.autoPublishAt).getOrThrow()
        assertEquals(captureBAt, published.capturedAt)
        assertNotEquals(editedA, published.imagePath)
        assertEquals(PostStatus.LATE, published.status)
    }

    @Test
    fun editingUnselectedCaptureDoesNotMutateTheAutomaticDraft() = withFixture { fixture ->
        val userId = "local-user"
        val captureA = fixture.image("captures")
        val captureB = fixture.image("captures")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy, autoPostMinutes = 45)
        val captureAAt = fixture.notification.plusSeconds(60)
        val captureBAt = fixture.notification.plusSeconds(120)
        fixture.captures.registerCapture(userId, captureA, captureAAt).getOrThrow()
        fixture.captures.registerCapture(userId, captureB, captureBAt).getOrThrow()
        val before = fixture.store.current().drafts.single()
        val captureBId = fixture.store.current().captures.single { it.filePath == captureB }.id

        val result = fixture.posts.beginDraftEditing(
            userId,
            captureBId,
            captureBAt.plusSeconds(600),
        ).getOrThrow()

        assertEquals(null, result)
        val after = fixture.store.current().drafts.single()
        assertEquals(com.realme.core.model.EditState.NOT_STARTED, after.editState)
        assertEquals(null, after.editedImagePath)
        assertEquals(before.autoPublishAt, after.autoPublishAt)
    }

    @Test
    fun unsavedEditingFallsBackToPrivacyAndNeverPublishesOriginal() = withFixture { fixture ->
        val userId = "local-user"
        val captureImage = fixture.image("captures")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy, autoPostMinutes = 45)
        val firstCaptureAt = fixture.notification.plusSeconds(60)
        fixture.captures.registerCapture(userId, captureImage, firstCaptureAt).getOrThrow()
        val draft = fixture.posts.beginDraftEditing(
            userId,
            fixture.store.current().captures.single().id,
            firstCaptureAt.plusSeconds(600),
        ).getOrThrow()!!

        val published = fixture.posts.publishDraft(draft.id, draft.autoPublishAt).getOrThrow()

        assertEquals(PostStatus.PRIVACY, published.status)
        assertEquals(privacy, published.imagePath)
        assertNotEquals(captureImage, published.imagePath)
    }

    @Test
    fun expiredDraftNeverFallsBackIntoTheNextDayEvent() = withFixture { fixture ->
        val userId = "local-user"
        val captureImage = fixture.image("captures")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy)
        fixture.captures.registerCapture(userId, captureImage, fixture.notification.plusSeconds(60)).getOrThrow()
        val draft = fixture.store.current().drafts.single()

        val afterMidnight = fixture.event.deadlineAt.plusSeconds(1)
        val result = fixture.posts.publishDraft(draft.id, afterMidnight)

        assertTrue(result.isFailure)
        assertEquals(0, fixture.store.current().posts.size)
        assertEquals(DraftStatus.FAILED, fixture.store.current().drafts.single().status)
    }

    @Test
    fun automaticFailureFallsBackToPrivacyInsteadOfPublishingMissingGameImage() = withFixture { fixture ->
        val userId = "local-user"
        val captureImage = fixture.image("captures")
        val privacy = fixture.image("privacy")
        fixture.seed(userId, privacy)
        fixture.captures.registerCapture(userId, captureImage, fixture.notification.plusSeconds(60)).getOrThrow()
        val draft = fixture.store.current().drafts.single()
        assertTrue(File(captureImage).delete())

        val published = fixture.posts.publishDraft(draft.id, draft.autoPublishAt).getOrThrow()
        assertEquals(PostStatus.PRIVACY, published.status)
        assertEquals(privacy, published.imagePath)
        assertNotEquals(captureImage, published.imagePath)
        assertEquals(1, fixture.store.current().posts.size)
    }

    private fun withFixture(block: suspend (Fixture) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "realme-test-${UUID.randomUUID()}.preferences_pb")
        }
        val store = LocalAppStore(context, dataStore)
        try {
            runBlocking { block(Fixture(context, store)) }
        } finally {
            scope.cancel()
        }
    }

    private class Fixture(context: Context, val store: LocalAppStore) {
        val today: LocalDate = Instant.now().atZone(JST).toLocalDate()
        val notification: Instant = today.atTime(18, 0).atZone(JST).toInstant()
        val event = DailyEventFactory.forDate(today, notification)
        val images = ImageFileStore(context)
        val scheduler = FakeScheduler()
        val posts = LocalPostRepository(store, images, scheduler)
        val captures = LocalCaptureRepository(store, images, scheduler)

        suspend fun image(folder: String): String {
            val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(40, 120, 90))
            return try {
                images.saveBitmap(bitmap, folder)
            } finally {
                bitmap.recycle()
            }
        }

        suspend fun seed(userId: String, privacyPath: String, autoPostMinutes: Int = 5) {
            store.update { snapshot ->
                snapshot.copy(
                    profiles = listOf(UserProfile(userId, "Test")),
                    events = listOf(event),
                    settings = mapOf(
                        userId to UserSettings(
                            userId,
                            autoPostMinutes = autoPostMinutes,
                            privacyImagePath = privacyPath,
                        ),
                    ),
                )
            }
        }
    }

    private class FakeScheduler : AutoPostScheduler {
        val scheduled = mutableListOf<PostDraft>()
        val cancelled = mutableListOf<String>()
        var scheduleCalls = 0
        override fun schedule(draft: PostDraft) {
            scheduleCalls++
            scheduled.removeAll { it.id == draft.id }
            scheduled += draft
        }
        override fun cancel(draftId: String) {
            cancelled += draftId
        }
    }
}
