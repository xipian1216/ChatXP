package com.xipian.chatxp_android.ui.screens.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.model.ChatMessageModel
import com.xipian.chatxp_android.data.model.ChatModel
import com.xipian.chatxp_android.data.model.ChatSessionModel
import com.xipian.chatxp_android.data.model.DEFAULT_MODEL_ID
import com.xipian.chatxp_android.data.model.MessageStatus
import com.xipian.chatxp_android.data.remote.ApiException
import com.xipian.chatxp_android.data.remote.ChatStreamEvent
import com.xipian.chatxp_android.data.repository.ChatDataRepository
import com.xipian.chatxp_android.data.repository.toModel
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: ChatDataRepository,
    private val authStore: CredentialStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()
    private var searchJob: Job? = null
    private var generationJob: Job? = null
    private val renamedSessions = mutableMapOf<String, String>()
    private val pinnedSessions = mutableMapOf<String, Boolean>()
    private val deletedSessionIds = mutableSetOf<String>()

    init {
        viewModelScope.launch { initialize() }
    }

    fun onAction(action: ChatAction) {
        when (action) {
            is ChatAction.ComposerChanged -> _uiState.update {
                it.copy(composerText = action.text, error = null)
            }
            ChatAction.Send -> send()
            ChatAction.OpenDrawer -> {
                _uiState.update { it.copy(isDrawerOpen = true, isModelMenuOpen = false) }
                refreshSessions(_uiState.value.searchQuery)
            }
            ChatAction.CloseDrawer -> _uiState.update { it.copy(isDrawerOpen = false) }
            is ChatAction.SearchChanged -> search(action.query)
            is ChatAction.SelectSession -> selectSession(action.sessionId)
            is ChatAction.RenameSession -> renameSession(action.sessionId, action.title)
            is ChatAction.TogglePinSession -> togglePinSession(action.sessionId)
            is ChatAction.DeleteSession -> deleteSession(action.sessionId)
            ChatAction.NewChat -> newChat()
            ChatAction.ModelClick -> toggleModelMenu()
            ChatAction.DismissModelMenu -> closeModelMenu()
            is ChatAction.SelectModel -> selectModel(action.modelId)
            is ChatAction.SelectReasoningMode -> selectReasoningMode(action.mode)
            ChatAction.Attach,
            ChatAction.MoreClick,
            ChatAction.ProfileClick -> Unit
        }
    }

    private suspend fun initialize() {
        _uiState.update { it.copy(isInitializing = true, error = null) }
        val (modelsResult, sessionsResult) = coroutineScope {
            val models = async { runCatching { repository.models() } }
            val sessions = async { runCatching { repository.sessions() } }
            models.await() to sessions.await()
        }
        val models = modelsResult.getOrDefault(emptyList())
        val sessions = sessionsResult.getOrDefault(emptyList())
        val modelOptions = models.toUiOptions()
        val defaultModel = modelOptions.firstOrNull(ModelOption::isDefault)
            ?: modelOptions.first()
        val selectedModelId = defaultModel.id
        val selectedReasoningMode = ReasoningModeUi.STANDARD

        if (sessionsResult.isSuccess) {
            val stored = authStore.read()
            val visibleSessions = applyLocalSessionChanges(sessions.map(ChatSessionModel::toUi))
            _uiState.update {
                it.copy(
                    modelName = defaultModel.displayName,
                    selectedModelId = selectedModelId,
                    selectedReasoningMode = selectedReasoningMode,
                    modelOptions = modelOptions,
                    isModelCatalogLoaded = modelsResult.isSuccess,
                    modelConfigError = modelsResult.exceptionOrNull()?.toUiError(),
                    sessions = visibleSessions,
                    selectedSessionId = null,
                    draftId = "draft-${UUID.randomUUID()}",
                    messages = emptyList(),
                    isInitializing = false
                )
            }
            authStore.saveSelectedSessionId(null)
            stored.activeClientRequestId?.let { recoverGeneration(it, fromStartup = true) }
        } else {
            _uiState.update {
                it.copy(
                    modelName = defaultModel.displayName,
                    selectedModelId = selectedModelId,
                    selectedReasoningMode = selectedReasoningMode,
                    modelOptions = modelOptions,
                    isModelCatalogLoaded = modelsResult.isSuccess,
                    modelConfigError = modelsResult.exceptionOrNull()?.toUiError(),
                    isInitializing = false,
                    error = sessionsResult.exceptionOrNull()?.toUiError()
                )
            }
        }
    }

    private fun toggleModelMenu() {
        _uiState.update {
            it.copy(
                isModelMenuOpen = !it.isModelMenuOpen,
                modelConfigError = null
            )
        }
    }

    private fun closeModelMenu() {
        _uiState.update { it.copy(isModelMenuOpen = false, modelConfigError = null) }
    }

    private fun selectModel(modelId: String) {
        val state = _uiState.value
        if (state.isModelConfigUpdating || modelId == state.selectedModelId) return
        val option = state.modelOptions.firstOrNull { it.id == modelId } ?: return
        val previousId = state.selectedModelId
        val previousName = state.modelName
        _uiState.update {
            it.copy(
                selectedModelId = option.id,
                modelName = option.displayName,
                modelConfigError = null
            )
        }
        val sessionId = state.selectedSessionId ?: return
        _uiState.update { it.copy(isModelConfigUpdating = true) }
        viewModelScope.launch {
            runCatching { repository.updateSession(sessionId = sessionId, modelId = modelId) }
                .onSuccess { updated -> applyUpdatedSession(updated) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            selectedModelId = previousId,
                            modelName = previousName,
                            isModelConfigUpdating = false,
                            modelConfigError = error.toUiError()
                        )
                    }
                    if (error is ApiException && error.code == "MODEL_NOT_FOUND") {
                        refreshModelCatalogAndFallback()
                    }
                }
        }
    }

    private fun selectReasoningMode(mode: ReasoningModeUi) {
        val state = _uiState.value
        if (state.isModelConfigUpdating || mode == state.selectedReasoningMode) return
        val selectedModel = state.modelOptions.firstOrNull { it.id == state.selectedModelId }
        if (selectedModel == null || mode !in selectedModel.reasoningModes) return
        val previous = state.selectedReasoningMode
        _uiState.update { it.copy(selectedReasoningMode = mode, modelConfigError = null) }
        val sessionId = state.selectedSessionId ?: return
        _uiState.update { it.copy(isModelConfigUpdating = true) }
        viewModelScope.launch {
            runCatching {
                repository.updateSession(sessionId = sessionId, reasoningMode = mode.apiValue)
            }.onSuccess { updated -> applyUpdatedSession(updated) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            selectedReasoningMode = previous,
                            isModelConfigUpdating = false,
                            modelConfigError = error.toUiError()
                        )
                    }
                }
        }
    }

    private fun applyUpdatedSession(session: ChatSessionModel) {
        val option = _uiState.value.modelOptions.firstOrNull { it.id == session.modelId }
        _uiState.update { state ->
            state.copy(
                selectedModelId = session.modelId,
                modelName = option?.displayName ?: state.modelName,
                selectedReasoningMode = session.reasoningMode.toReasoningModeUi(),
                sessions = upsertSession(state.sessions, session.toUi()),
                isModelConfigUpdating = false,
                modelConfigError = null
            )
        }
    }

    private fun refreshModelCatalogAndFallback() {
        viewModelScope.launch {
            val result = runCatching { repository.models() }
            val models = result.getOrDefault(emptyList())
            val options = models.toUiOptions()
            val default = options.firstOrNull(ModelOption::isDefault) ?: options.first()
            _uiState.update {
                it.copy(
                    selectedModelId = default.id,
                    modelName = default.displayName,
                    selectedReasoningMode = ReasoningModeUi.STANDARD,
                    modelOptions = options,
                    isModelCatalogLoaded = result.isSuccess,
                    isModelConfigUpdating = false,
                    modelConfigError = result.exceptionOrNull()?.toUiError() ?: UiError.VALIDATION
                )
            }
        }
    }

    private fun search(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            refreshSessions(query)
        }
    }

    private fun refreshSessions(query: String?) {
        viewModelScope.launch {
            runCatching { repository.sessions(query?.trim()) }
                .onSuccess { sessions ->
                    _uiState.update { state ->
                        state.copy(
                            sessions = applyLocalSessionChanges(
                                sessions.map(ChatSessionModel::toUi)
                            )
                        )
                    }
                }
                .onFailure { error -> _uiState.update { it.copy(error = error.toUiError()) } }
        }
    }

    private fun selectSession(sessionId: String) {
        if (_uiState.value.isGenerating) return
        _uiState.update { state ->
            val session = state.sessions.firstOrNull { it.sessionId == sessionId }
            val option = state.modelOptions.firstOrNull { it.id == session?.modelId }
            state.copy(
                selectedSessionId = sessionId,
                draftId = null,
                isDrawerOpen = false,
                isModelMenuOpen = false,
                selectedModelId = session?.modelId ?: state.selectedModelId,
                modelName = option?.displayName ?: state.modelName,
                selectedReasoningMode = session?.reasoningMode ?: ReasoningModeUi.STANDARD,
                composerText = "",
                modelConfigError = null
            )
        }
        viewModelScope.launch { authStore.saveSelectedSessionId(sessionId) }
        viewModelScope.launch { loadMessages(sessionId) }
    }

    private fun renameSession(sessionId: String, title: String) {
        val normalizedTitle = title.trim()
        if (normalizedTitle.isEmpty()) return
        renamedSessions[sessionId] = normalizedTitle
        _uiState.update { state ->
            state.copy(
                sessions = applyLocalSessionChanges(state.sessions)
            )
        }
    }

    private fun togglePinSession(sessionId: String) {
        val session = _uiState.value.sessions.firstOrNull { it.sessionId == sessionId } ?: return
        pinnedSessions[sessionId] = !session.isPinned
        _uiState.update { state ->
            state.copy(sessions = applyLocalSessionChanges(state.sessions))
        }
    }

    private fun deleteSession(sessionId: String) {
        val state = _uiState.value
        if (state.isGenerating && state.selectedSessionId == sessionId) return

        deletedSessionIds += sessionId
        renamedSessions.remove(sessionId)
        pinnedSessions.remove(sessionId)
        val remainingSessions = applyLocalSessionChanges(state.sessions)
        _uiState.update { it.copy(sessions = remainingSessions) }

        if (state.selectedSessionId != sessionId) return
        val nextSession = remainingSessions.firstOrNull()
        if (nextSession != null) {
            _uiState.update { current ->
                val model = current.modelOptions.firstOrNull { it.id == nextSession.modelId }
                current.copy(
                    selectedSessionId = nextSession.sessionId,
                    draftId = null,
                    selectedModelId = nextSession.modelId,
                    modelName = model?.displayName ?: current.modelName,
                    selectedReasoningMode = nextSession.reasoningMode,
                    messages = emptyList(),
                    composerText = ""
                )
            }
            viewModelScope.launch {
                authStore.saveSelectedSessionId(nextSession.sessionId)
                loadMessages(nextSession.sessionId)
            }
        } else {
            val draftId = "draft-${UUID.randomUUID()}"
            _uiState.update { current ->
                val defaultModel = current.modelOptions.firstOrNull(ModelOption::isDefault)
                    ?: current.modelOptions.firstOrNull()
                current.copy(
                    selectedSessionId = null,
                    draftId = draftId,
                    selectedModelId = defaultModel?.id ?: DEFAULT_MODEL_ID,
                    modelName = defaultModel?.displayName.orEmpty(),
                    selectedReasoningMode = ReasoningModeUi.STANDARD,
                    messages = emptyList(),
                    composerText = "",
                    error = null
                )
            }
            viewModelScope.launch { authStore.saveSelectedSessionId(null) }
        }
    }

    private fun applyLocalSessionChanges(sessions: List<ChatSession>): List<ChatSession> =
        sessions
            .asSequence()
            .filterNot { it.sessionId in deletedSessionIds }
            .map { session ->
                session.copy(
                    sessionTitle = renamedSessions[session.sessionId] ?: session.sessionTitle,
                    isPinned = pinnedSessions[session.sessionId] ?: session.isPinned
                )
            }
            .sortedByDescending(ChatSession::isPinned)
            .toList()

    private suspend fun loadMessages(sessionId: String) {
        _uiState.update { it.copy(isLoadingMessages = true, error = null) }
        runCatching { repository.messages(sessionId) }
            .onSuccess { messages ->
                if (_uiState.value.selectedSessionId == sessionId) {
                    _uiState.update {
                        it.copy(
                            messages = messages.map(ChatMessageModel::toUi),
                            isLoadingMessages = false
                        )
                    }
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(isLoadingMessages = false, error = error.toUiError())
                }
            }
    }

    private fun newChat() {
        if (_uiState.value.isGenerating) return
        val draftId = "draft-${UUID.randomUUID()}"
        _uiState.update {
            val defaultModel = it.modelOptions.firstOrNull(ModelOption::isDefault)
                ?: it.modelOptions.firstOrNull()
            it.copy(
                selectedSessionId = null,
                draftId = draftId,
                selectedModelId = defaultModel?.id ?: DEFAULT_MODEL_ID,
                modelName = defaultModel?.displayName.orEmpty(),
                selectedReasoningMode = ReasoningModeUi.STANDARD,
                messages = emptyList(),
                composerText = "",
                isDrawerOpen = false,
                isModelMenuOpen = false,
                modelConfigError = null,
                error = null
            )
        }
        viewModelScope.launch { authStore.saveSelectedSessionId(null) }
    }

    private fun send() {
        val state = _uiState.value
        val content = state.composerText.trim()
        if (content.isEmpty() || state.isGenerating || state.isInitializing) return

        val clientRequestId = UUID.randomUUID().toString()
        val clientMessageId = UUID.randomUUID().toString()
        val assistantId = "local-assistant-$clientRequestId"
        val conversationId = state.selectedSessionId ?: state.draftId.orEmpty()
        val nextSequence = (state.messages.maxOfOrNull(ChatMessage::sequence) ?: 0) + 1
        val optimistic = listOf(
            ChatMessage(
                id = clientMessageId,
                sessionId = conversationId,
                text = content,
                isUserMessage = true,
                sequence = nextSequence
            ),
            ChatMessage(
                id = assistantId,
                sessionId = conversationId,
                text = "",
                isUserMessage = false,
                status = MessageStatusUi.STREAMING,
                sequence = nextSequence + 1
            )
        )
        _uiState.update {
            it.copy(
                messages = it.messages + optimistic,
                composerText = "",
                isGenerating = true,
                isModelMenuOpen = false,
                error = null
            )
        }
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            authStore.saveActiveClientRequestId(clientRequestId)
            var lastDeltaSequence = 0
            var receivedTerminalEvent = false
            try {
                repository.streamMessage(
                    clientRequestId = clientRequestId,
                    clientMessageId = clientMessageId,
                    sessionId = state.selectedSessionId,
                    modelId = state.selectedSessionId?.let { null } ?: state.selectedModelId,
                    reasoningMode = state.selectedSessionId?.let { null }
                        ?: state.selectedReasoningMode.apiValue,
                    content = content
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Meta -> applyMeta(event, clientMessageId, assistantId)
                        is ChatStreamEvent.Delta -> {
                            if (event.value.sequence > lastDeltaSequence) {
                                lastDeltaSequence = event.value.sequence
                                appendDelta(event)
                            }
                        }
                        is ChatStreamEvent.Done -> {
                            receivedTerminalEvent = true
                            applyDone(event)
                        }
                        is ChatStreamEvent.Error -> throw StreamFailure(event.value.code)
                    }
                }
                if (receivedTerminalEvent) finishGeneration()
                else recoverGeneration(clientRequestId, fromStartup = false)
            } catch (error: CancellationException) {
                throw error
            } catch (error: StreamFailure) {
                failGeneration(error.code)
            } catch (error: Throwable) {
                recoverGeneration(clientRequestId, fromStartup = false)
            }
        }
    }

    private suspend fun applyMeta(
        event: ChatStreamEvent.Meta,
        clientMessageId: String,
        localAssistantId: String
    ) {
        val meta = event.value
        val session = meta.session.toModel()
        _uiState.update { state ->
            val option = state.modelOptions.firstOrNull { it.id == session.modelId }
            state.copy(
                selectedSessionId = session.id,
                draftId = null,
                selectedModelId = session.modelId,
                modelName = option?.displayName ?: state.modelName,
                selectedReasoningMode = session.reasoningMode.toReasoningModeUi(),
                sessions = upsertSession(state.sessions, session.toUi()),
                messages = state.messages.map { message ->
                    when (message.id) {
                        clientMessageId -> meta.userMessage.toModel().toUi()
                        localAssistantId -> message.copy(
                            id = meta.assistantMessageId,
                            sessionId = session.id
                        )
                        else -> message
                    }
                }
            )
        }
        authStore.saveSelectedSessionId(session.id)
    }

    private fun appendDelta(event: ChatStreamEvent.Delta) {
        _uiState.update { state ->
            state.copy(messages = state.messages.map { message ->
                if (message.id == event.value.assistantMessageId) {
                    message.copy(text = message.text + event.value.contentDelta)
                } else message
            })
        }
    }

    private fun applyDone(event: ChatStreamEvent.Done) {
        val assistant = event.value.assistantMessage.toModel().toUi()
        val session = event.value.session.toModel().toUi()
        _uiState.update { state ->
            val option = state.modelOptions.firstOrNull { it.id == session.modelId }
            state.copy(
                selectedModelId = session.modelId,
                modelName = option?.displayName ?: state.modelName,
                selectedReasoningMode = session.reasoningMode,
                messages = state.messages.map { if (it.id == assistant.id) assistant else it },
                sessions = upsertSession(state.sessions, session)
            )
        }
    }

    private suspend fun recoverGeneration(clientRequestId: String, fromStartup: Boolean) {
        val startedAt = System.currentTimeMillis()
        var delayIndex = 0
        var startupMessagesLoaded = false
        _uiState.update { it.copy(isGenerating = true) }
        while (System.currentTimeMillis() - startedAt < RECOVERY_TIMEOUT_MS) {
            val result = runCatching { repository.generation(clientRequestId) }
            val generation = result.getOrNull()
            if (generation != null) {
                val recoveredModelId = generation.modelId
                    ?: generation.assistantMessage.modelId
                    ?: _uiState.value.selectedModelId
                val recoveredModelName = _uiState.value.modelOptions
                    .firstOrNull { it.id == recoveredModelId }
                    ?.displayName
                    ?: _uiState.value.modelName
                _uiState.update {
                    it.copy(
                        selectedModelId = recoveredModelId,
                        modelName = recoveredModelName,
                        selectedReasoningMode = generation.reasoningMode.toReasoningModeUi()
                    )
                }
                if ((fromStartup && !startupMessagesLoaded) ||
                    _uiState.value.selectedSessionId != generation.sessionId
                ) {
                    _uiState.update { it.copy(selectedSessionId = generation.sessionId, draftId = null) }
                    loadMessages(generation.sessionId)
                    startupMessagesLoaded = true
                } else {
                    val assistant = generation.assistantMessage.toUi()
                    _uiState.update { state -> state.copy(
                        messages = state.messages.map {
                            if (it.id == assistant.id || (!it.isUserMessage && it.status == MessageStatusUi.STREAMING)) assistant else it
                        }
                    ) }
                }
                when (generation.status) {
                    "completed" -> {
                        refreshSessions(null)
                        finishGeneration()
                        return
                    }
                    "failed" -> {
                        failGeneration(generation.errorCode ?: "GENERATION_INTERRUPTED")
                        return
                    }
                }
            } else if (result.exceptionOrNull() is ApiException &&
                (result.exceptionOrNull() as ApiException).statusCode == 404
            ) {
                if (fromStartup) {
                    finishGeneration()
                    return
                }
            } else if (result.exceptionOrNull() is ApiException) {
                break
            }
            delay(RECOVERY_DELAYS_MS[delayIndex].toLong())
            if (delayIndex < RECOVERY_DELAYS_MS.lastIndex) delayIndex++
        }
        failGeneration("NETWORK")
    }

    private suspend fun finishGeneration() {
        authStore.saveActiveClientRequestId(null)
        _uiState.update { it.copy(isGenerating = false, error = null) }
    }

    private suspend fun failGeneration(code: String) {
        authStore.saveActiveClientRequestId(null)
        val error = code.toUiError()
        _uiState.update { state -> state.copy(
            isGenerating = false,
            error = error,
            messages = state.messages.map { message ->
                if (!message.isUserMessage && message.status == MessageStatusUi.STREAMING) {
                    message.copy(status = MessageStatusUi.FAILED, errorCode = code)
                } else message
            }
        ) }
    }

    private fun upsertSession(
        sessions: List<ChatSession>,
        session: ChatSession
    ): List<ChatSession> = applyLocalSessionChanges(
        listOf(session) + sessions.filterNot { it.sessionId == session.sessionId }
    ).sortedWith(
        compareByDescending<ChatSession> { it.isPinned }.thenByDescending { it.updatedAtText }
    )

    companion object {
        private const val SEARCH_DEBOUNCE_MS = 300L
        private const val RECOVERY_TIMEOUT_MS = 5 * 60 * 1000L
        private val RECOVERY_DELAYS_MS = listOf(1_000, 2_000, 4_000, 5_000)

        fun factory(repository: ChatDataRepository, authStore: CredentialStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(repository, authStore) as T
                }
            }
    }
}

