from __future__ import annotations

import json

import httpx

from chatxp.providers.base import ProviderDelta, ProviderDone, ProviderMessage
from chatxp.providers.openai_compatible import OpenAICompatibleProvider


class FragmentedUtf8Stream(httpx.AsyncByteStream):
    def __init__(self, chunks: list[bytes]) -> None:
        self._chunks = chunks

    async def __aiter__(self):  # type: ignore[no-untyped-def]
        for chunk in self._chunks:
            yield chunk

    async def aclose(self) -> None:
        pass


async def test_provider_preserves_markdown_across_utf8_byte_chunks() -> None:
    first = "# 标题\n\n```py"
    second = "thon\n你好 😀\n```"
    frames = [
        {"choices": [{"delta": {"content": first}, "finish_reason": None}]},
        {"choices": [{"delta": {"content": second}, "finish_reason": None}]},
        {
            "choices": [{"delta": {}, "finish_reason": "stop"}],
            "usage": {"prompt_tokens": 3, "completion_tokens": 5},
        },
    ]
    encoded = (
        "".join(
            f"data: {json.dumps(frame, ensure_ascii=False, separators=(',', ':'))}\n\n"
            for frame in frames
        )
        + "data: [DONE]\n\n"
    ).encode()
    chinese_offset = encoded.index("标".encode())
    chunks = [
        encoded[: chinese_offset + 1],
        encoded[chinese_offset + 1 : chinese_offset + 2],
        encoded[chinese_offset + 2 : chinese_offset + 19],
        encoded[chinese_offset + 19 :],
    ]

    def handler(request: httpx.Request) -> httpx.Response:
        assert request.url.path == "/v1/chat/completions"
        return httpx.Response(200, stream=FragmentedUtf8Stream(chunks))

    provider = OpenAICompatibleProvider(
        "https://provider.example/v1",
        "test-key",
        transport=httpx.MockTransport(handler),
    )
    events = [
        event
        async for event in provider.stream_chat(
            "deepseek-v4-flash", [ProviderMessage("user", "返回 Markdown")]
        )
    ]

    assert [event.content for event in events if isinstance(event, ProviderDelta)] == [
        first,
        second,
    ]
    assert isinstance(events[-1], ProviderDone)
    assert events[-1].usage is not None
    assert events[-1].usage.total_tokens == 8
