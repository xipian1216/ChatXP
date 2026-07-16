from chatxp.main import create_app


def test_openapi_contains_frozen_first_milestone_routes() -> None:
    schema = create_app().openapi()
    paths = schema["paths"]
    expected = {
        "/health/live",
        "/health/ready",
        "/api/v1/auth/anonymous",
        "/api/v1/auth/refresh",
        "/api/v1/auth/me",
        "/api/v1/auth/register",
        "/api/v1/auth/login",
        "/api/v1/auth/logout",
        "/api/v1/models",
        "/api/v1/sessions",
        "/api/v1/sessions/{session_id}",
        "/api/v1/sessions/{session_id}/messages",
        "/api/v1/chat/streams",
        "/api/v1/generations/by-client-request/{client_request_id}",
    }
    assert expected <= set(paths)
    session_path = paths["/api/v1/sessions/{session_id}"]
    assert {"get", "patch", "delete"} <= set(session_path)
    patch_schema = session_path["patch"]["requestBody"]["content"][
        "application/json"
    ]["schema"]
    assert patch_schema["$ref"].endswith("/SessionUpdateRequest")
    update_schema = schema["components"]["schemas"]["SessionUpdateRequest"]
    assert set(update_schema["properties"]) == {
        "title",
        "model_id",
        "reasoning_mode",
        "is_pinned",
    }
    assert update_schema["minProperties"] == 1
    assert "required" not in update_schema
    assert update_schema["properties"]["title"]["type"] == "string"
    assert update_schema["properties"]["model_id"]["type"] == "string"
    assert update_schema["properties"]["reasoning_mode"]["$ref"].endswith(
        "/ReasoningMode"
    )
    assert update_schema["properties"]["is_pinned"]["type"] == "boolean"
    assert session_path["patch"]["responses"]["200"]["content"][
        "application/json"
    ]["schema"]["$ref"].endswith("/DataEnvelope_SessionDto_")
    assert "204" in session_path["delete"]["responses"]
    assert "content" not in session_path["delete"]["responses"]["204"]
    chat_request = schema["components"]["schemas"]["ChatStreamRequest"]
    content_schema = chat_request["properties"]["content"]
    assert content_schema["minLength"] == 1
    assert content_schema["maxLength"] == 20_000
    assert "reasoning_mode" in chat_request["properties"]
    reasoning_mode = schema["components"]["schemas"]["ReasoningMode"]
    assert reasoning_mode["enum"] == ["standard", "advanced"]
    for dto_name in ("SessionDto", "MessageDto", "GenerationDto"):
        assert "reasoning_mode" in schema["components"]["schemas"][dto_name]["required"]
    model_capabilities = schema["components"]["schemas"]["ModelCapabilities"]
    assert "reasoning_modes" in model_capabilities["properties"]
    auth_user = schema["components"]["schemas"]["AuthUser"]
    assert set(auth_user["required"]) == {
        "id",
        "account_type",
        "display_name",
        "email",
        "avatar_text",
    }
    assert set(auth_user["properties"]["account_type"]["enum"]) == {
        "guest",
        "registered",
    }
    token_response = schema["components"]["schemas"]["TokenResponse"]
    assert "user" in token_response["required"]
    register_request = schema["components"]["schemas"]["RegisterRequest"]
    assert set(register_request["required"]) == {"display_name", "email", "password"}
    assert register_request["properties"]["password"]["minLength"] == 8
    assert register_request["properties"]["password"]["maxLength"] == 72
    assert "ErrorEnvelope" in schema["components"]["schemas"]
    for path in paths.values():
        for operation in path.values():
            if isinstance(operation, dict) and "responses" in operation:
                assert "422" not in operation["responses"]
