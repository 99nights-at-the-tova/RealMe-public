package com.realme.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.LocalDate

object InstantSerializer : KSerializer<Instant> {
    override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

object LocalDateSerializer : KSerializer<LocalDate> {
    override val descriptor = PrimitiveSerialDescriptor("LocalDate", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: LocalDate) = encoder.encodeString(value.toString())
    override fun deserialize(decoder: Decoder): LocalDate = LocalDate.parse(decoder.decodeString())
}

@Serializable
data class AuthSession(val userId: String)

@Serializable
data class UserProfile(
    val id: String,
    val displayName: String,
    val bio: String = "",
    val isDemo: Boolean = false,
)

@Serializable
data class LocalAccountPin(
    val userId: String,
    val pinHash: String,
    val salt: String,
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant,
    val failedAttempts: Int = 0,
    @Serializable(with = InstantSerializer::class)
    val lockedUntil: Instant? = null,
)

@Serializable
data class DailyEvent(
    val id: String,
    @Serializable(with = LocalDateSerializer::class)
    val eventDateJst: LocalDate,
    @Serializable(with = InstantSerializer::class)
    val notificationAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val deadlineAt: Instant,
)

@Serializable
enum class ImageType { GAME, PRIVACY }

@Serializable
enum class PostStatus { ON_TIME, LATE, PRIVACY }

@Serializable
data class Post(
    val id: String,
    val userId: String,
    val eventId: String,
    @Serializable(with = LocalDateSerializer::class)
    val eventDateJst: LocalDate,
    val imagePath: String? = null,
    val imageType: ImageType,
    @Serializable(with = InstantSerializer::class)
    val capturedAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val postedAt: Instant,
    val status: PostStatus,
    val caption: String = "",
    val isDemo: Boolean = false,
)

@Serializable
data class Capture(
    val id: String,
    val userId: String,
    val eventId: String,
    val filePath: String,
    @Serializable(with = InstantSerializer::class)
    val capturedAt: Instant,
    val eligible: Boolean,
)

@Serializable
enum class EditState { NOT_STARTED, EDITING, READY, FAILED }

@Serializable
enum class DraftStatus { ACTIVE, PUBLISHED, CANCELLED, FAILED }

@Serializable
data class PostDraft(
    val id: String,
    val userId: String,
    val eventId: String,
    val selectedCaptureId: String,
    val configuredMinutes: Int = 5,
    @Serializable(with = InstantSerializer::class)
    val firstCapturedAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val autoPublishAt: Instant,
    val editState: EditState = EditState.NOT_STARTED,
    val editedImagePath: String? = null,
    val status: DraftStatus = DraftStatus.ACTIVE,
)

@Serializable
data class UserSettings(
    val userId: String,
    val autoPostMinutes: Int = 5,
    val overlaySizeDp: Int = 64,
    val overlayOpacity: Float = 0.85f,
    val overlayX: Int = 24,
    val overlayY: Int = 240,
    val privacyImagePath: String? = null,
    val randomNotificationEnabled: Boolean = true,
    @Serializable(with = InstantSerializer::class)
    val lastActiveAt: Instant? = null,
    @Serializable(with = InstantSerializer::class)
    val lastInvitationAt: Instant? = null,
)

data class StreakStats(
    val current: Int,
    val best: Int,
    val total: Int,
)

@Serializable
data class ImageSave(
    val id: String,
    val postId: String,
    val savedByUserId: String,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
)

@Serializable
enum class NotificationType { IMAGE_SAVED }

@Serializable
data class AppNotification(
    val id: String,
    val userId: String,
    val type: NotificationType,
    val title: String,
    val message: String,
    val postId: String? = null,
    val actorUserId: String? = null,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant,
    @Serializable(with = InstantSerializer::class)
    val readAt: Instant? = null,
)
