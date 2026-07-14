package com.xipian.chatxp_android.ui.screens.chat

import com.xipian.chatxp_android.data.local.AuthSnapshot
import com.xipian.chatxp_android.data.local.CredentialStore
import com.xipian.chatxp_android.data.model.ChatMessageModel
import com.xipian.chatxp_android.data.model.ChatModel
import com.xipian.chatxp_android.data.model.ChatSessionModel
import com.xipian.chatxp_android.data.model.GenerationModel
import com.xipian.chatxp_android.data.remote.ChatStreamEvent
import com.xipian.chatxp_android.data.repository.ChatDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelSessionActionsTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun renameAndPinSurviveSessionRefresh() = runTest(dispatcher) {
        val repository = SessionActionsRepository()
        val viewModel = ChatViewModel(repository, SessionActionsCredentialStore())
        advanceUntilIdle()

        viewModel.onAction(ChatAction.RenameSession("session-2", "  新标题  "))
        viewModel.onAction(ChatAction.TogglePinSession("session-2"))
        viewModel.onAction(ChatAction.OpenDrawer)
        advanceUntilIdle()

        val sessions = viewModel.uiState.value.sessions
        assertEquals("session-2", sessions.first().sessionId)
        assertEquals("新标题", sessions.first().sessionTitle)
        assertTrue(sessions.first().isPinned)
    }

    @Test
    fun deletingSelectedSessionSelectsFirstRemainingAndKeepsDrawerOpen() = runTest(dispatcher) {
        val repository = SessionActionsRepository()
        val credentialStore = SessionActionsCredentialStore()
        val viewModel = ChatViewModel(repository, credentialStore)
        advanceUntilIdle()
        viewModel.onAction(ChatAction.OpenDrawer)
        advanceUntilIdle()

        viewModel.onAction(ChatAction.DeleteSession("session-1"))
        advanceUntilIdle()
        viewModel.onAction(ChatAction.OpenDrawer)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(listOf("session-2"), state.sessions.map(ChatSession::sessionId))
        assertEquals("session-2", state.selectedSessionId)
        assertTrue(state.isDrawerOpen)
        assertEquals("session-2", credentialStore.selectedSessionId)
    }

    @Test
    fun deletingLastSessionCreatesBlankDraft() = runTest(dispatcher) {
        val repository = SessionActionsRepository(
            sessions = listOf(sessionModel("session-1", "第一条"))
        )
        val credentialStore = SessionActionsCredentialStore()
        val viewModel = ChatViewModel(repository, credentialStore)
        advanceUntilIdle()

        viewModel.onAction(ChatAction.DeleteSession("session-1"))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.sessions.isEmpty())
        assertNull(state.selectedSessionId)
        assertTrue(state.draftId?.startsWith("draft-") == true)
        assertFalse(state.isGenerating)
        assertNull(credentialStore.selectedSessionId)
    }
}

private class SessionActionsRepository(
    var sessions: List<ChatSessionModel> = listOf(
        sessionModel("session-1", "第一条"),
        sessionModel("session-2", "第二条")
    )
) : ChatDataRepository {
    override suspend fun models(): List<ChatModel> = listOf(
        ChatModel("model-1", "Model", null, true)
    )

    override suspend fun sessions(query: String?): List<ChatSessionModel> = sessions

    override suspend fun messages(sessionId: String): List<ChatMessageModel> = emptyList()

    override fun streamMessage(
        clientRequestId: String,
        clientMessageId: String,
        sessionId: String?,
        modelId: String?,
        content: String
    ): Flow<ChatStreamEvent> = emptyFlow()

    override suspend fun generation(clientRequestId: String): GenerationModel =
        error("Not used by session action tests")
}

private class SessionActionsCredentialStore : CredentialStore {
    var selectedSessionId: String? = "session-1"

    override suspend fun read(): AuthSnapshot = AuthSnapshot(
        installationId = null,
        installationSecret = null,
        accessToken = null,
        refreshToken = null,
        accessExpiresAtMillis = 0,
        refreshExpiresAtMillis = 0,
        selectedSessionId = selectedSessionId,
        activeClientRequestId = null
    )

    override suspend fun saveInstallation(id: String, secret: String) = Unit

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        accessExpiresAtMillis: Long,
        refreshExpiresAtMillis: Long
    ) = Unit

    override suspend fun clearTokens() = Unit

    override suspend fun saveSelectedSessionId(sessionId: String?) {
        selectedSessionId = sessionId
    }

    override suspend fun saveActiveClientRequestId(clientRequestId: String?) = Unit
}

private fun sessionModel(id: String, title: String) = ChatSessionModel(
    id = id,
    title = title,
    modelId = "model-1",
    isPinned = false,
    messagePreview = title,
    messageCount = 1,
    updatedAt = "2026-07-14T10:00:00Z"
)
