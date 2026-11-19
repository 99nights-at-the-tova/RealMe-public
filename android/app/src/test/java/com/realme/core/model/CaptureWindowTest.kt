package com.realme.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class CaptureWindowTest {
    @Test fun buttonIsAvailableFromNotificationUntilDailyDeadline() {
        val start = Instant.parse("2026-11-16T09:00:00Z")
        val event = DailyEventFactory.forDate(LocalDate.of(2026, 11, 16), start)
        assertFalse(CaptureWindow.isOpen(event, start.minusMillis(1), true))
        assertFalse(CaptureWindow.isOpen(event, start, false))
        assertTrue(CaptureWindow.isOpen(event, start, true))
        assertTrue(CaptureWindow.isOpen(event, event.deadlineAt.minusMillis(1), true))
        assertFalse(CaptureWindow.isOpen(event, event.deadlineAt, true))
    }
}
