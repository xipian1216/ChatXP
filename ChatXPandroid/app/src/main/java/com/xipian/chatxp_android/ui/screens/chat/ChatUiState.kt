package com.xipian.chatxp_android.ui.screens.chat

enum class MessageStatusUi { STREAMING, COMPLETED, FAILED }

enum class UiError {
    NETWORK,
    AUTH,
    RATE_LIMIT,
    PROVIDER_UNAVAILABLE,
    VALIDATION,
    GENERATION_INTERRUPTED,
    UNKNOWN
}

data class ChatMessage(
    val id: String,
    val sessionId: String,
    val text: String,
    val isUserMessage: Boolean,
    val status: MessageStatusUi = MessageStatusUi.COMPLETED,
    val sequence: Int = 0,
    val errorCode: String? = null
)

data class ChatSession(
    val sessionId: String,
    val sessionTitle: String,
    val messagePreview: String,
    val updatedAtText: String,
    val isPinned: Boolean = false
)

data class ChatUiState(
    val modelName: String = "",
    val selectedModelId: String? = null,
    val sessions: List<ChatSession> = emptyList(),
    val selectedSessionId: String? = null,
    val draftId: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val composerText: String = "",
    val searchQuery: String = "",
    val isDrawerOpen: Boolean = false,
    val isInitializing: Boolean = true,
    val isLoadingMessages: Boolean = false,
    val isGenerating: Boolean = false,
    val error: UiError? = null
)

sealed interface ChatAction {
    data class ComposerChanged(val text: String) : ChatAction
    data object Send : ChatAction
    data object OpenDrawer : ChatAction
    data object CloseDrawer : ChatAction
    data class SearchChanged(val query: String) : ChatAction
    data class SelectSession(val sessionId: String) : ChatAction
    data class RenameSession(val sessionId: String, val title: String) : ChatAction
    data class TogglePinSession(val sessionId: String) : ChatAction
    data class DeleteSession(val sessionId: String) : ChatAction
    data object NewChat : ChatAction
    data object Attach : ChatAction
    data object ModelClick : ChatAction
    data object MoreClick : ChatAction
    data object ProfileClick : ChatAction
}
