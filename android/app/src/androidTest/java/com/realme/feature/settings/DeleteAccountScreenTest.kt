package com.realme.feature.settings

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.realme.core.design.RealMeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class DeleteAccountScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmationAndPinAreRequiredBeforeDeletion() {
        var submittedPin: String? = null
        composeRule.setContent {
            RealMeTheme {
                DeleteAccountScreen(
                    state = SettingsUiState(hasPin = true),
                    onBack = {},
                    onDelete = { submittedPin = it },
                    onClearMessage = {},
                )
            }
        }
        composeRule.onNodeWithTag("delete_confirmation").performTextInput("削除")
        composeRule.onNodeWithTag("delete_pin").performTextInput("1234")
        composeRule.onNodeWithTag("delete_account_button").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals("1234", submittedPin) }
    }

    @Test
    fun asciiDeleteConfirmationIsAcceptedWithoutPinWhenNoneIsSet() {
        var deleteRequested = false
        composeRule.setContent {
            RealMeTheme {
                DeleteAccountScreen(
                    state = SettingsUiState(hasPin = false),
                    onBack = {},
                    onDelete = { deleteRequested = true },
                    onClearMessage = {},
                )
            }
        }
        composeRule.onNodeWithTag("delete_confirmation").performTextInput("DELETE")
        composeRule.onNodeWithTag("delete_account_button").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(true, deleteRequested) }
    }
}
