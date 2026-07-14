from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Sequence
from pathlib import Path
from typing import Any

import httpx
from asgi_lifespan import LifespanManager
from sqlalchemy import select

from chatxp.db.tables import Generation, Message
from chatxp.main import create_app
from chatxp.providers.base import (
    ProviderDelta,
    ProviderEvent,
    ProviderMessage,
    ProviderUnavailableError,
)
from chatxp.providers.fake import FakeChatProvider
from chatxp.schemas.chat import ChatStreamRequest
from chatxp.services.chat import ChatCoordinator
from tests.conftest import authenticate, bearer, make_test_settings, parse_sse
from tests.integration.test_chat import chat_payload


class PartialFailureProvider:
    async def stream_chat(
        self, provider_model: str, messages: Sequence[ProviderMessage]
    ) -> AsyncIterator[ProviderEvent]:
        del provider_model, messages
        yield ProviderDelta("partial")
        raise ProviderUnavailableError("test failure")


async def test_generation_continues_without_a_stream_subscriber(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    coordinator = ChatCoordinator(
        app.state.session_factory,
        app.state.model_catalog,
        FakeChatProvider(delay_seconds=0.001),
    )
    payload = chat_payload("background")
    context = await coordinator.prepare(
        auth["user_id"], ChatStreamRequest.model_validate(payload)
    )
    await coordinator.ensure_running(str(context.generation.id))

    for _ in range(100):
        state = await coordinator.generation_by_client_request(
            auth["user_id"], payload["client_request_id"]
        )
        if state.status == "completed":
            break
        await asyncio.sleep(0.002)
    else:
        raise AssertionError("background generation did not complete")

    assert state.assistant_message.content == "Echo: background"
    await coordinator.shutdown()


async def test_partial_provider_failure_is_streamed_and_recoverable(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    app.state.chat_coordinator = ChatCoordinator(
        app.state.session_factory, app.state.model_catalog, PartialFailureProvider()
    )
    payload = chat_payload("will fail")
    response = await client.post(
        "/api/v1/chat/streams", json=payload, headers=bearer(auth)
    )
    events = parse_sse(response.text)
    assert [name for name, _ in events] == ["meta", "delta", "error"]
    assert events[-1][1]["code"] == "PROVIDER_UNAVAILABLE"

    recovered = await client.get(
        f"/api/v1/generations/by-client-request/{payload['client_request_id']}",
        headers=bearer(auth),
    )
    generation = recovered.json()["data"]
    assert generation["status"] == "failed"
    assert generation["assistant_message"]["content"] == "partial"
    assert generation["assistant_message"]["error_code"] == "PROVIDER_UNAVAILABLE"


async def test_startup_repairs_interrupted_generation(tmp_path: Path) -> None:
    settings = make_test_settings(tmp_path / "restart.db")
    first_app = create_app(settings)
    async with LifespanManager(first_app):
        transport = httpx.ASGITransport(app=first_app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            auth, _ = await authenticate(client)
            payload = chat_payload("restart")
            response = await client.post(
                "/api/v1/chat/streams", json=payload, headers=bearer(auth)
            )
            assert response.status_code == 200
            async with first_app.state.session_factory() as db, db.begin():
                generation = await db.scalar(
                    select(Generation).where(
                        Generation.client_request_id == payload["client_request_id"]
                    )
                )
                assert generation is not None
                assistant = await db.get(Message, generation.assistant_message_id)
                assert assistant is not None
                generation.status = "streaming"
                assistant.status = "streaming"

    second_app = create_app(settings)
    async with LifespanManager(second_app):
        transport = httpx.ASGITransport(app=second_app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            recovered = await client.get(
                f"/api/v1/generations/by-client-request/{payload['client_request_id']}",
                headers=bearer(auth),
            )
            assert recovered.status_code == 200
            data = recovered.json()["data"]
            assert data["status"] == "failed"
            assert data["error_code"] == "GENERATION_INTERRUPTED"
            assert data["assistant_message"]["status"] == "failed"


async def test_message_and_session_cursor_pagination(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    _, client = app_client
    auth, _ = await authenticate(client)
    headers = bearer(auth)
    session_ids: list[str] = []
    for label in ("one", "two", "three"):
        response = await client.post(
            "/api/v1/chat/streams", json=chat_payload(label), headers=headers
        )
        session_ids.append(parse_sse(response.text)[0][1]["session"]["id"])

    first_page = await client.get("/api/v1/sessions?limit=2", headers=headers)
    first_data = first_page.json()["data"]
    assert len(first_data["items"]) == 2
    assert first_data["has_more"] is True
    second_page = await client.get(
        "/api/v1/sessions",
        params={"limit": 2, "cursor": first_data["next_cursor"]},
        headers=headers,
    )
    assert len(second_page.json()["data"]["items"]) == 1

    existing_payload = {
        **chat_payload("follow up"),
        "session_id": session_ids[-1],
        "model_id": None,
    }
    await client.post("/api/v1/chat/streams", json=existing_payload, headers=headers)
    latest_messages = await client.get(
        f"/api/v1/sessions/{session_ids[-1]}/messages?limit=2", headers=headers
    )
    latest_data = latest_messages.json()["data"]
    assert [item["sequence"] for item in latest_data["items"]] == [3, 4]
    older_messages = await client.get(
        f"/api/v1/sessions/{session_ids[-1]}/messages",
        params={"limit": 2, "before": latest_data["next_before"]},
        headers=headers,
    )
    assert [item["sequence"] for item in older_messages.json()["data"]["items"]] == [1, 2]
