package com.realme.core.model

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.min
import kotlin.random.Random

val JST: ZoneId = ZoneId.of("Asia/Tokyo")
val ON_TIME_WINDOW: Duration = Duration.ofMinutes(5)

object CaptureWindow {
    fun isOpen(event: DailyEvent, now: Instant, reminderDelivered: Boolean): Boolean =
        reminderDelivered && !now.isBefore(event.notificationAt) && now.isBefore(event.deadlineAt)
}

object DailyEventFactory {
    fun forDate(date: LocalDate, notificationAt: Instant? = null): DailyEvent {
        val minutesAfterSix = Random(date.toEpochDay() * 31L + 17L).nextInt(301)
        val scheduledAt = date.atTime(18, 0).plusMinutes(minutesAfterSix.toLong())
            .atZone(JST).toInstant()
        return DailyEvent(
            id = "local-$date",
            eventDateJst = date,
            notificationAt = notificationAt ?: scheduledAt,
            deadlineAt = date.plusDays(1).atStartOfDay(JST).toInstant(),
        )
    }
}

object PostTimingPolicy {
    fun resolveStatus(
        imageType: ImageType,
        capturedAt: Instant?,
        postedAt: Instant,
        event: DailyEvent,
    ): PostStatus {
        require(postedAt.isBefore(event.deadlineAt)) {
            "当日の投稿締切を過ぎています"
        }
        if (imageType == ImageType.PRIVACY) return PostStatus.PRIVACY

        requireNotNull(capturedAt) { "ゲーム画像の撮影時刻がありません" }
        require(!postedAt.isBefore(capturedAt)) { "投稿時刻が撮影時刻より前になっています" }
        require(capturedAt.isBefore(event.deadlineAt)) {
            "当日の撮影締切を過ぎています"
        }
        return if (!capturedAt.isAfter(event.notificationAt.plus(ON_TIME_WINDOW))) {
            PostStatus.ON_TIME
        } else {
            PostStatus.LATE
        }
    }
}

object AutoPostCalculator {
    fun initialDeadline(
        firstCapturedAt: Instant,
        settingMinutes: Int,
        event: DailyEvent,
    ): Instant = deadline(firstCapturedAt, settingMinutes, EditState.NOT_STARTED, event)

    fun onEditStarted(
        draft: PostDraft,
        editStartedAt: Instant,
        event: DailyEvent,
    ): Instant {
        val originalDeadline = deadline(
            draft.firstCapturedAt,
            draft.configuredMinutes,
            EditState.NOT_STARTED,
            event,
        )
        if (!editStartedAt.isBefore(originalDeadline)) return draft.autoPublishAt
        return deadline(draft.firstCapturedAt, draft.configuredMinutes, EditState.EDITING, event)
    }

    private fun deadline(
        firstCapturedAt: Instant,
        settingMinutes: Int,
        editState: EditState,
        event: DailyEvent,
    ): Instant {
        val allowedMinutes = if (editState == EditState.NOT_STARTED) 15 else 45
        val effectiveMinutes = min(settingMinutes, allowedMinutes).coerceAtLeast(1)
        val requested = firstCapturedAt.plus(Duration.ofMinutes(effectiveMinutes.toLong()))
        return if (requested.isBefore(event.deadlineAt)) requested else event.deadlineAt
    }
}

object StreakCalculator {
    fun calculate(posts: List<Post>, asOfDate: LocalDate): StreakStats {
        val dates = posts.asSequence()
            .map { it.eventDateJst }
            .filter { !it.isAfter(asOfDate) }
            .distinct()
            .sorted()
            .toList()
        if (dates.isEmpty()) return StreakStats(0, 0, 0)

        var best = 0
        var run = 0
        var previous: LocalDate? = null
        dates.forEach { date ->
            run = if (previous?.plusDays(1) == date) run + 1 else 1
            best = maxOf(best, run)
            previous = date
        }

        val latest = dates.last()
        val current = if (latest == asOfDate || latest == asOfDate.minusDays(1)) {
            var count = 1
            var cursor = latest
            while (dates.contains(cursor.minusDays(1))) {
                count++
                cursor = cursor.minusDays(1)
            }
            count
        } else {
            0
        }
        return StreakStats(current = current, best = best, total = dates.size)
    }
}
