from __future__ import annotations

import base64
import secrets
from collections.abc import AsyncIterator
from pathlib import Path
from typing import Any
from uuid import uuid4

import httpx
import pytest
from asgi_lifespan import LifespanManager

from chatxp.core.config import Settings
from chatxp.main import create_app


def make_test_settings(database_path: Path) -> Settings:
    return Settings(
        database_url=f"sqlite+aiosqlite:///{database_path}",
        jwt_secret="test-jwt-secret-that-is-at-least-32-bytes",
        token_hash_secret="test-token-hash-secret-at-least-32-bytes",
        chat_provider="fake",
    )


@pytest.fixture
async def app_client(tmp_path: Path) -> AsyncIterator[tuple[Any, httpx.AsyncClient]]:
    app = create_app(make_test_settings(tmp_path / "test.db"))
    async with LifespanManager(app):
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
            yield app, client


def installation_payload() -> dict[str, str]:
    secret = base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode()
    return {
        "installation_id": str(uuid4()),
        "installation_secret": secret,
        "platform": "android",
        "app_version": "1.0.0",
    }


async def authenticate(
    client: httpx.AsyncClient, payload: dict[str, str] | None = None
) -> tuple[dict[str, Any], dict[str, str]]:
    request_payload = payload or installation_payload()
    response = await client.post("/api/v1/auth/anonymous", json=request_payload)
    assert response.status_code == 200, response.text
    return response.json()["data"], request_payload


def bearer(token_data: dict[str, Any]) -> dict[str, str]:
    return {"Authorization": f"Bearer {token_data['access_token']}"}


def parse_sse(raw: str) -> list[tuple[str, dict[str, Any]]]:
    events: list[tuple[str, dict[str, Any]]] = []
    for frame in raw.split("\n\n"):
        if not frame or frame.startswith(":"):
            continue
        event_name = ""
        data = ""
        for line in frame.splitlines():
            if line.startswith("event: "):
                event_name = line.removeprefix("event: ")
            elif line.startswith("data: "):
                data = line.removeprefix("data: ")
        if event_name and data:
            import json

            events.append((event_name, json.loads(data)))
    return events
