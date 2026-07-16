from __future__ import annotations

from pathlib import Path
from typing import Any
from uuid import uuid4

import httpx
from asgi_lifespan import LifespanManager
from sqlalchemy import select

from chatxp.core.security import verify_password
from chatxp.core.time import utcnow
from chatxp.db.tables import ChatSession, Generation, Message, User
from chatxp.main import create_app
from tests.conftest import authenticate, bearer, make_test_settings


async def _chat(
    client: httpx.AsyncClient,
    token: dict[str, Any],
    content: str,
    *,
    client_request_id: str | None = None,
    client_message_id: str | None = None,
) -> None:
    response = await client.post(
        "/api/v1/chat/streams",
        headers=bearer(token),
        json={
            "client_request_id": client_request_id or str(uuid4()),
            "client_message_id": client_message_id or str(uuid4()),
            "model_id": "chat-5.5",
            "reasoning_mode": "standard",
            "content": content,
        },
    )
    assert response.status_code == 200, response.text
    assert "event: done" in response.text


async def _session_titles(
    client: httpx.AsyncClient, token: dict[str, Any]
) -> set[str]:
    response = await client.get("/api/v1/sessions", headers=bearer(token))
    assert response.status_code == 200, response.text
    return {item["title"] for item in response.json()["data"]["items"]}


async def _insert_active_generation(app: Any, user_id: str) -> str:
    now = utcnow()
    session_id = str(uuid4())
    user_message_id = str(uuid4())
    assistant_message_id = str(uuid4())
    generation_id = str(uuid4())
    async with app.state.session_factory() as db, db.begin():
        db.add(
            ChatSession(
                id=session_id,
                user_id=user_id,
                title="busy",
                model_id="chat-5.5",
                reasoning_mode="standard",
                created_at=now,
                updated_at=now,
            )
        )
        await db.flush()
        db.add_all(
            (
                Message(
                    id=user_message_id,
                    user_id=user_id,
                    session_id=session_id,
                    role="user",
                    content="busy",
                    status="completed",
                    sequence=1,
                    created_at=now,
                    updated_at=now,
                ),
                Message(
                    id=assistant_message_id,
                    user_id=user_id,
                    session_id=session_id,
                    role="assistant",
                    content="",
                    status="streaming",
                    sequence=2,
                    model_id="chat-5.5",
                    reasoning_mode="standard",
                    created_at=now,
                    updated_at=now,
                ),
            )
        )
        await db.flush()
        db.add(
            Generation(
                id=generation_id,
                user_id=user_id,
                session_id=session_id,
                client_request_id=str(uuid4()),
                user_message_id=user_message_id,
                assistant_message_id=assistant_message_id,
                model_id="chat-5.5",
                reasoning_mode="standard",
                status="streaming",
                request_fingerprint="0" * 64,
                created_at=now,
                updated_at=now,
            )
        )
    return generation_id


async def _complete_generation(app: Any, generation_id: str) -> None:
    async with app.state.session_factory() as db, db.begin():
        generation = await db.scalar(select(Generation).where(Generation.id == generation_id))
        assert generation is not None
        generation.status = "completed"


