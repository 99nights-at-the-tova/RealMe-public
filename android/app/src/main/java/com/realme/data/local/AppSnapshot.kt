package com.realme.data.local

import com.realme.core.model.AppNotification
import com.realme.core.model.AuthSession
import com.realme.core.model.Capture
import com.realme.core.model.ImageSave
import com.realme.core.model.LocalAccountPin
import com.realme.core.model.DailyEvent
import com.realme.core.model.Post
import com.realme.core.model.PostDraft
import com.realme.core.model.UserProfile
import com.realme.core.model.UserSettings
import kotlinx.serialization.Serializable

@Serializable
data class DeletionCleanup(
    val id: String,
    val imagePaths: List<String>,
    val draftIds: List<String>,
    val completed: Boolean = false,
    val failed: Boolean = false,
)

@Serializable
data class AppSnapshot(
    val schemaVersion: Int = 1,
    val session: AuthSession? = null,
    val profiles: List<UserProfile> = emptyList(),
    val events: List<DailyEvent> = emptyList(),
    val posts: List<Post> = emptyList(),
    val captures: List<Capture> = emptyList(),
    val drafts: List<PostDraft> = emptyList(),
    val settings: Map<String, UserSettings> = emptyMap(),
    val imageSaves: List<ImageSave> = emptyList(),
    val notifications: List<AppNotification> = emptyList(),
    val accountPins: List<LocalAccountPin> = emptyList(),
    val deletionCleanups: List<DeletionCleanup> = emptyList(),
    val deliveredCaptureReminders: Set<String> = emptySet(),
    val expiredCaptureCleanupPaths: Set<String> = emptySet(),
)
