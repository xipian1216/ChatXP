package com.xipian.chatxp_android.ui.screens.chat.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.xipian.chatxp_android.ui.screens.chat.ChatMessage
import com.xipian.chatxp_android.ui.screens.chat.preview.ChatPreviewFrame
import com.xipian.chatxp_android.ui.screens.chat.preview.previewMarkdownMessages
import com.xipian.chatxp_android.ui.screens.chat.preview.previewMessages
import com.xipian.chatxp_android.ui.token.MessageItemSpacing
import com.xipian.chatxp_android.ui.token.MessageListBottomPadding
import com.xipian.chatxp_android.ui.token.MessageListHorizontalPadding
import com.xipian.chatxp_android.ui.token.MessageListTopPadding

@Composable
fun MessageList(
    messages: List<ChatMessage>,
    showEmptyState: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        start = MessageListHorizontalPadding,
        top = MessageListTopPadding,
        end = MessageListHorizontalPadding,
        bottom = MessageListBottomPadding
    ),
    messageSpacing: Dp = MessageItemSpacing
) {
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var shouldFollowLatest by remember { mutableStateOf(true) }

    LaunchedEffect(isDragged) {
        if (!isDragged) {
            shouldFollowLatest = !listState.canScrollForward
        }
    }

    LaunchedEffect(
        messages.lastOrNull()?.id,
        messages.lastOrNull()?.text?.length,
        showEmptyState
    ) {
        if (!showEmptyState && messages.isNotEmpty() && shouldFollowLatest && !isDragged) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        if (showEmptyState) {
            EmptyChatState()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = listState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(messageSpacing)
            ) {
                items(
                    items = messages,
                    key = { message -> message.id }
                ) { message ->
                    if (message.isUserMessage) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            MessageBubble(
                                messageText = message.text,
                                isUserMessage = true
                            )
                        }
                    } else {
                        AssistantMessageItem(
                            assistantText = message.text,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "Message list light", widthDp = 360, heightDp = 640, showBackground = true, showSystemUi = true)
@Composable
private fun MessageListLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        MessageList(messages = previewMessages(), showEmptyState = false)
    }
}

@Preview(name = "Message list dark", widthDp = 360, heightDp = 640, showBackground = true, showSystemUi = true)
@Composable
private fun MessageListDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        MessageList(messages = previewMessages(), showEmptyState = false)
    }
}

@Preview(name = "Message list empty", widthDp = 360, heightDp = 640, showBackground = true, showSystemUi = true)
@Composable
private fun MessageListEmptyPreview() {
    ChatPreviewFrame(darkTheme = false) {
        MessageList(messages = emptyList(), showEmptyState = true)
    }
}

@Preview(name = "Markdown list light", widthDp = 360, heightDp = 640, showBackground = true, showSystemUi = true)
@Composable
private fun MessageListMarkdownLightPreview() {
    ChatPreviewFrame(darkTheme = false) {
        MessageList(messages = previewMarkdownMessages(), showEmptyState = false)
    }
}

@Preview(name = "Markdown list dark", widthDp = 360, heightDp = 640, showBackground = true, showSystemUi = true)
@Composable
private fun MessageListMarkdownDarkPreview() {
    ChatPreviewFrame(darkTheme = true) {
        MessageList(messages = previewMarkdownMessages(), showEmptyState = false)
    }
}
