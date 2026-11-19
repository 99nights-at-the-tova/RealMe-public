package com.realme.data.local

import com.realme.core.model.AuthSession
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.ImageType
import com.realme.core.model.LocalAccountPin
import com.realme.core.model.Post
import com.realme.core.model.PostStatus
import com.realme.core.model.UserProfile
import com.realme.core.model.UserSettings
import com.realme.core.security.LocalPinHasher
import com.realme.core.util.TimeProvider
import com.realme.data.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.util.UUID

class LocalAuthRepository(
    private val store: LocalAppStore,
    private val timeProvider: TimeProvider,
) : AuthRepository {
    override val session: Flow<AuthSession?> = store.snapshots.map { it.session }
    override val localAccounts: Flow<List<UserProfile>> = store.snapshots.map { snapshot ->
        snapshot.profiles.filterNot { it.isDemo }
    }
    override val pinProtectedAccountIds: Flow<Set<String>> = store.snapshots.map { snapshot ->
        snapshot.accountPins.map { it.userId }.toSet()
    }

    override fun observeProfile(userId: String): Flow<UserProfile?> =
        store.snapshots.map { snapshot -> snapshot.profiles.firstOrNull { it.id == userId } }

    override suspend fun signInAnonymously(displayName: String): Result<UserProfile> = repositoryResult {
        val cleanName = displayName.trim()
        require(cleanName.isNotEmpty()) { "表示名を入力してください" }
        require(cleanName.length <= 24) { "表示名は24文字以内で入力してください" }
        val profile = UserProfile(id = UUID.randomUUID().toString(), displayName = cleanName)
        store.update { current ->
            val withDemo = seedDemoData(current)
            withDemo.copy(
                session = AuthSession(profile.id),
                profiles = withDemo.profiles.filterNot { it.id == profile.id } + profile,
                settings = withDemo.settings + (profile.id to UserSettings(userId = profile.id)),
            )
        }
        profile
    }

    override suspend fun signOut() {
        store.update { it.copy(session = null) }
    }

    override suspend fun signInExisting(userId: String): Result<UserProfile> =
        signInExisting(userId, null)

    override suspend fun signInExisting(userId: String, pin: String?): Result<UserProfile> = repositoryResult {
        val snapshot = store.current()
        val pinRecord = snapshot.accountPins.firstOrNull { it.userId == userId }
        if (pinRecord == null) {
            var selected: UserProfile? = null
            store.update { latest ->
                require(latest.accountPins.none { it.userId == userId }) {
                    "PINが設定されました。もう一度お試しください"
                }
                val profile = latest.profiles.firstOrNull { it.id == userId && !it.isDemo }
                requireNotNull(profile) { "ローカルアカウントが見つかりません" }
                selected = profile
                latest.copy(session = AuthSession(profile.id))
            }
            return@repositoryResult requireNotNull(selected)
        }

        requireNotNull(pin) { "このアカウントはPINで保護されています" }
        val now = timeProvider.now()
        activePinLockMessage(pinRecord, now)?.let { throw IllegalArgumentException(it) }
        val pinMatches = withContext(Dispatchers.Default) {
            LocalPinHasher.verify(pin, pinRecord.salt, pinRecord.pinHash)
        }
        var failureMessage: String? = null
        var selected: UserProfile? = null
        store.update { latest ->
            val latestPin = latest.accountPins.firstOrNull { it.userId == userId }
            require(latestPin == pinRecord) {
                "PINが変更されました。もう一度お試しください"
            }
            requireNotNull(latestPin)
            if (!pinMatches) {
                val failed = registerPinFailure(latestPin, now)
                failureMessage = failed.second
                latest.copy(
                    accountPins = latest.accountPins.filterNot { it.userId == userId } + failed.first,
                )
            } else {
                val profile = latest.profiles.firstOrNull { it.id == userId && !it.isDemo }
                requireNotNull(profile) { "ローカルアカウントが見つかりません" }
                selected = profile
                latest.copy(
                    session = AuthSession(profile.id),
                    accountPins = latest.accountPins.filterNot { it.userId == userId } +
                        latestPin.copy(failedAttempts = 0, lockedUntil = null),
                )
            }
        }
        failureMessage?.let { throw IllegalArgumentException(it) }
        requireNotNull(selected)
    }

    override suspend fun requiresPin(userId: String): Boolean =
        store.current().accountPins.any { it.userId == userId }

    override suspend fun verifyPin(userId: String, pin: String): Result<Unit> = repositoryResult {
        val pinRecord = store.current().accountPins.firstOrNull { it.userId == userId }
            ?: throw IllegalStateException("このアカウントにはPINが設定されていません")
        val now = timeProvider.now()
        activePinLockMessage(pinRecord, now)?.let { throw IllegalArgumentException(it) }
        val matches = withContext(Dispatchers.Default) {
            LocalPinHasher.verify(pin, pinRecord.salt, pinRecord.pinHash)
        }
        var failureMessage: String? = null
        store.update { latest ->
            val latestPin = latest.accountPins.firstOrNull { it.userId == userId }
            require(latestPin == pinRecord) { "PINが変更されました。もう一度お試しください" }
            requireNotNull(latestPin)
            val replacement = if (matches) {
                latestPin.copy(failedAttempts = 0, lockedUntil = null)
            } else {
                registerPinFailure(latestPin, now).also { failureMessage = it.second }.first
            }
            latest.copy(
                accountPins = latest.accountPins.filterNot { it.userId == userId } + replacement,
            )
        }
        failureMessage?.let { throw IllegalArgumentException(it) }
    }

    override suspend fun setAccountPin(
        userId: String,
        currentPin: String?,
        newPin: String?,
    ): Result<Unit> = repositoryResult {
        val current = store.current()
        require(current.profiles.any { it.id == userId && !it.isDemo }) {
            "ローカルアカウントが見つかりません"
        }
        var existing = current.accountPins.firstOrNull { it.userId == userId }
        if (existing != null) {
            requireNotNull(currentPin) { "現在のPINを入力してください" }
            val verifiedPin = existing
            verifyPin(userId, currentPin).getOrThrow()
            existing = store.current().accountPins.firstOrNull { it.userId == userId }
            require(existing != null && existing.salt == verifiedPin.salt && existing.pinHash == verifiedPin.pinHash) {
                "PINが変更されました。もう一度お試しください"
            }
        }
        val replacement = if (newPin.isNullOrBlank()) {
            null
        } else {
            LocalPinHasher.validatePin(newPin)
            val credentials = withContext(Dispatchers.Default) { LocalPinHasher.create(newPin) }
            LocalAccountPin(
                userId = userId,
                pinHash = credentials.hash,
                salt = credentials.salt,
                updatedAt = timeProvider.now(),
            )
        }
        store.update { snapshot ->
            require(snapshot.profiles.any { it.id == userId && !it.isDemo }) {
                "ローカルアカウントが見つかりません"
            }
            require(snapshot.accountPins.firstOrNull { it.userId == userId } == existing) {
                "PINが変更されました。もう一度お試しください"
            }
            existing?.let { activePinLockMessage(it, timeProvider.now()) }?.let {
                throw IllegalArgumentException(it)
            }
            snapshot.copy(
                accountPins = snapshot.accountPins.filterNot { it.userId == userId } +
                    listOfNotNull(replacement),
            )
        }
        return@repositoryResult Unit
    }

    private fun activePinLockMessage(pin: LocalAccountPin, now: java.time.Instant): String? {
        val lockedUntil = pin.lockedUntil ?: return null
        if (!now.isBefore(lockedUntil)) return null
        val seconds = Duration.between(now, lockedUntil).seconds.coerceAtLeast(1)
        return "PINの試行回数が上限に達しました。あと${seconds}秒お待ちください"
    }

    private fun registerPinFailure(
        pin: LocalAccountPin,
        now: java.time.Instant,
    ): Pair<LocalAccountPin, String> {
        val previousAttempts = if (pin.lockedUntil != null && !now.isBefore(pin.lockedUntil)) {
            0
        } else {
            pin.failedAttempts
        }
        val attempts = previousAttempts + 1
        return if (attempts >= MAX_PIN_ATTEMPTS) {
            pin.copy(
                failedAttempts = MAX_PIN_ATTEMPTS,
                lockedUntil = now.plus(PIN_LOCK_DURATION),
            ) to "PINを${MAX_PIN_ATTEMPTS}回間違えました。30秒後に再試行してください"
        } else {
            pin.copy(failedAttempts = attempts, lockedUntil = null) to
                "PINが正しくありません（残り${MAX_PIN_ATTEMPTS - attempts}回）"
        }
    }

    private fun seedDemoData(snapshot: AppSnapshot): AppSnapshot {
        if (snapshot.profiles.any { it.isDemo }) return snapshot
        val today = timeProvider.todayJst()
        val dates = listOf(today.minusDays(2), today.minusDays(1), today)
        val events = dates.map { DailyEventFactory.forDate(it) }
        val aoi = UserProfile("demo-aoi", "Aoi", "夜のランク戦が好き", isDemo = true)
        val ren = UserProfile("demo-ren", "Ren", "今日も一勝だけ", isDemo = true)
        val demoPosts = buildList {
            dates.forEachIndexed { index, date ->
                val event = events[index]
                val aoiType = if (index == 0) ImageType.PRIVACY else ImageType.GAME
                val aoiCapture = if (aoiType == ImageType.PRIVACY) null else event.notificationAt.plusSeconds(600)
                add(
                    Post(
                        id = "demo-aoi-$date",
                        userId = aoi.id,
                        eventId = event.id,
                        eventDateJst = date,
                        imageType = aoiType,
                        capturedAt = aoiCapture,
                        postedAt = (aoiCapture ?: event.notificationAt).plusSeconds(60),
                        status = if (aoiType == ImageType.PRIVACY) PostStatus.PRIVACY else PostStatus.LATE,
                        caption = when (index) {
                            0 -> "今日はゲームをお休み"
                            1 -> "ぎりぎりの一戦"
                            else -> "ラストマッチ、勝った！"
                        },
                        isDemo = true,
                    ),
                )
                val renCapture = event.notificationAt.plusSeconds(120)
                add(
                    Post(
                        id = "demo-ren-$date",
                        userId = ren.id,
                        eventId = event.id,
                        eventDateJst = date,
                        imageType = ImageType.GAME,
                        capturedAt = renCapture,
                        postedAt = renCapture.plusSeconds(45),
                        status = PostStatus.ON_TIME,
                        caption = if (index == 2) "新マップ探索中" else "今日の一戦",
                        isDemo = true,
                    ),
                )
            }
        }
        return snapshot.copy(
            profiles = snapshot.profiles + listOf(aoi, ren),
            events = (snapshot.events + events).distinctBy { it.id },
            posts = snapshot.posts + demoPosts,
        )
    }

    private companion object {
        const val MAX_PIN_ATTEMPTS = 5
        val PIN_LOCK_DURATION: Duration = Duration.ofSeconds(30)
    }
}
