package com.xipian.chatxp_android.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class DataEnvelopeDto<T>(val data: T)

@Serializable
data class ErrorEnvelopeDto(val error: ApiErrorDto)

@Serializable
data class ApiErrorDto(
    val code: String,
    val message: String,
    @SerialName("request_id") val requestId: String,
    val details: Map<String, JsonElement> = emptyMap()
)

@Serializable
data class AnonymousAuthRequestDto(
    @SerialName("installation_id") val installationId: String,
    @SerialName("installation_secret") val installationSecret: String,
    val platform: String = "android",
    @SerialName("app_version") val appVersion: String
)

@Serializable
data class RefreshRequestDto(@SerialName("refresh_token") val refreshToken: String)

@Serializable
data class RegisterRequestDto(
    @SerialName("display_name") val displayName: String,
    val email: String,
    val password: String
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class AuthUserDto(
    val id: String,
    @SerialName("account_type") val accountType: String,
    @SerialName("display_name") val displayName: String? = null,
    val email: String? = null,
    @SerialName("avatar_text") val avatarText: String? = null
)

@Serializable
data class TokenDto(
    @SerialName("user_id") val userId: String,
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("refresh_expires_in") val refreshExpiresIn: Long,
    val user: AuthUserDto? = null
)

@Serializable
data class ModelListDto(val items: List<ModelDto>)

@Serializable
data class ModelDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    val description: String?,
    @SerialName("is_default") val isDefault: Boolean,
    val capabilities: ModelCapabilitiesDto
)

@Serializable
data class ModelCapabilitiesDto(
    val streaming: Boolean,
    val attachments: Boolean,
    @SerialName("reasoning_modes")
    val reasoningModes: List<String> = listOf("standard")
)

@Serializable
data class SessionListDto(
    val items: List<SessionDto>,
    @SerialName("next_cursor") val nextCursor: String?,
    @SerialName("has_more") val hasMore: Boolean
)

@Serializable
data class SessionDto(
    val id: String,
    val title: String,
    @SerialName("model_id") val modelId: String,
    @SerialName("reasoning_mode") val reasoningMode: String = "standard",
    @SerialName("is_pinned") val isPinned: Boolean,
    @SerialName("last_message_preview") val lastMessagePreview: String?,
    @SerialName("message_count") val messageCount: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class MessageListDto(
    val items: List<MessageDto>,
    @SerialName("next_before") val nextBefore: String?,
    @SerialName("has_more") val hasMore: Boolean
)

@Serializable
data class MessageDto(
    val id: String,
    @SerialName("session_id") val sessionId: String,
    val role: String,
    val content: String,
    val status: String,
    val sequence: Int,
    @SerialName("model_id") val modelId: String?,
    @SerialName("reasoning_mode") val reasoningMode: String? = null,
    @SerialName("client_message_id") val clientMessageId: String?,
    @SerialName("error_code") val errorCode: String?,
    @SerialName("prompt_tokens") val promptTokens: Int?,
    @SerialName("completion_tokens") val completionTokens: Int?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class ChatStreamRequestDto(
    @SerialName("client_request_id") val clientRequestId: String,
    @SerialName("client_message_id") val clientMessageId: String,
    @SerialName("session_id") val sessionId: String?,
    @SerialName("model_id") val modelId: String?,
    @SerialName("reasoning_mode") val reasoningMode: String? = null,
    val content: String
)

@Serializable
data class SessionUpdateRequestDto(
    @SerialName("model_id") val modelId: String? = null,
    @SerialName("reasoning_mode") val reasoningMode: String? = null
)

@Serializable
data class MetaEventDto(
    @SerialName("generation_id") val generationId: String,
    @SerialName("client_request_id") val clientRequestId: String,
    @SerialName("session_created") val sessionCreated: Boolean,
    val session: SessionDto,
    @SerialName("user_message") val userMessage: MessageDto,
    @SerialName("assistant_message_id") val assistantMessageId: String
)

@Serializable
data class DeltaEventDto(
    @SerialName("assistant_message_id") val assistantMessageId: String,
    val sequence: Int,
    @SerialName("content_delta") val contentDelta: String
)

@Serializable
data class DoneEventDto(
    @SerialName("assistant_message") val assistantMessage: MessageDto,
    @SerialName("finish_reason") val finishReason: String,
    val usage: UsageDto?,
    val session: SessionDto
)

@Serializable
data class UsageDto(
    @SerialName("prompt_tokens") val promptTokens: Int,
    @SerialName("completion_tokens") val completionTokens: Int,
    @SerialName("total_tokens") val totalTokens: Int
)

@Serializable
data class StreamErrorEventDto(
    val code: String,
    val message: String,
    val retryable: Boolean,
    @SerialName("assistant_message_id") val assistantMessageId: String
)

@Serializable
data class GenerationDto(
    val id: String,
    @SerialName("client_request_id") val clientRequestId: String,
    val status: String,
    @SerialName("session_id") val sessionId: String,
    @SerialName("user_message_id") val userMessageId: String,
    @SerialName("assistant_message") val assistantMessage: MessageDto,
    @SerialName("reasoning_mode") val reasoningMode: String = "standard",
    @SerialName("error_code") val errorCode: String?,
    @SerialName("error_message") val errorMessage: String?,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String
)
