package com.xipian.chatxp_android.data.model

data class ChatModel(
    val id: String,
    val displayName: String,
    val description: String?,
    val isDefault: Boolean
)

data class ChatSessionModel(
    val id: String,
    val title: String,
    val modelId: String,
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
    val errorCode: String? = null
)

data class GenerationModel(
    val clientRequestId: String,
    val status: String,
    val sessionId: String,
    val assistantMessage: ChatMessageModel,
    val errorCode: String?
)
