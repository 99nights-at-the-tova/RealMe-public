package com.realme.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class PostTimingPolicyTest {
    private val date = LocalDate.of(2026, 11, 15)
    private val notification = date.atTime(18, 0).atZone(JST).toInstant()
    private val event = DailyEventFactory.forDate(date, notification)

    @Test
    fun `notification and exactly five minutes are on time`() {
        assertEquals(
            PostStatus.ON_TIME,
            status(capturedAt = notification, postedAt = notification),
        )
        assertEquals(
            PostStatus.ON_TIME,
            status(capturedAt = notification.plusSeconds(300), postedAt = notification.plusSeconds(600)),
        )
    }

    @Test
    fun `after five minutes is late but can still be posted`() {
        assertEquals(
            PostStatus.LATE,
            status(capturedAt = notification.plusSeconds(301), postedAt = notification.plusSeconds(400)),
        )
        val justBeforeMidnight = event.deadlineAt.minusSeconds(1)
        assertEquals(
            PostStatus.LATE,
            status(capturedAt = justBeforeMidnight, postedAt = justBeforeMidnight),
        )
    }

    @Test
    fun `capture before notification can be posted as on time`() {
        assertEquals(
            PostStatus.ON_TIME,
            status(capturedAt = notification.minusSeconds(1), postedAt = notification),
        )
        assertEquals(
            PostStatus.ON_TIME,
            status(capturedAt = notification.minusSeconds(3600), postedAt = notification),
        )
    }

    @Test
    fun `new post at next midnight is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            status(capturedAt = notification, postedAt = event.deadlineAt)
        }
        assertThrows(IllegalArgumentException::class.java) {
            status(capturedAt = event.deadlineAt, postedAt = event.deadlineAt)
        }
    }

    @Test
    fun `privacy is separate and can be used before notification`() {
        val beforeNotification = notification.minusSeconds(3600)
        assertEquals(
            PostStatus.PRIVACY,
            PostTimingPolicy.resolveStatus(ImageType.PRIVACY, null, beforeNotification, event),
        )
    }

    @Test
    fun `daily event is deterministic and uses inclusive 18 to 23 window`() {
        (0..60).forEach { offset ->
            val candidate = DailyEventFactory.forDate(date.plusDays(offset.toLong()))
            assertEquals(candidate, DailyEventFactory.forDate(candidate.eventDateJst))
            val localTime = candidate.notificationAt.atZone(JST).toLocalTime()
            assert(localTime.hour in 18..23)
            assertEquals(candidate.eventDateJst.plusDays(1), candidate.deadlineAt.atZone(JST).toLocalDate())
        }
    }

    private fun status(capturedAt: Instant, postedAt: Instant): PostStatus =
        PostTimingPolicy.resolveStatus(ImageType.GAME, capturedAt, postedAt, event)
}
