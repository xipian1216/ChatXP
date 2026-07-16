package com.xipian.chatxp_android.data.model

data class ChatModel(
    val id: String,
    val displayName: String,
    val description: String?,
    val isDefault: Boolean,
    val reasoningModes: List<String> = listOf(DEFAULT_REASONING_MODE)
)

data class ChatSessionModel(
    val id: String,
    val title: String,
    val modelId: String,
    val reasoningMode: String = DEFAULT_REASONING_MODE,
    val isPinned: Boolean,
    val messagePreview: String?,
    val messageCount: Int,
    val updatedAt: String
)

enum class MessageRole { USER, ASSISTANT }
enum class MessageStatus { STREAMING, COMPLETED, FAILED }

data class ChatMessageModel(
    val id: String,
    val sessionId: String,
    val role: MessageRole,
    val content: String,
    val status: MessageStatus,
    val sequence: Int,
    val modelId: String? = null,
    val reasoningMode: String? = null,
    val errorCode: String? = null
)

data class GenerationModel(
    val clientRequestId: String,
    val status: String,
    val sessionId: String,
    val assistantMessage: ChatMessageModel,
    val modelId: String? = null,
    val reasoningMode: String = DEFAULT_REASONING_MODE,
    val errorCode: String?
)

const val DEFAULT_MODEL_ID = "chat-5.5"
const val DEFAULT_REASONING_MODE = "standard"
