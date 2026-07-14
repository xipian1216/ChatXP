package com.xipian.chatxp_android.data.remote

import com.xipian.chatxp_android.data.remote.dto.ChatStreamRequestDto
import com.xipian.chatxp_android.data.remote.dto.DeltaEventDto
import com.xipian.chatxp_android.data.remote.dto.DoneEventDto
import com.xipian.chatxp_android.data.remote.dto.ErrorEnvelopeDto
import com.xipian.chatxp_android.data.remote.dto.MetaEventDto
import com.xipian.chatxp_android.data.remote.dto.StreamErrorEventDto
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

sealed interface ChatStreamEvent {
    data class Meta(val value: MetaEventDto) : ChatStreamEvent
    data class Delta(val value: DeltaEventDto) : ChatStreamEvent
    data class Done(val value: DoneEventDto) : ChatStreamEvent
    data class Error(val value: StreamErrorEventDto) : ChatStreamEvent
}

class ApiException(
    val code: String,
    val statusCode: Int,
    message: String
) : IOException(message)

class ChatStreamClient(
    private val baseUrl: String,
    private val client: OkHttpClient,
    private val json: Json
) {
    fun stream(body: ChatStreamRequestDto): Flow<ChatStreamEvent> = flow {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/api/v1/chat/streams")
            .post(
                json.encodeToString(ChatStreamRequestDto.serializer(), body)
                    .toRequestBody(JSON_MEDIA_TYPE)
            )
            .header("Accept", "text/event-stream")
            .build()
        val call = client.newCall(request)
        val completionHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw response.toApiException(json)
                val source = response.body?.source() ?: throw IOException("Empty SSE response")
                var eventName: String? = null
                val dataLines = mutableListOf<String>()

                while (currentCoroutineContext().isActive && !source.exhausted()) {
                    val line = source.readUtf8Line() ?: break
                    if (line.isEmpty()) {
                        decodeEvent(eventName, dataLines)?.let { emit(it) }
                        eventName = null
                        dataLines.clear()
                    } else if (!line.startsWith(':')) {
                        val separator = line.indexOf(':')
                        val field = if (separator >= 0) line.substring(0, separator) else line
                        val value = if (separator >= 0) {
                            line.substring(separator + 1).removePrefix(" ")
                        } else {
                            ""
                        }
                        when (field) {
                            "event" -> eventName = value
                            "data" -> dataLines += value
                        }
                    }
                }
                decodeEvent(eventName, dataLines)?.let { emit(it) }
            }
        } finally {
            completionHandle?.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private fun decodeEvent(name: String?, dataLines: List<String>): ChatStreamEvent? {
        if (name == null || dataLines.isEmpty()) return null
        val data = dataLines.joinToString("\n")
        return when (name) {
            "meta" -> ChatStreamEvent.Meta(json.decodeFromString(MetaEventDto.serializer(), data))
            "delta" -> ChatStreamEvent.Delta(json.decodeFromString(DeltaEventDto.serializer(), data))
            "done" -> ChatStreamEvent.Done(json.decodeFromString(DoneEventDto.serializer(), data))
            "error" -> ChatStreamEvent.Error(
                json.decodeFromString(StreamErrorEventDto.serializer(), data)
            )
            else -> null
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

internal fun okhttp3.Response.toApiException(json: Json): ApiException {
    val raw = body?.string().orEmpty()
    val envelope = runCatching {
        json.decodeFromString(ErrorEnvelopeDto.serializer(), raw)
    }.getOrNull()
    return ApiException(
        code = envelope?.error?.code ?: "HTTP_ERROR",
        statusCode = code,
        message = envelope?.error?.message ?: "HTTP $code"
    )
}
