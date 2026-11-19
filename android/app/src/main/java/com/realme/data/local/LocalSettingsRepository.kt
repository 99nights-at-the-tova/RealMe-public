package com.realme.data.local

import com.realme.core.model.UserSettings
import com.realme.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant

class LocalSettingsRepository(
    private val store: LocalAppStore,
    private val images: ImageFileStore,
) : SettingsRepository {
    override fun observeSettings(userId: String): Flow<UserSettings> = store.snapshots.map {
        it.settings[userId] ?: UserSettings(userId = userId)
    }

    override suspend fun getSettings(userId: String): UserSettings =
        store.current().settings[userId] ?: UserSettings(userId = userId)

    override suspend fun setAutoPostMinutes(userId: String, minutes: Int): Result<UserSettings> = repositoryResult {
        require(minutes in AUTO_POST_CHOICES) { "自動投稿時間の設定値が不正です" }
        update(userId) { it.copy(autoPostMinutes = minutes) }
    }

    override suspend fun setOverlayAppearance(
        userId: String,
        sizeDp: Int,
        opacity: Float,
    ): Result<UserSettings> = repositoryResult {
        update(userId) {
            it.copy(
                overlaySizeDp = sizeDp.coerceIn(48, 96),
                overlayOpacity = opacity.coerceIn(0.25f, 1f),
            )
        }
    }

    override suspend fun setOverlayPosition(userId: String, x: Int, y: Int) {
        update(userId) { it.copy(overlayX = x, overlayY = y) }
    }

    override suspend fun setPrivacyImage(userId: String, imagePath: String): Result<UserSettings> = repositoryResult {
        require(images.exists(imagePath)) { "プライバシー画像が見つかりません" }
        update(userId) { it.copy(privacyImagePath = imagePath) }
    }

    override suspend fun touchLastActive(userId: String, now: Instant) {
        runCatching { update(userId) { it.copy(lastActiveAt = now) } }
    }

    private suspend fun update(
        userId: String,
        transform: (UserSettings) -> UserSettings,
    ): UserSettings {
        var updated = UserSettings(userId)
        store.update { snapshot ->
            require(snapshot.profiles.any { it.id == userId && !it.isDemo }) { "ローカルアカウントが見つかりません" }
            updated = transform(snapshot.settings[userId] ?: UserSettings(userId))
            snapshot.copy(settings = snapshot.settings + (userId to updated))
        }
        return updated
    }

    companion object {
        val AUTO_POST_CHOICES = listOf(1, 3, 5, 10, 15, 30, 45)
    }
}
