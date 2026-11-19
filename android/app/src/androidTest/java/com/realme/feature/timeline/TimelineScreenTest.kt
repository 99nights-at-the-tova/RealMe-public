package com.realme.feature.timeline

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.realme.core.design.RealMeTheme
import com.realme.core.model.DailyEventFactory
import com.realme.core.model.ImageType
import com.realme.core.model.JST
import com.realme.core.model.Post
import com.realme.core.model.PostStatus
import com.realme.core.model.StreakStats
import com.realme.core.model.UserProfile
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TimelineScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun postShowsOwnerStatusAndStreak() {
        val today = LocalDate.of(2026, 11, 15)
        val notification = today.atTime(18, 0).atZone(JST).toInstant()
        val event = DailyEventFactory.forDate(today, notification)
        val post = Post(
            id = "post",
            userId = "user",
            eventId = event.id,
            eventDateJst = today,
            imageType = ImageType.GAME,
            capturedAt = notification,
            postedAt = notification.plusSeconds(30),
            status = PostStatus.ON_TIME,
            caption = "テストの一戦",
        )
        composeRule.setContent {
            RealMeTheme {
                TimelineScreen(
                    state = TimelineUiState(
                        isLoading = false,
                        entries = listOf(
                            TimelineEntry(
                                posts = listOf(post),
                                profile = UserProfile("user", "Test Player"),
                                streak = StreakStats(3, 4, 5),
                            ),
                        ),
                        currentUserId = "user",
                        event = event,
                        now = notification.plusSeconds(60),
                    ),
                    isDebug = false,
                    onCreatePost = {},
                    onStartDemo = {},
                    onDelete = {},
                    onReplaceWithPrivacy = {},
                    onClearMessage = {},
                    onRefreshClock = {},
                )
            }
        }
        composeRule.onNodeWithTag("timeline_list").assertIsDisplayed()
        composeRule.onNodeWithText("Test Player").assertIsDisplayed()
        composeRule.onNodeWithText("ON TIME").assertDoesNotExist()
        composeRule.onNodeWithText("3日連続・累計5日").assertExists()
    }
}

