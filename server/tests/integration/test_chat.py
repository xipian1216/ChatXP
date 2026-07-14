from __future__ import annotations

from typing import Any
from uuid import uuid4

import httpx

from chatxp.providers.fake import FakeChatProvider
from tests.conftest import authenticate, bearer, parse_sse


def chat_payload(content: str = "帮我制定学习计划") -> dict[str, Any]:
    return {
        "client_request_id": str(uuid4()),
        "client_message_id": str(uuid4()),
        "session_id": None,
        "model_id": "chat-default",
        "content": content,
    }


async def test_chat_stream_history_search_and_idempotency(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    headers = bearer(auth)
    payload = chat_payload()

    response = await client.post("/api/v1/chat/streams", json=payload, headers=headers)
    assert response.status_code == 200, response.text
    assert response.headers["content-type"] == "text/event-stream; charset=utf-8"
    events = parse_sse(response.text)
    assert events[0][0] == "meta"
    assert events[-1][0] == "done"
    assert all(name == "delta" for name, _ in events[1:-1])
    meta = events[0][1]
    done = events[-1][1]
    assert meta["session_created"] is True
    assert done["assistant_message"]["content"] == "Echo: 帮我制定学习计划"
    assert done["assistant_message"]["status"] == "completed"
    delta_events = [event for event in events if event[0] == "delta"]
    assert [event[1]["sequence"] for event in delta_events] == list(
        range(1, len(delta_events) + 1)
    )

    session_id = meta["session"]["id"]
    sessions = await client.get("/api/v1/sessions?q=学习", headers=headers)
    assert sessions.status_code == 200
    assert sessions.json()["data"]["items"][0]["message_count"] == 2
    messages = await client.get(
        f"/api/v1/sessions/{session_id}/messages", headers=headers
    )
    assert [item["sequence"] for item in messages.json()["data"]["items"]] == [1, 2]

    repeated = await client.post("/api/v1/chat/streams", json=payload, headers=headers)
    assert [name for name, _ in parse_sse(repeated.text)] == ["meta", "done"]
    provider = app.state.provider
    assert isinstance(provider, FakeChatProvider)
    assert provider.call_count == 1

    generation = await client.get(
        f"/api/v1/generations/by-client-request/{payload['client_request_id']}",
        headers=headers,
    )
    assert generation.json()["data"]["status"] == "completed"


async def test_idempotency_mismatch_and_user_isolation(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    _, client = app_client
    first_auth, _ = await authenticate(client)
    payload = chat_payload("private conversation")
    first = await client.post(
        "/api/v1/chat/streams", json=payload, headers=bearer(first_auth)
    )
    assert first.status_code == 200, first.text
    session_id = parse_sse(first.text)[0][1]["session"]["id"]

    changed = {**payload, "content": "changed"}
    mismatch = await client.post(
        "/api/v1/chat/streams", json=changed, headers=bearer(first_auth)
    )
    assert mismatch.status_code == 409
    assert mismatch.json()["error"]["details"]["reason"] == "IDEMPOTENCY_KEY_REUSED"

    second_auth, _ = await authenticate(client)
    hidden = await client.get(
        f"/api/v1/sessions/{session_id}", headers=bearer(second_auth)
    )
    assert hidden.status_code == 404
    assert hidden.json()["error"]["code"] == "SESSION_NOT_FOUND"
