package com.realme.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class AutoPostCalculatorTest {
    private val date = LocalDate.of(2026, 11, 15)
    private val notification = date.atTime(18, 0).atZone(JST).toInstant()
    private val event = DailyEventFactory.forDate(date, notification)
    private val firstCapture = notification.plusSeconds(60)

    @Test
    fun `selected minutes are used below the cap`() {
        assertEquals(
            firstCapture.plusSeconds(300),
            AutoPostCalculator.initialDeadline(firstCapture, 5, event),
        )
    }

    @Test
    fun `unedited draft is capped at fifteen minutes`() {
        assertEquals(
            firstCapture.plusSeconds(900),
            AutoPostCalculator.initialDeadline(firstCapture, 30, event),
        )
    }

    @Test
    fun `editing before original deadline can extend to forty five minutes`() {
        val draft = draft(autoPublishAt = firstCapture.plusSeconds(900))
        assertEquals(
            firstCapture.plusSeconds(2700),
            AutoPostCalculator.onEditStarted(draft, firstCapture.plusSeconds(600), event),
        )
    }

    @Test
    fun `editing after original deadline cannot resurrect or extend draft`() {
        val original = firstCapture.plusSeconds(900)
        val draft = draft(autoPublishAt = original)
        assertEquals(
            original,
            AutoPostCalculator.onEditStarted(draft, original, event),
        )
    }

    @Test
    fun `deadline never crosses next midnight JST`() {
        val nearMidnight = event.deadlineAt.minusSeconds(120)
        assertEquals(
            event.deadlineAt,
            AutoPostCalculator.initialDeadline(nearMidnight, 15, event),
        )
    }

    private fun draft(autoPublishAt: Instant) = PostDraft(
        id = "draft",
        userId = "user",
        eventId = event.id,
        selectedCaptureId = "capture",
        configuredMinutes = 45,
        firstCapturedAt = firstCapture,
        autoPublishAt = autoPublishAt,
    )
}