private class StreamFailure(val code: String) : IOException(code)

private fun ChatSessionModel.toUi() = ChatSession(
    sessionId = id,
    sessionTitle = title,
    modelId = modelId,
    reasoningMode = reasoningMode.toReasoningModeUi(),
    messagePreview = messagePreview.orEmpty(),
    updatedAtText = formatUpdatedAt(updatedAt),
    isPinned = isPinned
)

private fun List<ChatModel>.toUiOptions(): List<ModelOption> {
    val catalogById = associateBy(ChatModel::id)
    return SUPPORTED_MODEL_IDS.map { modelId ->
        ModelOption(
            id = modelId,
            displayName = modelId.removePrefix("chat-"),
            reasoningModes = ReasoningModeUi.entries.toSet(),
            isDefault = catalogById[modelId]?.isDefault == true || modelId == DEFAULT_MODEL_ID
        )
    }
}

private val SUPPORTED_MODEL_IDS = listOf(DEFAULT_MODEL_ID, "chat-5.6")

private fun String.toReasoningModeUi(): ReasoningModeUi {
    val resolved = ReasoningModeUi.entries.firstOrNull { it.apiValue == this }
    if (resolved == null) {
        Log.w("ChatViewModel", "Unknown public reasoning mode; falling back to standard")
    }
    return resolved ?: ReasoningModeUi.STANDARD
}

