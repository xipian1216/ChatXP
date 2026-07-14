package com.xipian.chatxp_android.ui.screens.chat.component.markdown

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.component.AssistantMessageItem
import com.xipian.chatxp_android.ui.screens.chat.component.MessageBubble
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatMarkdownTest {
    @get:Rule
    val composeRule = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun assistantMessageRendersMarkdownBlocks() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val markdown = context.getString(R.string.chat_preview_markdown_assistant)
        val heading = context.getString(R.string.chat_preview_markdown_heading)
        val linkLabel = context.getString(R.string.chat_preview_markdown_link_label)
        val codeLanguage = context.getString(R.string.markdown_code_language, "kotlin")
        val copyCode = context.getString(R.string.markdown_copy_code)

        composeRule.setContent {
            ChatXPandroidTheme {
                AssistantMessageItem(assistantText = markdown)
            }
        }

        composeRule.waitUntilAtLeastOneExists(hasText(heading))
        composeRule.onNodeWithText(heading).assertExists()
        composeRule.onNodeWithContentDescription(codeLanguage).assertExists()
        composeRule.onNodeWithContentDescription(copyCode).assertExists()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.CollectionInfo,
                CollectionInfo(rowCount = 3, columnCount = 2)
            )
        ).assertExists()
        composeRule.onNodeWithText(linkLabel).assertExists()
    }

    @Test
    fun userMessageRendersInlineMarkdown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val markdown = context.getString(R.string.chat_preview_markdown_user)
        val renderedText = context.getString(R.string.chat_preview_markdown_user_rendered)

        composeRule.setContent {
            ChatXPandroidTheme {
                MessageBubble(messageText = markdown)
            }
        }

        composeRule.onNodeWithText(renderedText).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun streamingContentUpdatesRenderedMarkdown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val start = context.getString(R.string.chat_preview_markdown_stream_start)
        val startRendered = context.getString(R.string.chat_preview_markdown_stream_start_rendered)
        val end = context.getString(R.string.chat_preview_markdown_stream_end)
        val endRendered = context.getString(R.string.chat_preview_markdown_stream_end_rendered)
        val content = mutableStateOf(start)

        composeRule.setContent {
            ChatXPandroidTheme {
                ChatMarkdown(content = content.value)
            }
        }

        composeRule.waitUntilAtLeastOneExists(hasText(startRendered))
        composeRule.runOnUiThread { content.value = end }
        composeRule.waitUntilAtLeastOneExists(hasText(endRendered))
        composeRule.onNodeWithText(endRendered).assertIsDisplayed()
    }
}
