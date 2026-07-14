package com.xipian.chatxp_android.ui.screens.chat

import com.xipian.chatxp_android.MainDispatcherRule
import com.xipian.chatxp_android.data.local.AuthSnapshot
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.model.ChatMessageModel
import com.xipian.chatxp_android.data.model.ChatModel
import com.xipian.chatxp_android.data.model.ChatSessionModel
import com.xipian.chatxp_android.data.model.GenerationModel
import com.xipian.chatxp_android.data.remote.ApiException
import com.xipian.chatxp_android.data.remote.ChatStreamEvent
import com.xipian.chatxp_android.data.remote.dto.DeltaEventDto
import com.xipian.chatxp_android.data.remote.dto.DoneEventDto
import com.xipian.chatxp_android.data.remote.dto.MessageDto
import com.xipian.chatxp_android.data.remote.dto.MetaEventDto
import com.xipian.chatxp_android.data.remote.dto.SessionDto
import com.xipian.chatxp_android.data.remote.dto.UsageDto
import com.xipian.chatxp_android.data.repository.ChatDataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun newDraftMigratesToServerSessionAndAppliesFinalMessage() = runTest {
        val store = ViewModelCredentialStore()
        val viewModel = ChatViewModel(FakeChatRepository(), store)
        advanceUntilIdle()

        viewModel.onAction(ChatAction.ComposerChanged("你好"))
        viewModel.onAction(ChatAction.Send)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("session-1", state.selectedSessionId)
        assertNull(state.draftId)
        assertFalse(state.isGenerating)
        assertEquals(2, state.messages.size)
        assertEquals("Echo: 你好", state.messages.last().text)
        assertEquals(MessageStatusUi.COMPLETED, state.messages.last().status)
        assertEquals("session-1", store.snapshot.selectedSessionId)
        assertNull(store.snapshot.activeClientRequestId)
    }

    @Test
    fun staleStartupGenerationDoesNotKeepSendingDisabled() = runTest {
        val store = ViewModelCredentialStore(
            activeClientRequestId = "stale-request"
        )
        val repository = FakeChatRepository(
            generationFailure = ApiException(
                code = "GENERATION_NOT_FOUND",
                statusCode = 404,
                message = "Generation not found"
            )
        )

        val viewModel = ChatViewModel(repository, store)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isGenerating)
        assertNull(state.error)
        assertNull(store.snapshot.activeClientRequestId)
    }
}

private class FakeChatRepository(
    private val generationFailure: Throwable? = null
) : ChatDataRepository {
    override suspend fun models(): List<ChatModel> = listOf(
        ChatModel("chat-default", "5.5 均衡", null, true)
    )

    override suspend fun sessions(query: String?): List<ChatSessionModel> = emptyList()
    override suspend fun messages(sessionId: String): List<ChatMessageModel> = emptyList()

    override fun streamMessage(
        clientRequestId: String,
        clientMessageId: String,
        sessionId: String?,
        modelId: String?,
        content: String
    ): Flow<ChatStreamEvent> {
        val sessionDto = sessionDto(lastMessagePreview = "Echo: $content")
        val user = messageDto(
            id = "user-1",
            role = "user",
            content = content,
            status = "completed",
            sequence = 1,
            clientMessageId = clientMessageId
        )
        val assistant = messageDto(
            id = "assistant-1",
            role = "assistant",
            content = "Echo: $content",
            status = "completed",
            sequence = 2
        )
        return flowOf(
            ChatStreamEvent.Meta(
                MetaEventDto(
                    generationId = "generation-1",
                    clientRequestId = clientRequestId,
                    sessionCreated = true,
                    session = sessionDto,
                    userMessage = user,
                    assistantMessageId = "assistant-1"
                )
            ),
            ChatStreamEvent.Delta(DeltaEventDto("assistant-1", 1, "Echo: ")),
            ChatStreamEvent.Delta(DeltaEventDto("assistant-1", 1, "duplicate")),
            ChatStreamEvent.Done(
                DoneEventDto(
                    assistantMessage = assistant,
                    finishReason = "stop",
                    usage = UsageDto(1, 1, 2),
                    session = sessionDto
                )
            )
        )
    }

    override suspend fun generation(clientRequestId: String): GenerationModel {
        throw generationFailure ?: error("Recovery should not be used")
    }

    private fun sessionDto(lastMessagePreview: String) = SessionDto(
        id = "session-1",
        title = "你好",
        modelId = "chat-default",
        isPinned = false,
        lastMessagePreview = lastMessagePreview,
        messageCount = 2,
        createdAt = "2026-07-14T00:00:00Z",
        updatedAt = "2026-07-14T00:00:00Z"
    )

    private fun messageDto(
        id: String,
        role: String,
        content: String,
        status: String,
        sequence: Int,
        clientMessageId: String? = null
    ) = MessageDto(
        id = id,
        sessionId = "session-1",
        role = role,
        content = content,
        status = status,
        sequence = sequence,
        modelId = if (role == "assistant") "chat-default" else null,
        clientMessageId = clientMessageId,
        errorCode = null,
        promptTokens = null,
        completionTokens = null,
        createdAt = "2026-07-14T00:00:00Z",
        updatedAt = "2026-07-14T00:00:00Z"
    )
}

private class ViewModelCredentialStore(
    activeClientRequestId: String? = null
) : CredentialStore {
    var snapshot = AuthSnapshot(
        installationId = null,
        installationSecret = null,
        accessToken = null,
        refreshToken = null,
        accessExpiresAtMillis = 0,
        refreshExpiresAtMillis = 0,
        selectedSessionId = null,
        activeClientRequestId = activeClientRequestId
    )

    override suspend fun read(): AuthSnapshot = snapshot
    override suspend fun saveInstallation(id: String, secret: String) = Unit
    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long
    ) = Unit
    override suspend fun clearTokens() = Unit
    override suspend fun saveSelectedSessionId(sessionId: String?) {
        snapshot = snapshot.copy(selectedSessionId = sessionId)
    }
    override suspend fun saveActiveClientRequestId(clientRequestId: String?) {
        snapshot = snapshot.copy(activeClientRequestId = clientRequestId)
    }
}
