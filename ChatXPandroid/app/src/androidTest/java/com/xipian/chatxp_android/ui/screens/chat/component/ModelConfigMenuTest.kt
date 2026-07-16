package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.xipian.chatxp_android.ui.screens.chat.ModelOption
import com.xipian.chatxp_android.ui.screens.chat.ReasoningModeUi
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ModelConfigMenuTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun modelAndReasoningSelectionsRemainAvailableInSameMenu() {
        var selectedModel: String? = null
        var selectedReasoningMode: ReasoningModeUi? = null
        composeRule.setContent {
            ChatXPandroidTheme {
                ModelConfigMenu(
                    modelOptions = modelOptions(),
                    selectedModelId = "chat-5.5",
                    selectedReasoningMode = ReasoningModeUi.STANDARD,
                    isCatalogLoaded = true,
                    isUpdating = false,
                    errorText = null,
                    onModelSelected = { selectedModel = it },
                    onReasoningModeSelected = { selectedReasoningMode = it }
                )
            }
        }

        composeRule.onNodeWithText("5.6").performClick()
        composeRule.onNodeWithText("进阶").performClick()

        assertEquals("chat-5.6", selectedModel)
        assertEquals(ReasoningModeUi.ADVANCED, selectedReasoningMode)
        composeRule.onNodeWithText("模型").assertIsDisplayed()
        composeRule.onNodeWithText("思考程度").assertIsDisplayed()
    }

    @Test
    fun unconfirmedCatalogOptionsAreDisabled() {
        composeRule.setContent {
            ChatXPandroidTheme {
                ModelConfigMenu(
                    modelOptions = modelOptions(),
                    selectedModelId = "chat-5.5",
                    selectedReasoningMode = ReasoningModeUi.STANDARD,
                    isCatalogLoaded = false,
                    isUpdating = false,
                    errorText = null,
                    onModelSelected = {},
                    onReasoningModeSelected = {}
                )
            }
        }

        composeRule.onNodeWithText("5.6").assertIsNotEnabled()
        composeRule.onNodeWithText("进阶").assertIsNotEnabled()
        composeRule.onNodeWithText("模型目录暂不可用").assertIsDisplayed()
    }
}

private fun modelOptions() = listOf(
    ModelOption(
        id = "chat-5.5",
        displayName = "5.5",
        reasoningModes = ReasoningModeUi.entries.toSet(),
        isDefault = true
    ),
    ModelOption(
        id = "chat-5.6",
        displayName = "5.6",
        reasoningModes = ReasoningModeUi.entries.toSet()
    )
)
