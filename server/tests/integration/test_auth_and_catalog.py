from __future__ import annotations

import httpx

from tests.conftest import authenticate, bearer, installation_payload


async def test_anonymous_recovery_refresh_rotation_and_catalog(
    app_client: tuple[object, httpx.AsyncClient],
) -> None:
    _, client = app_client
    assert (await client.get("/health/live")).json() == {"status": "ok"}
    ready = await client.get("/health/ready")
    assert ready.status_code == 200
    assert ready.json() == {"status": "ok"}
    payload = installation_payload()
    first, _ = await authenticate(client, payload)
    recovered, _ = await authenticate(client, payload)
    assert recovered["user_id"] == first["user_id"]

    models = await client.get("/api/v1/models", headers=bearer(first))
    assert models.status_code == 200
    catalog = models.json()["data"]["items"]
    assert [model["id"] for model in catalog] == ["chat-5.5", "chat-5.6"]
    assert [model["display_name"] for model in catalog] == ["5.5", "5.6"]
    assert [model["is_default"] for model in catalog] == [True, False]
    assert all(
        model["capabilities"]["reasoning_modes"] == ["standard", "advanced"]
        for model in catalog
    )
    assert "provider_model" not in models.text

    refreshed = await client.post(
        "/api/v1/auth/refresh", json={"refresh_token": first["refresh_token"]}
    )
    assert refreshed.status_code == 200
    replay = await client.post(
        "/api/v1/auth/refresh", json={"refresh_token": first["refresh_token"]}
    )
    assert replay.status_code == 401
    assert replay.json()["error"]["code"] == "AUTH_INVALID"


async def test_wrong_installation_secret_is_rejected(
    app_client: tuple[object, httpx.AsyncClient],
) -> None:
    _, client = app_client
    payload = installation_payload()
    await authenticate(client, payload)
    changed = {**payload, "installation_secret": installation_payload()["installation_secret"]}
    response = await client.post("/api/v1/auth/anonymous", json=changed)
    assert response.status_code == 401
    assert response.json()["error"]["code"] == "AUTH_INVALID"


async def test_validation_and_request_id_use_contract_shape(
    app_client: tuple[object, httpx.AsyncClient],
) -> None:
    _, client = app_client
    response = await client.post(
        "/api/v1/auth/anonymous",
        json={},
        headers={"X-Request-ID": "android-request-1"},
    )
    assert response.status_code == 400
    assert response.headers["X-Request-ID"] == "android-request-1"
    error = response.json()["error"]
    assert error["code"] == "VALIDATION_ERROR"
    assert error["request_id"] == "android-request-1"
    assert error["details"]["fields"]
