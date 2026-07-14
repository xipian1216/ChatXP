from __future__ import annotations

import pytest

from chatxp.core.config import ProviderModelConfig, Settings
from chatxp.core.cursor import InvalidCursorError, decode_cursor, encode_cursor
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


def test_chat_strings_are_trimmed_before_length_validation() -> None:
    request = ChatStreamRequest.model_validate(
        {
            "client_request_id": "1f5735d5-9c41-4350-bee2-728524106e55",
            "client_message_id": "50711fca-62c8-40d2-98a3-34c74d175962",
            "model_id": " chat-default ",
            "content": " hello ",
        }
    )
    assert request.model_id == "chat-default"
    assert request.content == "hello"
