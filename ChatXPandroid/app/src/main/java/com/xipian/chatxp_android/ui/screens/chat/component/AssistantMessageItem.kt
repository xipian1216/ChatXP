package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.screens.chat.component.markdown.ChatMarkdown
import com.xipian.chatxp_android.ui.token.AssistantMessageMaxWidthFraction

@Composable
fun AssistantMessageItem(
    assistantText: String,
    modifier: Modifier = Modifier,
    assistantMaxWidthFraction: Float = AssistantMessageMaxWidthFraction,
    showAssistantAvatar: Boolean = false,
    showAssistantBackground: Boolean = false
) {
    Box(
        modifier = modifier.fillMaxWidth(assistantMaxWidthFraction)
    ) {
        ChatMarkdown(content = assistantText)
    }
}

@Preview(name = "Assistant message light", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun AssistantMessageItemLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        AssistantMessageItem(
            assistantText = stringResource(R.string.chat_preview_assistant_message_long)
        )
    }
}

@Preview(name = "Assistant message dark", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun AssistantMessageItemDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        AssistantMessageItem(
            assistantText = stringResource(R.string.chat_preview_assistant_message_long)
        )
    }
}

@Preview(name = "Assistant Markdown light", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun AssistantMessageItemMarkdownLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        AssistantMessageItem(
            assistantText = stringResource(R.string.chat_preview_markdown_assistant)
        )
    }
}

@Preview(name = "Assistant Markdown dark", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun AssistantMessageItemMarkdownDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        AssistantMessageItem(
            assistantText = stringResource(R.string.chat_preview_markdown_assistant)
        )
    }
}