async def test_register_preserves_guest_identity_and_rejects_duplicate_email(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    _, client = app_client
    guest, _ = await authenticate(client)
    await _chat(client, guest, "注册前对话")

    response = await client.post(
        "/api/v1/auth/register",
        headers=bearer(guest),
        json={
            "display_name": "alice",
            "email": "Alice@Example.com",
            "password": "password123",
        },
    )
    assert response.status_code == 200, response.text
    registered = response.json()["data"]
    assert registered["user_id"] == guest["user_id"]
    assert registered["user"] == {
        "id": guest["user_id"],
        "account_type": "registered",
        "display_name": "alice",
        "email": "alice@example.com",
        "avatar_text": "A",
    }
    assert await _session_titles(client, registered) == {"注册前对话"}
    assert (await client.get("/api/v1/auth/me", headers=bearer(guest))).status_code == 401
    old_refresh = await client.post(
        "/api/v1/auth/refresh", json={"refresh_token": guest["refresh_token"]}
    )
    assert old_refresh.status_code == 401

    other_guest, _ = await authenticate(client)
    duplicate = await client.post(
        "/api/v1/auth/register",
        headers=bearer(other_guest),
        json={
            "display_name": "other",
            "email": "alice@example.com",
            "password": "another-password",
        },
    )
    assert duplicate.status_code == 409
    assert duplicate.json()["error"]["code"] == "EMAIL_ALREADY_REGISTERED"


async def test_startup_upgrades_legacy_bootstrap_admin(tmp_path: Path) -> None:
    database_path = tmp_path / "legacy-admin.db"
    settings = make_test_settings(database_path)
    legacy_settings = settings.model_copy(
        update={"default_admin_username": "admin", "default_admin_password": "123"}
    )
    legacy_app = create_app(legacy_settings)
    async with LifespanManager(legacy_app):
        async with legacy_app.state.session_factory() as db:
            legacy = await db.scalar(
                select(User).where(User.email_normalized == "admin")
            )
            assert legacy is not None
            legacy_user_id = legacy.id

    upgraded_app = create_app(settings)
    async with LifespanManager(upgraded_app):
        async with upgraded_app.state.session_factory() as db:
            upgraded = await db.scalar(
                select(User).where(User.email_normalized == "admin@123.com")
            )
            assert upgraded is not None
            assert upgraded.id == legacy_user_id
            assert upgraded.password_hash is not None
            assert verify_password(upgraded.password_hash, "123456") is True
            assert await db.scalar(
                select(User).where(User.email_normalized == "admin")
            ) is None


async def test_admin_login_merges_sessions_and_logout_only_affects_current_device(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    _, client = app_client
    first_guest, first_installation = await authenticate(client)
    await _chat(client, first_guest, "设备一游客对话")
    first_login = await client.post(
        "/api/v1/auth/login",
        headers=bearer(first_guest),
        json={"email": "admin@123.com", "password": "123456"},
    )
    assert first_login.status_code == 200, first_login.text
    first_admin = first_login.json()["data"]
    assert first_admin["user"]["display_name"] == "admin@123.com"
    assert first_admin["user"]["avatar_text"] == "A"

    second_guest, second_installation = await authenticate(client)
    await _chat(client, second_guest, "设备二游客对话")
    second_login = await client.post(
        "/api/v1/auth/login",
        headers=bearer(second_guest),
        json={"email": "ADMIN@123.COM", "password": "123456"},
    )
    assert second_login.status_code == 200, second_login.text
    second_admin = second_login.json()["data"]
    assert second_admin["user_id"] == first_admin["user_id"]
    assert await _session_titles(client, second_admin) == {
        "设备一游客对话",
        "设备二游客对话",
    }

    logout = await client.post("/api/v1/auth/logout", headers=bearer(second_admin))
    assert logout.status_code == 200, logout.text
    new_guest = logout.json()["data"]
    assert new_guest["user"]["account_type"] == "guest"
    assert new_guest["user_id"] != second_admin["user_id"]
    assert await _session_titles(client, new_guest) == set()
    assert (await client.get("/api/v1/auth/me", headers=bearer(second_admin))).status_code == 401
    assert (await client.get("/api/v1/auth/me", headers=bearer(first_admin))).status_code == 200
    old_refresh = await client.post(
        "/api/v1/auth/refresh", json={"refresh_token": second_admin["refresh_token"]}
    )
    assert old_refresh.status_code == 401

    recovered_first, _ = await authenticate(client, first_installation)
    recovered_second, _ = await authenticate(client, second_installation)
    assert recovered_first["user"]["account_type"] == "registered"
    assert recovered_second["user"]["account_type"] == "guest"

    await _chat(client, new_guest, "退出期间游客对话")
    relogin = await client.post(
        "/api/v1/auth/login",
        headers=bearer(new_guest),
        json={"email": "admin@123.com", "password": "123456"},
    )
    assert relogin.status_code == 200
    assert await _session_titles(client, relogin.json()["data"]) == {
        "设备一游客对话",
        "设备二游客对话",
        "退出期间游客对话",
    }


async def test_login_rate_limit_and_invalid_credentials_are_not_account_enumerable(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    _, client = app_client
    guest, _ = await authenticate(client)
    for email in ("admin@123.com", "missing@example.com"):
        response = await client.post(
            "/api/v1/auth/login",
            headers=bearer(guest),
            json={"email": email, "password": "wrong"},
        )
        assert response.status_code == 401
        assert response.json()["error"]["code"] == "INVALID_CREDENTIALS"

    for _ in range(4):
        response = await client.post(
            "/api/v1/auth/login",
            headers=bearer(guest),
            json={"email": "admin@123.com", "password": "wrong"},
        )
        assert response.status_code == 401
    limited = await client.post(
        "/api/v1/auth/login",
        headers=bearer(guest),
        json={"email": "admin@123.com", "password": "123456"},
    )
    assert limited.status_code == 429
    assert limited.json()["error"]["code"] == "LOGIN_RATE_LIMITED"


async def test_login_keeps_both_records_when_client_idempotency_keys_collide(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    _, client = app_client
    request_id = str(uuid4())
    message_id = str(uuid4())

    account_guest, _ = await authenticate(client)
    account_login = await client.post(
        "/api/v1/auth/login",
        headers=bearer(account_guest),
        json={"email": "admin@123.com", "password": "123456"},
    )
    account = account_login.json()["data"]
    await _chat(
        client,
        account,
        "账号对话",
        client_request_id=request_id,
        client_message_id=message_id,
    )

    guest, _ = await authenticate(client)
    await _chat(
        client,
        guest,
        "游客对话",
        client_request_id=request_id,
        client_message_id=message_id,
    )
    merged = await client.post(
        "/api/v1/auth/login",
        headers=bearer(guest),
        json={"email": "admin@123.com", "password": "123456"},
    )
    assert merged.status_code == 200, merged.text
    assert await _session_titles(client, merged.json()["data"]) == {"账号对话", "游客对话"}


async def test_active_generation_blocks_register_login_and_logout(
    app_client: tuple[Any, httpx.AsyncClient],
) -> None:
    app, client = app_client

    register_guest, _ = await authenticate(client)
    generation_id = await _insert_active_generation(app, register_guest["user_id"])
    register = await client.post(
        "/api/v1/auth/register",
        headers=bearer(register_guest),
        json={
            "display_name": "busy",
            "email": "busy@example.com",
            "password": "password123",
        },
    )
    assert register.status_code == 409
    assert register.json()["error"]["code"] == "AUTH_TRANSITION_BUSY"
    await _complete_generation(app, generation_id)

    login_guest, _ = await authenticate(client)
    await _insert_active_generation(app, login_guest["user_id"])
    login = await client.post(
        "/api/v1/auth/login",
        headers=bearer(login_guest),
        json={"email": "admin@123.com", "password": "123456"},
    )
    assert login.status_code == 409
    assert login.json()["error"]["code"] == "AUTH_TRANSITION_BUSY"

    registered = await client.post(
        "/api/v1/auth/register",
        headers=bearer(register_guest),
        json={
            "display_name": "busy",
            "email": "busy@example.com",
            "password": "password123",
        },
    )
    assert registered.status_code == 200
    registered_data = registered.json()["data"]
    await _insert_active_generation(app, registered_data["user_id"])
    logout = await client.post("/api/v1/auth/logout", headers=bearer(registered_data))
    assert logout.status_code == 409
    assert logout.json()["error"]["code"] == "AUTH_TRANSITION_BUSY"
