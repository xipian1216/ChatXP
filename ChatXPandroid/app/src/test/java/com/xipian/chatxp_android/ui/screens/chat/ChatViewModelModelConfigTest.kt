package com.xipian.chatxp_android.ui.screens.chat

import com.xipian.chatxp_android.MainDispatcherRule
import com.xipian.chatxp_android.data.local.AuthSnapshot
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.model.ChatMessageModel
import com.xipian.chatxp_android.data.model.ChatModel
import com.xipian.chatxp_android.data.model.ChatSessionModel
import com.xipian.chatxp_android.data.model.GenerationModel
import com.xipian.chatxp_android.data.model.MessageRole
import com.xipian.chatxp_android.data.model.MessageStatus
import com.xipian.chatxp_android.data.remote.ApiException
import com.xipian.chatxp_android.data.remote.ChatStreamEvent
import com.xipian.chatxp_android.data.remote.dto.StreamErrorEventDto
import com.xipian.chatxp_android.data.repository.ChatDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelModelConfigTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun catalogLabelsAndReasoningModesAreNormalizedForTheUi() = runTest {
        val repository = ModelConfigRepository(
            models = listOf(
                ChatModel(
                    id = "chat-5.5",
                    displayName = "5.5 均衡",
                    description = null,
                    isDefault = true,
                    reasoningModes = listOf("standard")
                )
            )
        )
        val viewModel = ChatViewModel(repository, ModelConfigCredentialStore())
        advanceUntilIdle()

        assertEquals(listOf("5.5", "5.6"), viewModel.uiState.value.modelOptions.map { it.displayName })
        assertTrue(
            viewModel.uiState.value.modelOptions.all {
                it.reasoningModes == ReasoningModeUi.entries.toSet()
            }
        )
    }

    @Test
    fun draftDefaultsToStandardAndSendsAllFourCombinations() = runTest {
        val combinations = listOf(
            "chat-5.5" to ReasoningModeUi.STANDARD,
            "chat-5.5" to ReasoningModeUi.ADVANCED,
            "chat-5.6" to ReasoningModeUi.STANDARD,
            "chat-5.6" to ReasoningModeUi.ADVANCED
        )

        combinations.forEach { (modelId, reasoningMode) ->
            val repository = ModelConfigRepository(sessions = emptyList())
            val viewModel = ChatViewModel(repository, ModelConfigCredentialStore())
            advanceUntilIdle()

            assertEquals("chat-5.5", viewModel.uiState.value.selectedModelId)
            assertEquals(ReasoningModeUi.STANDARD, viewModel.uiState.value.selectedReasoningMode)
            viewModel.onAction(ChatAction.SelectModel(modelId))
            viewModel.onAction(ChatAction.SelectReasoningMode(reasoningMode))
            viewModel.onAction(ChatAction.ComposerChanged("测试"))
            viewModel.onAction(ChatAction.Send)
            runCurrent()

            assertEquals(modelId, repository.lastStreamModelId)
            assertEquals(reasoningMode.apiValue, repository.lastStreamReasoningMode)
        }
    }

    @Test
    fun existingSessionUpdatesEachSettingAndKeepsMenuOpen() = runTest {
        val repository = ModelConfigRepository()
        val viewModel = ChatViewModel(repository, ModelConfigCredentialStore())
        advanceUntilIdle()
        viewModel.onAction(ChatAction.SelectSession("session-1"))
        advanceUntilIdle()
        viewModel.onAction(ChatAction.ModelClick)

        viewModel.onAction(ChatAction.SelectModel("chat-5.6"))
        advanceUntilIdle()
        viewModel.onAction(ChatAction.SelectReasoningMode(ReasoningModeUi.ADVANCED))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("chat-5.6", state.selectedModelId)
        assertEquals(ReasoningModeUi.ADVANCED, state.selectedReasoningMode)
        assertTrue(state.isModelMenuOpen)
        assertFalse(state.isModelConfigUpdating)
        assertEquals(
            listOf("chat-5.6" to null, null to "advanced"),
            repository.updates
        )
    }

    @Test
    fun failedModelPatchRollsBackOnlyModelSelection() = runTest {
        val repository = ModelConfigRepository(
            updateFailure = ApiException("VALIDATION_ERROR", 400, "invalid")
        )
        val viewModel = ChatViewModel(repository, ModelConfigCredentialStore())
        advanceUntilIdle()
        viewModel.onAction(ChatAction.SelectSession("session-1"))
        advanceUntilIdle()
        viewModel.onAction(ChatAction.ModelClick)
        viewModel.onAction(ChatAction.SelectModel("chat-5.6"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("chat-5.5", state.selectedModelId)
        assertEquals(ReasoningModeUi.STANDARD, state.selectedReasoningMode)
        assertTrue(state.isModelMenuOpen)
        assertFalse(state.isModelConfigUpdating)
        assertNotNull(state.modelConfigError)
    }

    @Test
    fun selectingExistingSessionRestoresItsConfiguration() = runTest {
        val repository = ModelConfigRepository(
            sessions = listOf(
                modelConfigSession().copy(
                    modelId = "chat-5.6",
                    reasoningMode = "advanced"
                )
            )
        )
        val viewModel = ChatViewModel(repository, ModelConfigCredentialStore())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.SelectSession("session-1"))
        advanceUntilIdle()

        assertEquals("chat-5.6", viewModel.uiState.value.selectedModelId)
        assertEquals(ReasoningModeUi.ADVANCED, viewModel.uiState.value.selectedReasoningMode)
        assertEquals("5.6", viewModel.uiState.value.modelName)
    }

    @Test
    fun catalogFailureKeepsFallbackAndDisablesUnconfirmedOptions() = runTest {
        val viewModel = ChatViewModel(
            ModelConfigRepository(modelsFailure = ApiException("NETWORK", 503, "offline")),
            ModelConfigCredentialStore()
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("chat-5.5", state.selectedModelId)
        assertEquals(ReasoningModeUi.STANDARD, state.selectedReasoningMode)
        assertFalse(state.isModelCatalogLoaded)
        assertEquals(listOf("5.5", "5.6"), state.modelOptions.map { it.displayName })
        assertNotNull(state.modelConfigError)
    }

    @Test
    fun recoveredGenerationRestoresActualConfiguration() = runTest {
        val recoveredSession = modelConfigSession().copy(
            modelId = "chat-5.6",
            reasoningMode = "advanced"
        )
        val repository = ModelConfigRepository(
            sessions = listOf(recoveredSession),
            generationResult = GenerationModel(
                clientRequestId = "recover-request",
                status = "completed",
                sessionId = recoveredSession.id,
                assistantMessage = ChatMessageModel(
                    id = "assistant-1",
                    sessionId = recoveredSession.id,
                    role = MessageRole.ASSISTANT,
                    content = "完成",
                    status = MessageStatus.COMPLETED,
                    sequence = 2,
                    modelId = "chat-5.6",
                    reasoningMode = "advanced"
                ),
                modelId = "chat-5.6",
                reasoningMode = "advanced",
                errorCode = null
            )
        )
        val viewModel = ChatViewModel(
            repository,
            ModelConfigCredentialStore(activeClientRequestId = "recover-request")
        )
        advanceUntilIdle()

        assertEquals("chat-5.6", viewModel.uiState.value.selectedModelId)
        assertEquals(ReasoningModeUi.ADVANCED, viewModel.uiState.value.selectedReasoningMode)
        assertEquals("5.6", viewModel.uiState.value.modelName)
        assertFalse(viewModel.uiState.value.isGenerating)
    }
}

private class ModelConfigRepository(
    sessions: List<ChatSessionModel> = listOf(modelConfigSession()),
    private val models: List<ChatModel> = defaultModelConfigModels(),
    private val updateFailure: Throwable? = null,
    private val modelsFailure: Throwable? = null,
    private val generationResult: GenerationModel? = null
) : ChatDataRepository {
    private var currentSession = sessions.firstOrNull()
    val updates = mutableListOf<Pair<String?, String?>>()
    var lastStreamModelId: String? = null
    var lastStreamReasoningMode: String? = null

    override suspend fun models(): List<ChatModel> {
        modelsFailure?.let { throw it }
        return models
    }

    override suspend fun sessions(query: String?): List<ChatSessionModel> =
        currentSession?.let(::listOf).orEmpty()

    override suspend fun updateSession(
        sessionId: String,
        modelId: String?,
        reasoningMode: String?
    ): ChatSessionModel {
        updates += modelId to reasoningMode
        updateFailure?.let { throw it }
        return requireNotNull(currentSession).copy(
            modelId = modelId ?: currentSession?.modelId.orEmpty(),
            reasoningMode = reasoningMode ?: currentSession?.reasoningMode.orEmpty()
        ).also { currentSession = it }
    }

    override suspend fun messages(sessionId: String): List<ChatMessageModel> = emptyList()

    override fun streamMessage(
        clientRequestId: String,
        clientMessageId: String,
        sessionId: String?,
        modelId: String?,
        reasoningMode: String?,
        content: String
    ): Flow<ChatStreamEvent> {
        lastStreamModelId = modelId
        lastStreamReasoningMode = reasoningMode
        return flowOf(
            ChatStreamEvent.Error(
                StreamErrorEventDto(
                    code = "GENERATION_INTERRUPTED",
                    message = "test",
                    retryable = false,
                    assistantMessageId = "assistant-test"
                )
            )
        )
    }

    override suspend fun generation(clientRequestId: String): GenerationModel =
        generationResult ?: error("Not used")
}

private fun defaultModelConfigModels(): List<ChatModel> =
    listOf(
        ChatModel(
            id = "chat-5.5",
            displayName = "5.5",
            description = null,
            isDefault = true,
            reasoningModes = listOf("standard", "advanced")
        ),
        ChatModel(
            id = "chat-5.6",
            displayName = "5.6",
            description = null,
            isDefault = false,
            reasoningModes = listOf("standard", "advanced")
        )
    )

private class ModelConfigCredentialStore(
    private var activeClientRequestId: String? = null
) : CredentialStore {
    override suspend fun read(): AuthSnapshot = AuthSnapshot(
        installationId = null,
        installationSecret = null,
        accessToken = null,
        refreshToken = null,
        accessExpiresAtMillis = 0,
        refreshExpiresAtMillis = 0,
        selectedSessionId = null,
        activeClientRequestId = activeClientRequestId
    )

    override suspend fun saveInstallation(id: String, secret: String) = Unit
    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long
    ) = Unit

    override suspend fun clearTokens() = Unit
    override suspend fun saveSelectedSessionId(sessionId: String?) = Unit
    override suspend fun saveActiveClientRequestId(clientRequestId: String?) {
        activeClientRequestId = clientRequestId
    }
}

private fun modelConfigSession() = ChatSessionModel(
    id = "session-1",
    title = "会话",
    modelId = "chat-5.5",
    reasoningMode = "standard",
    isPinned = false,
    messagePreview = "",
    messageCount = 0,
    updatedAt = "2026-07-16T00:00:00Z"
)
