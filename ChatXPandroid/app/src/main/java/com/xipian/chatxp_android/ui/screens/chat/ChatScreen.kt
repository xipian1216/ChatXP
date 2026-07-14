package com.xipian.chatxp_android.ui.screens.chat

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.xipian.chatxp_android.R
import com.xipian.chatxp_android.ui.screens.chat.component.ChatComposer
import com.xipian.chatxp_android.ui.screens.chat.component.ChatTopBar
import com.xipian.chatxp_android.ui.screens.chat.component.DrawerEdgeSwipeArea
import com.xipian.chatxp_android.ui.screens.chat.component.MessageList
import com.xipian.chatxp_android.ui.screens.chat.component.ThreadDrawer
import com.xipian.chatxp_android.ui.screens.chat.preview.previewChatSessions
import com.xipian.chatxp_android.ui.screens.chat.preview.previewMessages
import com.xipian.chatxp_android.ui.theme.ChatXPandroidTheme

@Composable
fun ChatScreen(
    uiState: ChatUiState,
    onAction: (ChatAction) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = uiState.isDrawerOpen) { onAction(ChatAction.CloseDrawer) }
    val localizedError = uiState.error?.let { stringResource(it.messageRes) }
    val replyingText = stringResource(R.string.chat_replying)
    val visibleMessages = uiState.messages.map { message ->
        when {
            message.status == MessageStatusUi.STREAMING && message.text.isEmpty() -> {
                message.copy(text = replyingText)
            }
            message.status == MessageStatusUi.FAILED && message.text.isEmpty() -> {
                message.copy(text = localizedError ?: stringResource(R.string.chat_error_unknown))
            }
            else -> message
        }
    }.let { messages ->
        if (messages.isEmpty() && localizedError != null) {
            listOf(
                ChatMessage(
                    id = "ui-error",
                    sessionId = uiState.selectedSessionId.orEmpty(),
                    text = localizedError,
                    isUserMessage = false,
                    status = MessageStatusUi.FAILED
                )
            )
        } else messages
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar(
                modelName = uiState.modelName.ifEmpty {
                    stringResource(R.string.chat_model_name)
                },
                onMenuClick = { onAction(ChatAction.OpenDrawer) },
                onModelClick = { onAction(ChatAction.ModelClick) },
                onNewChatClick = { onAction(ChatAction.NewChat) },
                onMoreClick = { onAction(ChatAction.MoreClick) }
            )
            MessageList(
                messages = visibleMessages,
                showEmptyState = visibleMessages.isEmpty() && !uiState.isLoadingMessages,
                modifier = Modifier.weight(1f)
            )
            ChatComposer(
                composerText = uiState.composerText,
                onComposerTextChange = { onAction(ChatAction.ComposerChanged(it)) },
                isEnabled = !uiState.isInitializing && !uiState.isLoadingMessages,
                isSendEnabled = !uiState.isGenerating,
                onAttachClick = { onAction(ChatAction.Attach) },
                onSendClick = { onAction(ChatAction.Send) }
            )
        }

        DrawerEdgeSwipeArea(
            enabled = !uiState.isDrawerOpen,
            onOpen = { onAction(ChatAction.OpenDrawer) },
            modifier = Modifier.align(Alignment.CenterStart)
        )

        ThreadDrawer(
            isOpen = uiState.isDrawerOpen,
            sessions = uiState.sessions,
            selectedSessionId = uiState.selectedSessionId,
            isGenerating = uiState.isGenerating,
            searchQuery = uiState.searchQuery,
            title = stringResource(R.string.drawer_title),
            searchPlaceholder = stringResource(R.string.drawer_search_placeholder),
            emptySearchText = stringResource(R.string.drawer_search_empty),
            chatButtonText = stringResource(R.string.drawer_chat_button),
            profileLabel = stringResource(R.string.drawer_profile_label),
            onSearchQueryChange = { onAction(ChatAction.SearchChanged(it)) },
            onSessionSelected = { onAction(ChatAction.SelectSession(it)) },
            onSessionRenamed = { sessionId, title ->
                onAction(ChatAction.RenameSession(sessionId, title))
            },
            onSessionPinToggled = { onAction(ChatAction.TogglePinSession(it)) },
            onSessionDeleted = { onAction(ChatAction.DeleteSession(it)) },
            onClose = { onAction(ChatAction.CloseDrawer) },
            onNewChatClick = { onAction(ChatAction.NewChat) },
            onProfileClick = { onAction(ChatAction.ProfileClick) },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@get:StringRes
private val UiError.messageRes: Int
    get() = when (this) {
        UiError.NETWORK -> R.string.chat_error_network
        UiError.AUTH -> R.string.chat_error_auth
        UiError.RATE_LIMIT -> R.string.chat_error_rate_limit
        UiError.PROVIDER_UNAVAILABLE -> R.string.chat_error_provider_unavailable
        UiError.VALIDATION -> R.string.chat_error_validation
        UiError.GENERATION_INTERRUPTED -> R.string.chat_error_generation_interrupted
        UiError.UNKNOWN -> R.string.chat_error_unknown
    }

@Preview(name = "Chat screen light", showBackground = true, showSystemUi = true)
@Composable
private fun ChatScreenLightPreview() {
    ChatScreenPreview(darkTheme = false, showEmptyState = false)
}

@Preview(name = "Chat screen dark", showBackground = true, showSystemUi = true)
@Composable
private fun ChatScreenDarkPreview() {
    ChatScreenPreview(darkTheme = true, showEmptyState = false)
}

@Preview(name = "Chat empty light", showBackground = true, showSystemUi = true)
@Composable
private fun ChatScreenEmptyLightPreview() {
    ChatScreenPreview(darkTheme = false, showEmptyState = true)
}

@Preview(name = "Chat empty dark", showBackground = true, showSystemUi = true)
@Composable
private fun ChatScreenEmptyDarkPreview() {
    ChatScreenPreview(darkTheme = true, showEmptyState = true)
}

@Composable
private fun ChatScreenPreview(darkTheme: Boolean, showEmptyState: Boolean) {
    ChatXPandroidTheme(darkTheme = darkTheme) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            val sessions = previewChatSessions()
            ChatScreen(
                uiState = ChatUiState(
                    modelName = stringResource(R.string.chat_model_name),
                    sessions = sessions,
                    selectedSessionId = sessions.firstOrNull()?.sessionId,
                    messages = if (showEmptyState) emptyList() else previewMessages(),
                    isInitializing = false
                ),
                onAction = {},
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}
