from __future__ import annotations

from typing import Any

import httpx
import pytest

from chatxp.providers.fake import FakeChatProvider
from tests.conftest import authenticate, bearer, parse_sse
from tests.integration.test_chat import chat_payload


@pytest.mark.parametrize(
    ("model_id", "reasoning_mode", "provider_model"),
    [
        ("chat-5.5", "standard", "deepseek-v4-flash"),
        ("chat-5.5", "advanced", "deepseek-v4-flash"),
        ("chat-5.6", "standard", "deepseek-v4-pro"),
        ("chat-5.6", "advanced", "deepseek-v4-pro"),
    ],
)
async def test_all_model_reasoning_combinations_are_persisted_and_mapped(
    app_client: tuple[Any, httpx.AsyncClient],
    model_id: str,
    reasoning_mode: str,
    provider_model: str,
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    payload = {
        **chat_payload(f"{model_id} {reasoning_mode}"),
        "model_id": model_id,
        "reasoning_mode": reasoning_mode,
    }
    response = await client.post(
        "/api/v1/chat/streams", json=payload, headers=bearer(auth)
    )
    assert response.status_code == 200, response.text
    events = parse_sse(response.text)
    meta = events[0][1]
    done = events[-1][1]
    assert meta["session"]["model_id"] == model_id
    assert meta["session"]["reasoning_mode"] == reasoning_mode
    assert meta["user_message"]["model_id"] is None
    assert meta["user_message"]["reasoning_mode"] is None
    assert done["assistant_message"]["model_id"] == model_id
    assert done["assistant_message"]["reasoning_mode"] == reasoning_mode

    history = await client.get(
        f"/api/v1/sessions/{meta['session']['id']}/messages", headers=bearer(auth)
    )
    assert [item["reasoning_mode"] for item in history.json()["data"]["items"]] == [
        None,
        reasoning_mode,
    ]
    recovery = await client.get(
        f"/api/v1/generations/by-client-request/{payload['client_request_id']}",
        headers=bearer(auth),
    )
    generation = recovery.json()["data"]
    assert generation["reasoning_mode"] == reasoning_mode
    assert generation["assistant_message"]["model_id"] == model_id

    provider = app.state.provider
    assert isinstance(provider, FakeChatProvider)
    assert provider.calls == [(provider_model, reasoning_mode)]
    assert "deepseek-v4" not in response.text
    assert "deepseek-v4" not in history.text
    assert "deepseek-v4" not in recovery.text


async def test_existing_session_uses_patched_model_and_reasoning_defaults(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    headers = bearer(auth)
    first = await client.post(
        "/api/v1/chat/streams", json=chat_payload("first"), headers=headers
    )
    session_id = parse_sse(first.text)[0][1]["session"]["id"]

    reasoning_patch = await client.patch(
        f"/api/v1/sessions/{session_id}",
        json={"reasoning_mode": "advanced"},
        headers=headers,
    )
    assert reasoning_patch.status_code == 200
    assert reasoning_patch.json()["data"]["model_id"] == "chat-5.5"
    assert reasoning_patch.json()["data"]["reasoning_mode"] == "advanced"

    follow_up = {
        **chat_payload("follow up"),
        "session_id": session_id,
        "model_id": None,
        "reasoning_mode": None,
    }
    second = await client.post(
        "/api/v1/chat/streams", json=follow_up, headers=headers
    )
    assistant = parse_sse(second.text)[-1][1]["assistant_message"]
    assert assistant["model_id"] == "chat-5.5"
    assert assistant["reasoning_mode"] == "advanced"

    model_patch = await client.patch(
        f"/api/v1/sessions/{session_id}",
        json={"model_id": "chat-5.6"},
        headers=headers,
    )
    assert model_patch.status_code == 200
    assert model_patch.json()["data"]["model_id"] == "chat-5.6"
    assert model_patch.json()["data"]["reasoning_mode"] == "advanced"

    provider = app.state.provider
    assert isinstance(provider, FakeChatProvider)
    assert provider.calls == [
        ("deepseek-v4-flash", "standard"),
        ("deepseek-v4-flash", "advanced"),
    ]


async def test_new_session_validation_and_configuration_idempotency(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = app_client
    auth, _ = await authenticate(client)
    headers = bearer(auth)
    base = chat_payload("idempotent configuration")

    missing_reasoning = {**base}
    missing_reasoning.pop("reasoning_mode")
    response = await client.post(
        "/api/v1/chat/streams", json=missing_reasoning, headers=headers
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"

    invalid_reasoning = {**base, "reasoning_mode": "unsupported"}
    response = await client.post(
        "/api/v1/chat/streams", json=invalid_reasoning, headers=headers
    )
    assert response.status_code == 400
    assert response.json()["error"]["code"] == "VALIDATION_ERROR"

    unknown_model = {**base, "model_id": "unknown-model"}
    response = await client.post(
        "/api/v1/chat/streams", json=unknown_model, headers=headers
    )
    assert response.status_code == 404
    assert response.json()["error"]["code"] == "MODEL_NOT_FOUND"

    completed = await client.post(
        "/api/v1/chat/streams", json=base, headers=headers
    )
    assert completed.status_code == 200
    provider = app.state.provider
    assert isinstance(provider, FakeChatProvider)
    assert provider.call_count == 1

    retry_without_optional_configuration = {
        **base,
        "model_id": None,
        "reasoning_mode": None,
    }
    retry = await client.post(
        "/api/v1/chat/streams",
        json=retry_without_optional_configuration,
        headers=headers,
    )
    assert retry.status_code == 200
    assert provider.call_count == 1

    for changed in (
        {**base, "reasoning_mode": "advanced"},
        {**base, "model_id": "chat-5.6"},
    ):
        mismatch = await client.post(
            "/api/v1/chat/streams", json=changed, headers=headers
        )
        assert mismatch.status_code == 409
        assert mismatch.json()["error"]["code"] == "VALIDATION_ERROR"
        assert mismatch.json()["error"]["details"]["reason"] == "IDEMPOTENCY_KEY_REUSED"
    assert provider.call_count == 1
