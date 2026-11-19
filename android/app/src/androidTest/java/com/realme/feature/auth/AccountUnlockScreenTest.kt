package com.realme.feature.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.realme.core.design.RealMeTheme
import com.realme.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountUnlockScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validLengthPinCanBeSubmitted() {
        var submitted = ""
        composeRule.setContent {
            RealMeTheme {
                AccountUnlockScreen(
                    account = UserProfile("account", "Player"),
                    isSubmitting = false,
                    error = null,
                    onUnlock = { submitted = it },
                    onSignOut = {},
                )
            }
        }
        composeRule.onNodeWithTag("unlock_pin_input").performTextInput("1234")
        composeRule.onNodeWithTag("unlock_button").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("1234", submitted) }
    }
}
