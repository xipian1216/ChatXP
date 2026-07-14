from chatxp.main import create_app


def test_openapi_contains_frozen_first_milestone_routes() -> None:
    schema = create_app().openapi()
    paths = schema["paths"]
    expected = {
        "/health/live",
        "/health/ready",
        "/api/v1/auth/anonymous",
        "/api/v1/auth/refresh",
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
    assert set(update_schema["properties"]) == {"title", "model_id", "is_pinned"}
    assert update_schema["minProperties"] == 1
    assert "required" not in update_schema
    assert update_schema["properties"]["title"]["type"] == "string"
    assert update_schema["properties"]["model_id"]["type"] == "string"
    assert update_schema["properties"]["is_pinned"]["type"] == "boolean"
    assert session_path["patch"]["responses"]["200"]["content"][
        "application/json"
    ]["schema"]["$ref"].endswith("/DataEnvelope_SessionDto_")
    assert "204" in session_path["delete"]["responses"]
    assert "content" not in session_path["delete"]["responses"]["204"]
    assert "ErrorEnvelope" in schema["components"]["schemas"]
    for path in paths.values():
        for operation in path.values():
            if isinstance(operation, dict) and "responses" in operation:
                assert "422" not in operation["responses"]
