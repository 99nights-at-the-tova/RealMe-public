package com.realme

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.runtime.mutableStateOf
import com.realme.data.local.DeletionCleanup
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeletionCleanupNoticeTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun failedCleanupOffersRetryAndCompletionCanBeAcknowledged() {
        var retried = false
        var dismissed = false
        val done = mutableStateOf(false)
        composeRule.setContent {
            DeletionCleanupNotice(
                DeletionCleanup("job", emptyList(), emptyList(), completed = done.value, failed = !done.value),
                onRetry = { retried = true },
                onDismiss = { dismissed = true },
            )
        }
        composeRule.onNodeWithTag("deletion_cleanup_notice").assertExists()
        composeRule.onNodeWithTag("cleanup_retry").performClick()
        composeRule.runOnIdle { assertTrue(retried); done.value = true }
        composeRule.onNodeWithTag("cleanup_dismiss").performClick()
        composeRule.runOnIdle { assertTrue(dismissed) }
    }
}
