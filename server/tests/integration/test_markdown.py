from __future__ import annotations

from collections.abc import AsyncIterator, Sequence
from typing import Any

import httpx

from chatxp.providers.base import (
    ProviderDelta,
    ProviderDone,
    ProviderEvent,
    ProviderMessage,
    ProviderUsage,
)
from chatxp.services.chat import ChatCoordinator
from tests.conftest import authenticate, bearer, parse_sse
from tests.integration.test_chat import chat_payload

MARKDOWN_USER = """\

    print("leading indentation")

# 用户标题

- **粗体**
- [安全链接](https://example.com)

| 列 A | 列 B |
| --- | --- |
| 中文 | 😀 |

末尾保留两个空格  
"""

MARKDOWN_RESPONSE_CHUNKS = [
    "# 回",
    "复标题\n\n> 引用\n\n**粗",
    "体** 与 `inline`\n\n```py",
    "thon\nprint(\"你好 😀\")\n",
    "```\n\n| A | B |\n| --- | --- |\n| 1 | 2 |",
]
MARKDOWN_RESPONSE = "".join(MARKDOWN_RESPONSE_CHUNKS)


class MarkdownProvider:
    def __init__(self) -> None:
        self.call_count = 0
        self.received_messages: list[ProviderMessage] = []

    async def stream_chat(
        self, provider_model: str, messages: Sequence[ProviderMessage]
    ) -> AsyncIterator[ProviderEvent]:
        del provider_model
        self.call_count += 1
        self.received_messages = list(messages)
        for chunk in MARKDOWN_RESPONSE_CHUNKS:
            yield ProviderDelta(chunk)
        yield ProviderDone("stop", ProviderUsage(20, 30))


async def test_markdown_is_preserved_across_stream_storage_and_recovery(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    headers = bearer(auth)
    provider = MarkdownProvider()
    coordinator = ChatCoordinator(
        app.state.session_factory, app.state.model_catalog, provider
    )
    app.state.chat_coordinator = coordinator
    payload = chat_payload(MARKDOWN_USER)

    try:
        response = await client.post("/api/v1/chat/streams", json=payload, headers=headers)
        assert response.status_code == 200, response.text
        events = parse_sse(response.text)
        assert [name for name, _ in events] == [
            "meta",
            *("delta" for _ in MARKDOWN_RESPONSE_CHUNKS),
            "done",
        ]
        meta = events[0][1]
        deltas = [data["content_delta"] for name, data in events if name == "delta"]
        done = events[-1][1]

        assert meta["user_message"]["content"] == MARKDOWN_USER
        assert provider.received_messages[-1].content == MARKDOWN_USER
        assert deltas == MARKDOWN_RESPONSE_CHUNKS
        assert "".join(deltas) == MARKDOWN_RESPONSE
        assert done["assistant_message"]["content"] == MARKDOWN_RESPONSE

        session_id = meta["session"]["id"]
        history = await client.get(
            f"/api/v1/sessions/{session_id}/messages", headers=headers
        )
        assert [item["content"] for item in history.json()["data"]["items"]] == [
            MARKDOWN_USER,
            MARKDOWN_RESPONSE,
        ]
        recovery = await client.get(
            f"/api/v1/generations/by-client-request/{payload['client_request_id']}",
            headers=headers,
        )
        assert recovery.json()["data"]["assistant_message"]["content"] == MARKDOWN_RESPONSE

        retry_payload = {**payload, "content": MARKDOWN_USER.strip()}
        repeated = await client.post(
            "/api/v1/chat/streams", json=retry_payload, headers=headers
        )
        assert repeated.status_code == 200, repeated.text
        assert [name for name, _ in parse_sse(repeated.text)] == ["meta", "done"]
        assert provider.call_count == 1
    finally:
        await coordinator.shutdown()
