package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThreadItemTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longPressShowsActionsInExpectedOrder() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val rename = context.getString(R.string.thread_action_rename)
        val pin = context.getString(R.string.thread_action_pin)
        val delete = context.getString(R.string.thread_action_delete)

        composeRule.setContent {
            ChatXPandroidTheme {
                ThreadItem(
                    title = "Session",
                    isPinned = false,
                    isSelected = false,
                    isDeleteEnabled = true,
                    onClick = {},
                    onRenameRequest = {},
                    onTogglePin = {},
                    onDeleteRequest = {}
                )
            }
        }

        composeRule.onNodeWithText("Session").performTouchInput { longClick() }

        val renameNode = composeRule.onNodeWithText(rename).assertIsDisplayed().fetchSemanticsNode()
        val pinNode = composeRule.onNodeWithText(pin).assertIsDisplayed().fetchSemanticsNode()
        val deleteNode = composeRule.onNodeWithText(delete).assertIsDisplayed().fetchSemanticsNode()
        assertTrue(renameNode.boundsInRoot.top < pinNode.boundsInRoot.top)
        assertTrue(pinNode.boundsInRoot.top < deleteNode.boundsInRoot.top)
    }
}
