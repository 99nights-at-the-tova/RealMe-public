package com.realme.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class StreakCalculatorTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `on time late and privacy all continue a streak`() {
        val posts = listOf(
            post(today.minusDays(2), PostStatus.ON_TIME),
            post(today.minusDays(1), PostStatus.LATE),
            post(today, PostStatus.PRIVACY),
        )
        assertEquals(StreakStats(current = 3, best = 3, total = 3), StreakCalculator.calculate(posts, today))
    }

    @Test
    fun `missing a day resets current but keeps best and total`() {
        val posts = listOf(
            post(today.minusDays(4), PostStatus.ON_TIME),
            post(today.minusDays(3), PostStatus.ON_TIME),
            post(today.minusDays(2), PostStatus.LATE),
            post(today, PostStatus.ON_TIME),
        )
        assertEquals(StreakStats(current = 1, best = 3, total = 4), StreakCalculator.calculate(posts, today))
    }

    @Test
    fun `yesterday streak remains current until a new post today`() {
        val posts = listOf(post(today.minusDays(2), PostStatus.ON_TIME), post(today.minusDays(1), PostStatus.LATE))
        assertEquals(2, StreakCalculator.calculate(posts, today).current)
    }

    @Test
    fun `old streak is zero after an unposted day`() {
        val posts = listOf(post(today.minusDays(2), PostStatus.ON_TIME))
        assertEquals(StreakStats(current = 0, best = 1, total = 1), StreakCalculator.calculate(posts, today))
    }

    @Test
    fun `duplicate posts on an event date count once`() {
        val posts = listOf(post(today, PostStatus.ON_TIME), post(today, PostStatus.PRIVACY))
        assertEquals(StreakStats(current = 1, best = 1, total = 1), StreakCalculator.calculate(posts, today))
    }

    @Test
    fun `capture or login is not a post and does not increase streak`() {
        assertEquals(StreakStats(0, 0, 0), StreakCalculator.calculate(emptyList(), today))
    }

    private fun post(date: LocalDate, status: PostStatus) = Post(
        id = "$date-$status-${Instant.EPOCH}",
        userId = "user",
        eventId = "event-$date",
        eventDateJst = date,
        imageType = if (status == PostStatus.PRIVACY) ImageType.PRIVACY else ImageType.GAME,
        capturedAt = null,
        postedAt = date.atTime(20, 0).atZone(JST).toInstant(),
        status = status,
    )
}
