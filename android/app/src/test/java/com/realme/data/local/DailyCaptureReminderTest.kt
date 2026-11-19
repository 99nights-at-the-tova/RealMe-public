package com.realme.data.local

import com.realme.core.model.JST
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class DailyCaptureReminderTest {
    @Test
    fun showsAbsoluteAutoPostTimeInJst() {
        val publishAt = LocalDateTime.of(2026, 11, 17, 18, 47).atZone(JST).toInstant()
        assertEquals(
            "18:47に自動投稿されます",
            DailyCaptureReminder.autoPostNotificationText(publishAt),
        )
    }

    @Test
    fun fallsBackWhenThereIsNoActiveDraft() {
        assertEquals(
            "画像を保存しました",
            DailyCaptureReminder.autoPostNotificationText(null),
        )
    }

    @Test
    fun explainsWhenTodayPostAlreadyExists() {
        assertEquals(
            "今日は投稿済みです",
            DailyCaptureReminder.autoPostNotificationText(null, alreadyPosted = true),
        )
    }
}
