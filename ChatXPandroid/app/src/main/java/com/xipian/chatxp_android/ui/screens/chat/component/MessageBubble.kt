package com.xipian.chatxp_android.ui.screens.chat.component

import android.annotation.SuppressLint
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.screens.chat.component.markdown.ChatMarkdown
import com.xipian.chatxp_android.ui.theme.ChatCorner
import com.xipian.chatxp_android.ui.token.MessageBubbleHorizontalPadding
import com.xipian.chatxp_android.ui.token.MessageBubbleVerticalPadding
import com.xipian.chatxp_android.ui.token.Space4
import com.xipian.chatxp_android.ui.token.UserMessageMaxWidthFraction

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MessageBubble(
    messageText: String,
    modifier: Modifier = Modifier,
    isUserMessage: Boolean = true,
    userMessageMaxWidthFraction: Float = UserMessageMaxWidthFraction
) {
    val bodyStyle = if (isUserMessage) {
        MaterialTheme.typography.bodyMedium.copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both
            ),
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )
    } else {
        MaterialTheme.typography.bodyMedium
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (isUserMessage) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            modifier = Modifier.widthIn(max = maxWidth * userMessageMaxWidthFraction),
            shape = ChatCorner.Dialog,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Box(
                modifier = Modifier.padding(
                    horizontal = MessageBubbleHorizontalPadding,
                    vertical = MessageBubbleVerticalPadding
                ),
                contentAlignment = Alignment.Center
            ) {
                ChatMarkdown(
                    content = messageText,
                    bodyStyle = bodyStyle,
                    trimLeadingBlockSpacing = isUserMessage
                )
            }
        }
    }
}

@Composable
private fun MessageBubbleAlignmentPreviewContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(Space4),
        verticalArrangement = Arrangement.spacedBy(Space4)
    ) {
        MessageBubble(messageText = stringResource(R.string.chat_preview_user_message_short))
        MessageBubble(messageText = stringResource(R.string.chat_preview_user_message_mixed))
        MessageBubble(messageText = stringResource(R.string.chat_preview_user_message_multiline))
        MessageBubble(messageText = stringResource(R.string.chat_preview_user_message_markdown_inline))
        MessageBubble(messageText = stringResource(R.string.chat_preview_user_message_markdown_blocks))
    }
}

@Preview(name = "User message alignment light", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun MessageBubbleAlignmentLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        MessageBubbleAlignmentPreviewContent()
    }
}

@Preview(name = "User message alignment dark", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun MessageBubbleAlignmentDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        MessageBubbleAlignmentPreviewContent()
    }
}

@Preview(name = "User message light", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun MessageBubbleLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        MessageBubble(messageText = stringResource(R.string.chat_preview_user_message_long))
    }
}

@Preview(name = "User message dark", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun MessageBubbleDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        MessageBubble(messageText = stringResource(R.string.chat_preview_user_message_long))
    }
}

@Preview(name = "User Markdown light", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun MessageBubbleMarkdownLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        MessageBubble(messageText = stringResource(R.string.chat_preview_markdown_user))
    }
}

@Preview(name = "User Markdown dark", widthDp = 360, showBackground = true, showSystemUi = true)
@Composable
private fun MessageBubbleMarkdownDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        MessageBubble(messageText = stringResource(R.string.chat_preview_markdown_user))
    }
}
