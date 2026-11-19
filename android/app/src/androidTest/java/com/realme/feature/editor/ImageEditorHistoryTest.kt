package com.realme.feature.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.realme.core.model.AuthSession
import com.realme.core.model.Post
import com.realme.core.model.PostDraft
import com.realme.core.model.UserProfile
import com.realme.core.util.TimeProvider
import com.realme.data.local.ImageFileStore
import com.realme.data.repository.AuthRepository
import com.realme.data.repository.PostRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ImageEditorHistoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun movingTextIsUndoneWithoutRemovingTheText() = runBlocking {
        val images = ImageFileStore(context)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val sourcePath = images.saveBitmap(bitmap, "editor-test")
        bitmap.recycle()
        val viewModel = ImageEditorViewModel(
            postRepository = EmptyPostRepository,
            authRepository = FixedAuthRepository,
            imageFiles = images,
            timeProvider = object : TimeProvider {
                override fun now(): Instant = Instant.parse("2026-11-15T10:00:00Z")
            },
        )
        try {
            withContext(Dispatchers.Main) { viewModel.load(sourcePath, null) }
            withTimeout(5_000) { viewModel.uiState.first { !it.isLoading } }
            withContext(Dispatchers.Main) { viewModel.addText("MOVE", 0.5f, 0.5f) }
            val added = withTimeout(5_000) {
                viewModel.uiState.first { it.config.texts.size == 1 && !it.isBusy }
            }
            val textId = added.config.texts.single().id

            withContext(Dispatchers.Main) { viewModel.moveText(textId, 0.2f, 0.8f) }
            assertEquals(0.2f, viewModel.uiState.value.config.texts.single().xFraction, 0.001f)
            assertTrue(viewModel.uiState.value.canUndo)

            withContext(Dispatchers.Main) { viewModel.undo() }
            val undone = viewModel.uiState.value
            assertEquals(1, undone.config.texts.size)
            assertEquals(0.5f, undone.config.texts.single().xFraction, 0.001f)
            assertEquals(0.5f, undone.config.texts.single().yFraction, 0.001f)
        } finally {
            images.delete(sourcePath)
        }
    }

    @Test
    fun cropEffectAndStampTransformsCanBeUndone() = runBlocking {
        val images = ImageFileStore(context)
        val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        val sourcePath = images.saveBitmap(bitmap, "editor-test")
        bitmap.recycle()
        val viewModel = ImageEditorViewModel(EmptyPostRepository, FixedAuthRepository, images, object : TimeProvider {
            override fun now(): Instant = Instant.parse("2026-11-15T10:00:00Z")
        })
        suspend fun waitFor(predicate: (ImageEditorUiState) -> Boolean) =
            withTimeout(5_000) { viewModel.uiState.first { !it.isBusy && predicate(it) } }
        try {
            withContext(Dispatchers.Main) { viewModel.load(sourcePath, null) }
            waitFor { !it.isLoading }
            val crop = NormalizedRect(0.1f, 0.2f, 0.8f, 0.9f)
            withContext(Dispatchers.Main) { viewModel.setFreeCrop(crop) }
            waitFor { it.config.freeCrop == crop }
            withContext(Dispatchers.Main) { viewModel.setEffect(EditorEffect.MOSAIC) }
            waitFor { it.config.effect == EditorEffect.MOSAIC }
            val region = NormalizedRect(0.2f, 0.1f, 0.7f, 0.8f)
            withContext(Dispatchers.Main) { viewModel.setEffectRect(region) }
            waitFor { it.config.effectRect == region }
            withContext(Dispatchers.Main) { viewModel.addStamp("★", 0.5f, 0.5f) }
            waitFor { it.config.stamps.size == 1 }
            withContext(Dispatchers.Main) { viewModel.scaleLastStamp(1.5f) }
            waitFor { it.config.stamps.single().scale == 1.5f }
            withContext(Dispatchers.Main) { viewModel.rotateLastStamp(15f) }
            waitFor { it.config.stamps.single().rotation == 15f }
            withContext(Dispatchers.Main) { viewModel.undo() }
            assertEquals(0f, viewModel.uiState.value.config.stamps.single().rotation, 0.001f)
            withContext(Dispatchers.Main) { viewModel.undo() }
            assertEquals(1f, viewModel.uiState.value.config.stamps.single().scale, 0.001f)
            withContext(Dispatchers.Main) { viewModel.undo() }
            assertTrue(viewModel.uiState.value.config.stamps.isEmpty())
            withContext(Dispatchers.Main) { viewModel.undo() }
            waitFor { it.config.effectRect == NormalizedRect() }
            withContext(Dispatchers.Main) { viewModel.undo() }
            waitFor { it.config.effect == EditorEffect.NONE }
            withContext(Dispatchers.Main) { viewModel.undo() }
            val initial = waitFor { it.config == EditorConfig() }
            assertTrue(!initial.canUndo)
        } finally { images.delete(sourcePath) }
    }

    private object FixedAuthRepository : AuthRepository {
        override val session: Flow<AuthSession?> = flowOf(AuthSession("user"))
        override val localAccounts: Flow<List<UserProfile>> = flowOf(emptyList())
        override fun observeProfile(userId: String): Flow<UserProfile?> = flowOf(null)
        override suspend fun signInAnonymously(displayName: String): Result<UserProfile> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signInExisting(userId: String): Result<UserProfile> =
            Result.failure(UnsupportedOperationException())
        override suspend fun signOut() = Unit
    }

    private object EmptyPostRepository : PostRepository {
        override val posts: Flow<List<Post>> = flowOf(emptyList())
        override val drafts: Flow<List<PostDraft>> = flowOf(emptyList())
        override suspend fun createGamePost(
            userId: String,
            imagePath: String,
            capturedAt: Instant,
            caption: String,
            now: Instant,
        ): Result<Post> = unsupported()
        override suspend fun createPrivacyPost(
            userId: String,
            imagePath: String,
            caption: String,
            now: Instant,
        ): Result<Post> = unsupported()
        override suspend fun replaceWithPrivacy(userId: String, postId: String): Result<Post> = unsupported()
        override suspend fun deletePost(userId: String, postId: String): Result<Unit> = unsupported()
        override suspend fun selectDraftCapture(userId: String, captureId: String): Result<PostDraft> = unsupported()
        override suspend fun beginDraftEditing(
            userId: String,
            captureId: String,
            now: Instant,
        ): Result<PostDraft?> = Result.success(null)
        override suspend fun markDraftReady(
            userId: String,
            draftId: String,
            editedImagePath: String,
            expectedCaptureId: String?,
        ): Result<PostDraft> = unsupported()
        override suspend fun publishDraft(draftId: String, now: Instant): Result<Post> = unsupported()
        override suspend fun cancelDraft(userId: String, draftId: String): Result<Unit> = unsupported()
        override suspend fun getDraft(draftId: String): PostDraft? = null
        private fun <T> unsupported(): Result<T> = Result.failure(UnsupportedOperationException())
    }
}
