from __future__ import annotations

import pytest
from pydantic import ValidationError

from chatxp.core.config import ProviderModelConfig, Settings
from chatxp.core.cursor import InvalidCursorError, decode_cursor, encode_cursor
from chatxp.core.security import (
    create_access_token,
    decode_access_token,
    hash_password,
    verify_password,
)
from chatxp.schemas.chat import ChatStreamRequest, default_title, preview
from chatxp.streaming.sse import encode_sse, heartbeat


def test_title_and_preview_use_unicode_characters() -> None:
    assert default_title("  第一行\n第二行  ") == "第一行 第二行"
    content = "你" * 31
    assert default_title(content) == "你" * 30 + "…"
    assert preview("界" * 81) == "界" * 80


def test_cursor_is_signed() -> None:
    encoded = encode_cursor({"before": 3}, "secret")
    assert decode_cursor(encoded, "secret") == {"before": 3}
    with pytest.raises(InvalidCursorError):
        decode_cursor(encoded + "broken", "secret")


def test_sse_is_single_line_json() -> None:
    frame = encode_sse("delta", {"content_delta": "a\nb"}, 2)
    assert frame == 'id: 2\nevent: delta\ndata: {"content_delta":"a\\nb"}\n\n'
    assert heartbeat() == ": ping\n\n"


def test_model_catalog_rejects_duplicate_ids() -> None:
    model = ProviderModelConfig(
        id="same", provider_model="provider", display_name="Same", description=None
    )
    with pytest.raises(ValueError, match="unique"):
        Settings(ai_default_model="same", ai_models_json=[model, model])


def test_model_catalog_defaults_and_legacy_configuration_upgrade() -> None:
    defaults = Settings(_env_file=None)
    assert defaults.ai_default_model == "chat-5.5"
    assert [model.id for model in defaults.ai_models_json] == ["chat-5.5", "chat-5.6"]
    assert defaults.provider_model("chat-5.5") == "deepseek-v4-flash"
    assert defaults.provider_model("chat-5.6") == "deepseek-v4-pro"

    legacy = Settings(
        _env_file=None,
        ai_default_model="chat-default",
        ai_models_json=[
            ProviderModelConfig(
                id="chat-default",
                provider_model="deepseek-v4-flash",
                display_name="Legacy",
            )
        ],
    )
    assert legacy.ai_default_model == "chat-5.5"
    assert [model.id for model in legacy.ai_models_json] == ["chat-5.5", "chat-5.6"]


def test_chat_preserves_markdown_whitespace_and_normalizes_model_id() -> None:
    content = "\r\n    print('hello')  \r\n"
    request = ChatStreamRequest.model_validate(
        {
            "client_request_id": "1f5735d5-9c41-4350-bee2-728524106e55",
            "client_message_id": "50711fca-62c8-40d2-98a3-34c74d175962",
            "model_id": " chat-5.5 ",
            "reasoning_mode": "standard",
            "content": content,
        }
    )
    assert request.model_id == "chat-5.5"
    assert request.reasoning_mode == "standard"
    assert request.content == content


def test_chat_rejects_blank_and_oversized_original_content() -> None:
    base = {
        "client_request_id": "1f5735d5-9c41-4350-bee2-728524106e55",
        "client_message_id": "50711fca-62c8-40d2-98a3-34c74d175962",
        "model_id": "chat-5.5",
        "reasoning_mode": "standard",
    }
    with pytest.raises(ValidationError):
        ChatStreamRequest.model_validate({**base, "content": " \n\t "})

    boundary = " " + "x" * 19_999
    assert ChatStreamRequest.model_validate({**base, "content": boundary}).content == boundary
    with pytest.raises(ValidationError):
        ChatStreamRequest.model_validate({**base, "content": boundary + "x"})


def test_password_hash_and_installation_bound_access_token() -> None:
    password_hash = hash_password(" exact password ")
    assert password_hash.startswith("$argon2id$")
    assert verify_password(password_hash, " exact password ") is True
    assert verify_password(password_hash, "exact password") is False

    token = create_access_token(
        "user-id",
        "installation-id",
        3,
        "test-jwt-secret-that-is-at-least-32-bytes",
        60,
    )
    assert decode_access_token(
        token, "test-jwt-secret-that-is-at-least-32-bytes"
    ) == ("user-id", "installation-id", 3)
