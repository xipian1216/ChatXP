package com.xipian.chatxp_android.ui.screens.chat

import com.xipian.chatxp_android.data.model.DEFAULT_MODEL_ID

enum class MessageStatusUi { STREAMING, COMPLETED, FAILED }

enum class ReasoningModeUi(val apiValue: String) {
    STANDARD("standard"),
    ADVANCED("advanced");

    companion object {
        fun fromApiValue(value: String?): ReasoningModeUi =
            entries.firstOrNull { it.apiValue == value } ?: STANDARD
    }
}

data class ModelOption(
    val id: String,
    val displayName: String,
    val reasoningModes: Set<ReasoningModeUi>,
    val isDefault: Boolean = false
)

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
    val modelId: String = DEFAULT_MODEL_ID,
    val reasoningMode: ReasoningModeUi = ReasoningModeUi.STANDARD,
    val messagePreview: String,
    val updatedAtText: String,
    val isPinned: Boolean = false
)

data class ChatUiState(
    val modelName: String = "",
    val selectedModelId: String = DEFAULT_MODEL_ID,
    val selectedReasoningMode: ReasoningModeUi = ReasoningModeUi.STANDARD,
    val modelOptions: List<ModelOption> = emptyList(),
    val isModelCatalogLoaded: Boolean = false,
    val isModelMenuOpen: Boolean = false,
    val isModelConfigUpdating: Boolean = false,
    val modelConfigError: UiError? = null,
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
    data object DismissModelMenu : ChatAction
    data class SelectModel(val modelId: String) : ChatAction
    data class SelectReasoningMode(val mode: ReasoningModeUi) : ChatAction
    data object MoreClick : ChatAction
    data object ProfileClick : ChatAction
}
