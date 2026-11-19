package com.realme.feature.auth

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.realme.core.design.RealMeTheme
import com.realme.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun displayNameEnablesLocalSignIn() {
        var submittedName = ""
        composeRule.setContent {
            RealMeTheme {
                AuthScreen(
                    state = AuthUiState(isCheckingSession = false),
                    onSignIn = { submittedName = it },
                    onSelectAccount = {},
                    onClearError = {},
                )
            }
        }
        composeRule.onNodeWithTag("app_title").assertExists()
        composeRule.onNodeWithTag("display_name_input").performTextInput("Test Player")
        composeRule.onNodeWithTag("sign_in_button").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("Test Player", submittedName) }
    }

    @Test
    fun protectedAccountRequestsPinBeforeSignIn() {
        var submittedPin = ""
        composeRule.setContent {
            RealMeTheme {
                AuthScreen(
                    state = AuthUiState(
                        isCheckingSession = false,
                        localAccounts = listOf(UserProfile("account-one", "Player")),
                        pinProtectedAccountIds = setOf("account-one"),
                        pendingPinAccount = UserProfile("account-one", "Player"),
                    ),
                    onSignIn = {},
                    onSelectAccount = {},
                    onClearError = {},
                    onSubmitPin = { submittedPin = it },
                )
            }
        }
        composeRule.onNodeWithTag("pin_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("pin_input").performTextInput("123")
        composeRule.onNodeWithTag("pin_submit").assertIsNotEnabled()
        composeRule.onNodeWithTag("pin_input").performTextInput("4")
        composeRule.onNodeWithTag("pin_submit").performClick()
        composeRule.runOnIdle { assertEquals("1234", submittedPin) }
    }

    @Test
    fun existingAccountCanBeSelectedById() {
        var selected: String? = null
        composeRule.setContent {
            RealMeTheme {
                AuthScreen(
                    state = AuthUiState(isCheckingSession = false, localAccounts = listOf(UserProfile("account-one", "Player"))),
                    onSignIn = {},
                    onSelectAccount = { selected = it },
                    onClearError = {},
                )
            }
        }
        composeRule.onNodeWithTag("local_account_account-one").performClick()
        composeRule.runOnIdle { assertEquals("account-one", selected) }
    }
}