private fun ChatMessageModel.toUi() = ChatMessage(
    id = id,
    sessionId = sessionId,
    text = content,
    isUserMessage = role == com.xipian.chatxp_android.data.model.MessageRole.USER,
    status = when (status) {
        MessageStatus.STREAMING -> MessageStatusUi.STREAMING
        MessageStatus.FAILED -> MessageStatusUi.FAILED
        MessageStatus.COMPLETED -> MessageStatusUi.COMPLETED
    },
    sequence = sequence,
    errorCode = errorCode
)

private fun Throwable.toUiError(): UiError = when (this) {
    is ApiException -> code.toUiError()
    is IOException -> UiError.NETWORK
    else -> UiError.UNKNOWN
}

private fun String.toUiError(): UiError = when (this) {
    "AUTH_INVALID", "AUTH_EXPIRED" -> UiError.AUTH
    "PROVIDER_RATE_LIMIT" -> UiError.RATE_LIMIT
    "PROVIDER_UNAVAILABLE" -> UiError.PROVIDER_UNAVAILABLE
    "VALIDATION_ERROR", "MODEL_NOT_FOUND", "SESSION_NOT_FOUND" -> UiError.VALIDATION
    "GENERATION_INTERRUPTED" -> UiError.GENERATION_INTERRUPTED
    "NETWORK" -> UiError.NETWORK
    else -> UiError.UNKNOWN
}

private fun formatUpdatedAt(value: String): String {
    return runCatching {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val date = input.parse(value.take(19)) ?: return value.take(10)
        val now = Date()
        val sameDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        if (sameDay.format(date) == sameDay.format(now)) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            SimpleDateFormat("MM-dd", Locale.getDefault()).format(date)
        }
    }.getOrDefault(value.take(10))
}
