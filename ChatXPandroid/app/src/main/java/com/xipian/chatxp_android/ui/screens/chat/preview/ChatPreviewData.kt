package com.xipian.chatxp_android.ui.screens.chat.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.ChatMessage
import com.xipian.chatxp_android.ui.screens.chat.ChatSession

const val PreviewSelectedSessionId = "study-plan"

@Composable
fun previewMessages(): List<ChatMessage> = listOf(
    ChatMessage("preview-user-1", "study-plan", stringResource(R.string.chat_preview_user_message_short), isUserMessage = true, sequence = 1),
    ChatMessage("preview-assistant-1", "study-plan", stringResource(R.string.chat_preview_assistant_message_short), isUserMessage = false, sequence = 2),
    ChatMessage("preview-user-2", "study-plan", stringResource(R.string.chat_preview_user_message_long), isUserMessage = true, sequence = 3),
    ChatMessage(
        id = "preview-assistant-2",
        sessionId = "study-plan",
        text = stringResource(R.string.chat_preview_assistant_message_long),
        isUserMessage = false,
        sequence = 4
    )
)

@Composable
fun previewMarkdownMessages(): List<ChatMessage> = listOf(
    ChatMessage(
        id = "preview-markdown-user",
        sessionId = "markdown-preview",
        text = stringResource(R.string.chat_preview_markdown_user),
        isUserMessage = true,
        sequence = 1
    ),
    ChatMessage(
        id = "preview-markdown-assistant",
        sessionId = "markdown-preview",
        text = stringResource(R.string.chat_preview_markdown_assistant),
        isUserMessage = false,
        sequence = 2
    )
)

val PreviewEmptyMessages = emptyList<ChatMessage>()

@Composable
fun previewChatSessions(): List<ChatSession> {
    val messages = previewMessages()
    return listOf(
        ChatSession(
            sessionId = PreviewSelectedSessionId,
            sessionTitle = stringResource(R.string.preview_session_study_title),
            messagePreview = messages[1].text,
            updatedAtText = stringResource(R.string.preview_session_study_time),
            isPinned = true
        ),
        ChatSession(
            sessionId = "writing-outline",
            sessionTitle = stringResource(R.string.preview_session_writing_title),
            messagePreview = messages[2].text,
            updatedAtText = stringResource(R.string.preview_session_writing_time)
        ),
        ChatSession(
            sessionId = "material-summary",
            sessionTitle = stringResource(R.string.preview_session_summary_title),
            messagePreview = messages.last().text,
            updatedAtText = stringResource(R.string.preview_session_summary_time)
        )
    )
}
