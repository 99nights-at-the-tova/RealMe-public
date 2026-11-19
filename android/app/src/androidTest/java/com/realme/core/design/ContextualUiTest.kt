package com.realme.core.design

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.realme.core.model.PostStatus
import org.junit.Rule
import org.junit.Test

class ContextualUiTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun explanationAppearsOnlyAfterOpening() {
        composeRule.setContent { RealMeTheme { ExplanationButton("連続記録", "投稿した日で計算します。") } }
        composeRule.onNodeWithText("投稿した日で計算します。").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("連続記録 の説明").performClick()
        composeRule.onNodeWithText("投稿した日で計算します。").assertExists()
        composeRule.onNodeWithText("閉じる").performClick()
        composeRule.onNodeWithText("投稿した日で計算します。").assertDoesNotExist()
    }

    @Test fun lateIsMarkedAndOnTimeIsNot() {
        composeRule.setContent { RealMeTheme { StatusBadge(PostStatus.LATE) } }
        composeRule.onNodeWithContentDescription("遅刻した投稿").assertExists()
        composeRule.onNodeWithText("LATE").assertDoesNotExist()
    }
}
