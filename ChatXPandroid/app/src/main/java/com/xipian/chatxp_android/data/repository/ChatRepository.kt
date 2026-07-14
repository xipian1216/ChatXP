package com.xipian.chatxp_android.data.repository

import com.xipian.chatxp_android.data.model.ChatMessageModel
import com.xipian.chatxp_android.data.model.ChatModel
import com.xipian.chatxp_android.data.model.ChatSessionModel
import com.xipian.chatxp_android.data.model.GenerationModel
import com.xipian.chatxp_android.data.model.MessageRole
import com.xipian.chatxp_android.data.model.MessageStatus
import com.xipian.chatxp_android.data.remote.ApiException
import com.xipian.chatxp_android.data.remote.ChatStreamClient
import com.xipian.chatxp_android.data.remote.ChatStreamEvent
import com.xipian.chatxp_android.data.remote.ChatXpApi
import com.xipian.chatxp_android.data.remote.dto.ChatStreamRequestDto
import com.xipian.chatxp_android.data.remote.dto.ErrorEnvelopeDto
import com.xipian.chatxp_android.data.remote.dto.MessageDto
import com.xipian.chatxp_android.data.remote.dto.SessionDto
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import retrofit2.HttpException

interface ChatDataRepository {
    suspend fun models(): List<ChatModel>
    suspend fun sessions(query: String? = null): List<ChatSessionModel>
    suspend fun messages(sessionId: String): List<ChatMessageModel>
    fun streamMessage(
        clientRequestId: String,
        clientMessageId: String,
        sessionId: String?,
        modelId: String?,
        content: String
    ): Flow<ChatStreamEvent>
    suspend fun generation(clientRequestId: String): GenerationModel
}

class ChatRepository(
    private val api: ChatXpApi,
    private val streamClient: ChatStreamClient,
    private val json: Json
) : ChatDataRepository {
    override suspend fun models(): List<ChatModel> = apiCall {
        api.models().data.items.map {
            ChatModel(it.id, it.displayName, it.description, it.isDefault)
        }
    }

    override suspend fun sessions(query: String?): List<ChatSessionModel> = apiCall {
        api.sessions(query = query?.takeIf(String::isNotBlank)).data.items.map(SessionDto::toModel)
    }

    override suspend fun messages(sessionId: String): List<ChatMessageModel> = apiCall {
        api.messages(sessionId).data.items.map(MessageDto::toModel)
    }

    override fun streamMessage(
        clientRequestId: String,
        clientMessageId: String,
        sessionId: String?,
        modelId: String?,
        content: String
    ): Flow<ChatStreamEvent> = streamClient.stream(
        ChatStreamRequestDto(
            clientRequestId = clientRequestId,
            clientMessageId = clientMessageId,
            sessionId = sessionId,
            modelId = modelId,
            content = content
        )
    )

    override suspend fun generation(clientRequestId: String): GenerationModel = apiCall {
        api.generation(clientRequestId).data.let {
            GenerationModel(
                clientRequestId = it.clientRequestId,
                status = it.status,
                sessionId = it.sessionId,
                assistantMessage = it.assistantMessage.toModel(),
                errorCode = it.errorCode
            )
        }
    }

    private suspend fun <T> apiCall(block: suspend () -> T): T {
        try {
            return block()
        } catch (error: HttpException) {
            val raw = error.response()?.errorBody()?.string().orEmpty()
            val envelope = runCatching {
                json.decodeFromString(ErrorEnvelopeDto.serializer(), raw)
            }.getOrNull()
            throw ApiException(
                code = envelope?.error?.code ?: "HTTP_ERROR",
                statusCode = error.code(),
                message = envelope?.error?.message ?: error.message()
            )
        }
    }
}

fun SessionDto.toModel() = ChatSessionModel(
    id = id,
    title = title,
    modelId = modelId,
    isPinned = isPinned,
    messagePreview = lastMessagePreview,
    messageCount = messageCount,
    updatedAt = updatedAt
)

fun MessageDto.toModel() = ChatMessageModel(
    id = id,
    sessionId = sessionId,
    role = if (role == "user") MessageRole.USER else MessageRole.ASSISTANT,
    content = content,
    status = when (status) {
        "streaming" -> MessageStatus.STREAMING
        "failed" -> MessageStatus.FAILED
        else -> MessageStatus.COMPLETED
    },
    sequence = sequence,
    errorCode = errorCode
)
