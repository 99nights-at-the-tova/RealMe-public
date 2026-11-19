package com.realme.data.local

import com.realme.core.model.UserProfile
import com.realme.data.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LocalProfileRepository(private val store: LocalAppStore) : ProfileRepository {
    override val profiles: Flow<List<UserProfile>> = store.snapshots.map { it.profiles }

    override suspend fun updateProfile(
        userId: String,
        displayName: String,
        bio: String,
    ): Result<UserProfile> = repositoryResult {
        val cleanName = displayName.trim()
        require(cleanName.isNotEmpty()) { "表示名を入力してください" }
        require(cleanName.length <= 24) { "表示名は24文字以内で入力してください" }
        require(bio.length <= 160) { "自己紹介は160文字以内で入力してください" }
        lateinit var updated: UserProfile
        store.update { snapshot ->
            val existing = snapshot.profiles.firstOrNull { it.id == userId }
            requireNotNull(existing) { "プロフィールが見つかりません" }
            updated = existing.copy(displayName = cleanName, bio = bio.trim())
            snapshot.copy(profiles = snapshot.profiles.filterNot { it.id == userId } + updated)
        }
        updated
    }
}
