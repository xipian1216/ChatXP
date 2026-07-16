from __future__ import annotations

from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any

import httpx
import pytest
from asgi_lifespan import LifespanManager
from sqlalchemy import func, select, update

from chatxp.core.config import ProviderModelConfig
from chatxp.core.time import utcnow
from chatxp.db.tables import ChatSession, Generation, Message, new_uuid
from chatxp.main import create_app
from chatxp.schemas.chat import ChatStreamRequest
from tests.conftest import authenticate, bearer, make_test_settings, parse_sse
from tests.integration.test_chat import chat_payload


async def create_completed_session(
    client: httpx.AsyncClient, headers: dict[str, str], content: str
) -> str:
    response = await client.post(
        "/api/v1/chat/streams", json=chat_payload(content), headers=headers
    )
    assert response.status_code == 200, response.text
    return str(parse_sse(response.text)[0][1]["session"]["id"])


async def test_update_session_fields_validation_and_user_isolation(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    _, client = app_client
    first_auth, _ = await authenticate(client)
    first_headers = bearer(first_auth)
    first_id = await create_completed_session(client, first_headers, "first")
    second_id = await create_completed_session(client, first_headers, "second")

    before = await client.get(f"/api/v1/sessions/{first_id}", headers=first_headers)
    old_updated_at = before.json()["data"]["updated_at"]
    renamed = await client.patch(
        f"/api/v1/sessions/{first_id}",
        json={"title": "  新标题  ", "is_pinned": True},
        headers=first_headers,
    )
    assert renamed.status_code == 200, renamed.text
    data = renamed.json()["data"]
    assert data["title"] == "新标题"
    assert data["is_pinned"] is True
    assert data["updated_at"] >= old_updated_at
    assert data["message_count"] == 2

    listed = await client.get("/api/v1/sessions", headers=first_headers)
    assert [item["id"] for item in listed.json()["data"]["items"]][:2] == [
        first_id,
        second_id,
    ]
    unpinned = await client.patch(
        f"/api/v1/sessions/{first_id}",
        json={"is_pinned": False},
        headers=first_headers,
    )
    assert unpinned.status_code == 200
    assert unpinned.json()["data"]["is_pinned"] is False

    invalid_payloads = [
        {},
        {"title": "   "},
        {"title": "x" * 101},
        {"title": None},
        {"model_id": None},
        {"reasoning_mode": None},
        {"is_pinned": None},
    ]
    for payload in invalid_payloads:
        response = await client.patch(
            f"/api/v1/sessions/{first_id}", json=payload, headers=first_headers
        )
        assert response.status_code == 400, (payload, response.text)
        assert response.json()["error"]["code"] == "VALIDATION_ERROR"

    unknown_model = await client.patch(
        f"/api/v1/sessions/{first_id}",
        json={"model_id": "unknown-model"},
        headers=first_headers,
    )
    assert unknown_model.status_code == 404
    assert unknown_model.json()["error"]["code"] == "MODEL_NOT_FOUND"

    second_auth, _ = await authenticate(client)
    second_headers = bearer(second_auth)
    hidden_update = await client.patch(
        f"/api/v1/sessions/{first_id}",
        json={"title": "not allowed"},
        headers=second_headers,
    )
    hidden_delete = await client.delete(
        f"/api/v1/sessions/{first_id}", headers=second_headers
    )
    assert hidden_update.status_code == 404
    assert hidden_update.json()["error"]["code"] == "SESSION_NOT_FOUND"
    assert hidden_delete.status_code == 404
    assert hidden_delete.json()["error"]["code"] == "SESSION_NOT_FOUND"


@pytest.mark.parametrize("active_status", ["queued", "streaming"])
async def test_delete_rejects_active_generation_then_cascades(
    app_client: tuple[Any, httpx.AsyncClient], active_status: str
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    headers = bearer(auth)
    session_id = await create_completed_session(client, headers, active_status)

    async with app.state.session_factory() as db, db.begin():
        generation = await db.scalar(
            select(Generation).where(Generation.session_id == session_id)
        )
        assert generation is not None
        generation.status = active_status

    busy = await client.delete(f"/api/v1/sessions/{session_id}", headers=headers)
    assert busy.status_code == 409
    assert busy.json()["error"]["code"] == "SESSION_BUSY"
    assert (await client.get(f"/api/v1/sessions/{session_id}", headers=headers)).status_code == 200

    terminal_status = "failed" if active_status == "queued" else "completed"
    async with app.state.session_factory() as db, db.begin():
        await db.execute(
            update(Generation)
            .where(Generation.session_id == session_id)
            .values(status=terminal_status)
        )

    deleted = await client.delete(f"/api/v1/sessions/{session_id}", headers=headers)
    assert deleted.status_code == 204
    assert deleted.content == b""
    assert (await client.get(f"/api/v1/sessions/{session_id}", headers=headers)).status_code == 404
    assert (
        await client.get(f"/api/v1/sessions/{session_id}/messages", headers=headers)
    ).status_code == 404

    async with app.state.session_factory() as db:
        message_count = await db.scalar(
            select(func.count(Message.id)).where(Message.session_id == session_id)
        )
        generation_count = await db.scalar(
            select(func.count(Generation.id)).where(Generation.session_id == session_id)
        )
    assert message_count == 0
    assert generation_count == 0


async def test_delete_empty_session(app_client: tuple[Any, httpx.AsyncClient]) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    session_id = new_uuid()
    now = utcnow()
    async with app.state.session_factory() as db, db.begin():
        db.add(
            ChatSession(
                id=session_id,
                user_id=auth["user_id"],
                title="empty",
                model_id="chat-5.5",
                reasoning_mode="standard",
                is_pinned=False,
                created_at=now,
                updated_at=now,
            )
        )

    response = await client.delete(
        f"/api/v1/sessions/{session_id}", headers=bearer(auth)
    )
    assert response.status_code == 204
    assert response.content == b""


@pytest.fixture
async def multi_model_client(
    tmp_path: Path,
) -> AsyncIterator[tuple[Any, httpx.AsyncClient]]:
    settings = make_test_settings(tmp_path / "multi-model.db")
    settings.ai_models_json = [
        ProviderModelConfig(
            id="chat-5.5",
            provider_model="deepseek-v4-flash",
            display_name="5.5",
        ),
        ProviderModelConfig(
            id="chat-5.6",
            provider_model="deepseek-v4-pro",
            display_name="5.6",
        ),
    ]
    app = create_app(settings)
    async with LifespanManager(app):
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            yield app, client


async def test_model_switch_only_changes_future_generations(
    multi_model_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = multi_model_client
    auth, _ = await authenticate(client)
    payload = chat_payload("queued with original model")
    context = await app.state.chat_coordinator.prepare(
        auth["user_id"], ChatStreamRequest.model_validate(payload)
    )
    session_id = str(context.session.id)

    switched = await client.patch(
        f"/api/v1/sessions/{session_id}",
        json={"model_id": "chat-5.6", "reasoning_mode": "advanced"},
        headers=bearer(auth),
    )
    assert switched.status_code == 200, switched.text
    assert switched.json()["data"]["model_id"] == "chat-5.6"
    assert switched.json()["data"]["reasoning_mode"] == "advanced"

    async with app.state.session_factory() as db:
        generation = await db.scalar(
            select(Generation).where(Generation.session_id == session_id)
        )
        assistant = await db.scalar(
            select(Message).where(
                Message.session_id == session_id, Message.role == "assistant"
            )
        )
    assert generation is not None
    assert generation.model_id == "chat-5.5"
    assert generation.reasoning_mode == "standard"
    assert assistant is not None
    assert assistant.model_id == "chat-5.5"
    assert assistant.reasoning_mode == "standard"

    follow_up = {
        **chat_payload("future generation"),
        "session_id": session_id,
        "model_id": None,
        "reasoning_mode": None,
    }
    next_context = await app.state.chat_coordinator.prepare(
        auth["user_id"], ChatStreamRequest.model_validate(follow_up)
    )
    assert next_context.generation.assistant_message.model_id == "chat-5.6"
    assert next_context.generation.assistant_message.reasoning_mode == "advanced"
