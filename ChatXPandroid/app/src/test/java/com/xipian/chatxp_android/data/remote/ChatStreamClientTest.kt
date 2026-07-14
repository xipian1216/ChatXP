package com.xipian.chatxp_android.data.remote

import com.xipian.chatxp_android.data.remote.dto.ChatStreamRequestDto
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ChatStreamClientTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesChunkedSseFramesAndIgnoresHeartbeat() = runBlocking {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(SSE_BODY, 3)
        )
        val client = ChatStreamClient(
            baseUrl = server.url("/").toString(),
            client = OkHttpClient(),
            json = Json { ignoreUnknownKeys = true }
        )

        val events = client.stream(
            ChatStreamRequestDto(
                clientRequestId = "request",
                clientMessageId = "client-message",
                sessionId = null,
                modelId = "chat-default",
                content = "你好"
            )
        ).toList()

        assertEquals(3, events.size)
        assertTrue(events[0] is ChatStreamEvent.Meta)
        assertEquals("你", (events[1] as ChatStreamEvent.Delta).value.contentDelta)
        assertEquals("你好", (events[2] as ChatStreamEvent.Done).value.assistantMessage.content)
    }

    private companion object {
        val SSE_BODY = """
            : ping

            id: 1
            event: meta
            data: {"generation_id":"generation","client_request_id":"request","session_created":true,"session":{"id":"session","title":"你好","model_id":"chat-default","is_pinned":false,"last_message_preview":null,"message_count":2,"created_at":"2026-07-14T00:00:00Z","updated_at":"2026-07-14T00:00:00Z"},"user_message":{"id":"user-message","session_id":"session","role":"user","content":"你好","status":"completed","sequence":1,"model_id":null,"client_message_id":"client-message","error_code":null,"prompt_tokens":null,"completion_tokens":null,"created_at":"2026-07-14T00:00:00Z","updated_at":"2026-07-14T00:00:00Z"},"assistant_message_id":"assistant-message"}

            id: 2
            event: delta
            data: {"assistant_message_id":"assistant-message",
            data: "sequence":1,"content_delta":"你"}

            id: 3
            event: done
            data: {"assistant_message":{"id":"assistant-message","session_id":"session","role":"assistant","content":"你好","status":"completed","sequence":2,"model_id":"chat-default","client_message_id":null,"error_code":null,"prompt_tokens":1,"completion_tokens":1,"created_at":"2026-07-14T00:00:00Z","updated_at":"2026-07-14T00:00:00Z"},"finish_reason":"stop","usage":{"prompt_tokens":1,"completion_tokens":1,"total_tokens":2},"session":{"id":"session","title":"你好","model_id":"chat-default","is_pinned":false,"last_message_preview":"你好","message_count":2,"created_at":"2026-07-14T00:00:00Z","updated_at":"2026-07-14T00:00:00Z"}}

        """.trimIndent() + "\n\n"
    }
}
