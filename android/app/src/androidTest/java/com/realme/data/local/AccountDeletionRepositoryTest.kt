package com.realme.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.realme.core.model.AuthSession
import com.realme.core.model.AppNotification
import com.realme.core.model.Capture
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.DraftStatus
import com.realme.core.model.ImageSave
import com.realme.core.model.ImageType
import com.realme.core.model.NotificationType
import com.realme.core.model.Post
import com.realme.core.model.PostDraft
import com.realme.core.model.PostStatus
import com.realme.core.model.UserProfile
import com.realme.core.model.UserSettings
import com.realme.core.util.TimeProvider
import com.realme.data.repository.AutoPostScheduler
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.CaptureSessionController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountDeletionRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun deletionStopsCaptureServiceBeforeRemovingAccount() = withFixture { fixture ->
        var stopped = false
        val deletion = LocalAccountDeletionRepository(
            fixture.store,
            fixture.images,
            fixture.auth,
            fixture.scheduler,
            captureSessionController = CaptureSessionController {
                stopped = true
                true
            },
        )
        deletion.deleteAccount("owner", "1234").getOrThrow()
        assertTrue(stopped)
        assertNull(fixture.store.current().session)
    }

    @Test
    fun unableToStopCaptureServicePreventsAccountDeletion() = withFixture { fixture ->
        val deletion = LocalAccountDeletionRepository(
            fixture.store,
            fixture.images,
            fixture.auth,
            fixture.scheduler,
            captureSessionController = CaptureSessionController { false },
        )
        assertTrue(deletion.deleteAccount("owner", "1234").isFailure)
        assertTrue(fixture.store.current().profiles.any { it.id == "owner" })
        assertEquals("owner", fixture.store.current().session?.userId)
    }

    @Test
    fun failedCleanupIsPersistedAndCanBeRetriedAfterRepositoryRecreation() = withFixture { fixture ->
        fixture.scheduler.failCancellation = true
        fixture.deletion.deleteAccount("owner", "1234").getOrThrow()
        val failed = fixture.store.current().deletionCleanups.single()
        assertTrue(failed.failed)
        assertFalse(failed.completed)
        assertFalse(File(fixture.ownerPostImage).exists())
        assertTrue(File(fixture.otherPostImage).exists())

        fixture.scheduler.failCancellation = false
        val recreated = LocalAccountDeletionRepository(fixture.store, fixture.images, fixture.auth, fixture.scheduler)
        assertTrue(recreated.retryCleanup())
        assertTrue(fixture.store.current().deletionCleanups.single().completed)
        assertTrue(fixture.scheduler.cancelled.contains("draft-owner"))
        recreated.acknowledgeCleanup(failed.id)
        assertTrue(fixture.store.current().deletionCleanups.isEmpty())
    }

    @Test
    fun cancelledCleanupRemainsPendingForTheNextRun() = withFixture { fixture ->
        fixture.scheduler.interruptCancellation = true
        try {
            fixture.deletion.deleteAccount("owner", "1234")
            throw AssertionError("Expected cancellation")
        } catch (_: CancellationException) {
            // The deletion record must survive cancellation after the account was removed.
        }
        assertFalse(fixture.store.current().deletionCleanups.single().completed)
        assertFalse(fixture.store.current().profiles.any { it.id == "owner" })
        fixture.scheduler.interruptCancellation = false
        var enqueued = 0
        val restarted = LocalAccountDeletionRepository(fixture.store, fixture.images, fixture.auth, fixture.scheduler) { enqueued++ }
        restarted.enqueuePendingCleanup()
        assertEquals(1, enqueued)
        assertTrue(restarted.retryCleanup())
        restarted.enqueuePendingCleanup()
        assertEquals(1, enqueued)
        assertFalse(File(fixture.ownerPostImage).exists())
        assertTrue(File(fixture.otherPostImage).exists())
    }

    @Test
    fun deletionPreservesImagesStillReferencedByAnotherAccount() = withFixture { fixture ->
        fixture.store.update { snapshot ->
            snapshot.copy(posts = snapshot.posts.map {
                if (it.id == "post-other") it.copy(imagePath = fixture.ownerPostImage) else it
            })
        }
        fixture.deletion.deleteAccount("owner", "1234").getOrThrow()
        assertTrue(File(fixture.ownerPostImage).exists())
        assertEquals(fixture.ownerPostImage, fixture.store.current().posts.single().imagePath)
    }

    @Test
    fun changedPinCannotAuthorizeDeletionWithEarlierPin() = withFixture { fixture ->
        val changingAuth = object : AuthRepository by fixture.auth {
            override suspend fun verifyPin(userId: String, pin: String): Result<Unit> {
                val result = fixture.auth.verifyPin(userId, pin)
                if (result.isSuccess) fixture.auth.setAccountPin(userId, pin, "5678").getOrThrow()
                return result
            }
        }
        val deletion = LocalAccountDeletionRepository(fixture.store, fixture.images, changingAuth, fixture.scheduler)
        assertTrue(deletion.deleteAccount("owner", "1234").isFailure)
        assertTrue(fixture.store.current().profiles.any { it.id == "owner" })
    }

    @Test
    fun deletedAccountCannotRecreatePostsCapturesOrSettings() = withFixture { fixture ->
        fixture.deletion.deleteAccount("owner", "1234").getOrThrow()
        val now = Instant.parse("2026-11-15T10:00:00Z")
        val posts = LocalPostRepository(fixture.store, fixture.images, fixture.scheduler)
        val captures = LocalCaptureRepository(fixture.store, fixture.images, fixture.scheduler)
        val settings = LocalSettingsRepository(fixture.store, fixture.images)
        assertTrue(posts.createGamePost("owner", fixture.otherPostImage, now, "", now).isFailure)
        assertTrue(posts.createPrivacyPost("owner", fixture.otherPrivacyImage, "", now).isFailure)
        assertTrue(captures.registerCapture("owner", fixture.otherCaptureImage, now).isFailure)
        assertTrue(settings.setPrivacyImage("owner", fixture.otherPrivacyImage).isFailure)
        val snapshot = fixture.store.current()
        assertTrue(snapshot.posts.none { it.userId == "owner" })
        assertTrue(snapshot.captures.none { it.userId == "owner" })
        assertFalse(snapshot.settings.containsKey("owner"))
    }

    @Test
    fun deleteOwnerDataKeepsOtherAccountsAndCannotBeRunWithWrongPin() = withFixture { fixture ->
        val wrong = fixture.deletion.deleteAccount("owner", "9999")
        assertTrue(wrong.isFailure)
        assertTrue(fixture.store.current().profiles.any { it.id == "owner" })

        fixture.deletion.deleteAccount("owner", "1234").getOrThrow()

        val snapshot = fixture.store.current()
        assertNull(snapshot.session)
        assertEquals(listOf("other"), snapshot.profiles.map { it.id })
        assertEquals(listOf("post-other"), snapshot.posts.map { it.id })
        assertEquals(listOf("capture-other"), snapshot.captures.map { it.id })
        assertEquals(listOf("draft-other"), snapshot.drafts.map { it.id })
        assertEquals(setOf("other"), snapshot.settings.keys)
        assertTrue(snapshot.accountPins.isEmpty())
        assertEquals(listOf("save-unrelated"), snapshot.imageSaves.map { it.id })
        assertEquals(listOf("notification-unrelated"), snapshot.notifications.map { it.id })
        assertTrue(fixture.scheduler.cancelled.contains("draft-owner"))

        assertFalse(File(fixture.ownerPostImage).exists())
        assertFalse(File(fixture.ownerCaptureImage).exists())
        assertFalse(File(fixture.ownerEditedImage).exists())
        assertFalse(File(fixture.ownerPrivacyImage).exists())
        assertTrue(File(fixture.otherPostImage).exists())
        assertTrue(File(fixture.otherPrivacyImage).exists())
    }

    private fun withFixture(block: suspend (Fixture) -> Unit) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            File(context.cacheDir, "realme-delete-test-${UUID.randomUUID()}.preferences_pb")
        }
        val store = LocalAppStore(context, dataStore)
        val images = ImageFileStore(context)
        val clock = object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-11-15T12:00:00Z")
        }
        val auth = LocalAuthRepository(store, clock)
        val scheduler = FakeScheduler()
        try {
            runBlocking {
                block(Fixture(context, store, images, auth, scheduler))
            }
        } finally {
            scope.cancel()
        }
    }

    private class Fixture(
        context: Context,
        val store: LocalAppStore,
        val images: ImageFileStore,
        val auth: LocalAuthRepository,
        val scheduler: FakeScheduler,
    ) {
        val ownerPostImage = image(context)
        val ownerCaptureImage = image(context)
        val ownerEditedImage = image(context)
        val ownerPrivacyImage = image(context)
        val otherPostImage = image(context)
        val otherCaptureImage = image(context)
        val otherEditedImage = image(context)
        val otherPrivacyImage = image(context)
        val deletion = LocalAccountDeletionRepository(store, images, auth, scheduler)

        init {
            runBlocking {
                val now = Instant.parse("2026-11-15T10:00:00Z")
                val date = LocalDate.of(2026, 11, 15)
                val event = DailyEventFactory.forDate(date, now.minusSeconds(600))
                store.update { snapshot ->
                    snapshot.copy(
                        session = AuthSession("owner"),
                        profiles = listOf(UserProfile("owner", "Owner"), UserProfile("other", "Other")),
                        events = listOf(event),
                        posts = listOf(
                            post("post-owner", "owner", event.id, event.eventDateJst, ownerPostImage, now),
                            post("post-other", "other", event.id, event.eventDateJst, otherPostImage, now),
                        ),
                        captures = listOf(
                            Capture("capture-owner", "owner", event.id, ownerCaptureImage, now, true),
                            Capture("capture-other", "other", event.id, otherCaptureImage, now, true),
                        ),
                        drafts = listOf(
                            PostDraft(
                                id = "draft-owner",
                                userId = "owner",
                                eventId = event.id,
                                selectedCaptureId = "capture-owner",
                                firstCapturedAt = now,
                                autoPublishAt = now.plusSeconds(300),
                                editedImagePath = ownerEditedImage,
                            ),
                            PostDraft(
                                id = "draft-other",
                                userId = "other",
                                eventId = event.id,
                                selectedCaptureId = "capture-other",
                                firstCapturedAt = now,
                                autoPublishAt = now.plusSeconds(300),
                                editedImagePath = otherEditedImage,
                            ),
                        ),
                        settings = mapOf(
                            "owner" to UserSettings("owner", privacyImagePath = ownerPrivacyImage),
                            "other" to UserSettings("other", privacyImagePath = otherPrivacyImage),
                        ),
                        imageSaves = listOf(
                            ImageSave("save-owner-post", "post-owner", "other", now),
                            ImageSave("save-owner-as-saver", "post-other", "owner", now),
                            ImageSave("save-unrelated", "post-other", "other", now),
                        ),
                        notifications = listOf(
                            AppNotification("notification-owner", "owner", NotificationType.IMAGE_SAVED, "a", "a", "post-owner", "other", now),
                            AppNotification("notification-by-owner", "other", NotificationType.IMAGE_SAVED, "b", "b", "post-other", "owner", now),
                            AppNotification("notification-unrelated", "other", NotificationType.IMAGE_SAVED, "c", "c", "post-other", "other", now),
                        ),
                    )
                }
                auth.setAccountPin("owner", null, "1234").getOrThrow()
            }
        }

        private fun image(context: Context): String {
            val bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.rgb(30, 100, 160))
            val directory = File(context.filesDir, "delete-test").apply { mkdirs() }
            val file = File(directory, "${UUID.randomUUID()}.jpg")
            try {
                file.outputStream().use { output ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)
                }
            } finally {
                bitmap.recycle()
            }
            return file.absolutePath
        }

        private fun post(
            id: String,
            userId: String,
            eventId: String,
            date: LocalDate,
            path: String,
            now: Instant,
        ) = Post(
            id = id,
            userId = userId,
            eventId = eventId,
            eventDateJst = date,
            imagePath = path,
            imageType = ImageType.GAME,
            capturedAt = now,
            postedAt = now,
            status = PostStatus.ON_TIME,
        )
    }

    private class FakeScheduler : AutoPostScheduler {
        val cancelled = mutableListOf<String>()
        var failCancellation = false
        var interruptCancellation = false
        override fun schedule(draft: PostDraft) = Unit
        override fun cancel(draftId: String) {
            cancelled += draftId
        }
        override suspend fun cancelAndWait(draftId: String) {
            if (interruptCancellation) throw CancellationException("test interruption")
            if (failCancellation) throw IllegalStateException("test failure")
            cancel(draftId)
        }
    }
}
