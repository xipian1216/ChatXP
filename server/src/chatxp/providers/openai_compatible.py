from __future__ import annotations

import json
from collections.abc import AsyncIterator, Sequence
from typing import Any

import httpx

from chatxp.providers.base import (
    ProviderDelta,
    ProviderDone,
    ProviderEvent,
    ProviderMessage,
    ProviderRateLimitError,
    ProviderUnavailableError,
    ProviderUsage,
)


class OpenAICompatibleProvider:
    def __init__(
        self,
        base_url: str,
        api_key: str,
        timeout_seconds: float = 120,
        transport: httpx.AsyncBaseTransport | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._timeout = httpx.Timeout(timeout_seconds, connect=15)
        self._transport = transport

    async def stream_chat(
        self,
        provider_model: str,
        reasoning_mode: str,
        messages: Sequence[ProviderMessage],
    ) -> AsyncIterator[ProviderEvent]:
        payload = {
            "model": provider_model,
            "messages": [{"role": item.role, "content": item.content} for item in messages],
            "stream": True,
            "stream_options": {"include_usage": True},
            "thinking": {
                "type": "enabled" if reasoning_mode == "advanced" else "disabled"
            },
        }
        finish_reason = "stop"
        usage: ProviderUsage | None = None
        try:
            async with httpx.AsyncClient(
                timeout=self._timeout, transport=self._transport
            ) as client:
                async with client.stream(
                    "POST",
                    f"{self._base_url}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {self._api_key}",
                        "Accept": "text/event-stream",
                    },
                    json=payload,
                ) as response:
                    if response.status_code == 429:
                        raise ProviderRateLimitError("upstream rate limited the request")
                    if response.status_code >= 400:
                        raise ProviderUnavailableError(
                            f"upstream returned HTTP {response.status_code}"
                        )
                    async for line in response.aiter_lines():
                        if not line.startswith("data:"):
                            continue
                        data = line.removeprefix("data:").strip()
                        if not data or data == "[DONE]":
                            continue
                        chunk = self._decode_chunk(data)
                        choices = chunk.get("choices")
                        if isinstance(choices, list) and choices:
                            choice = choices[0]
                            if isinstance(choice, dict):
                                delta = choice.get("delta")
                                if isinstance(delta, dict) and isinstance(
                                    delta.get("content"), str
                                ):
                                    content = delta["content"]
                                    if content:
                                        yield ProviderDelta(content)
                                if isinstance(choice.get("finish_reason"), str):
                                    finish_reason = choice["finish_reason"]
                        raw_usage = chunk.get("usage")
                        if isinstance(raw_usage, dict):
                            prompt = raw_usage.get("prompt_tokens")
                            completion = raw_usage.get("completion_tokens")
                            if isinstance(prompt, int) and isinstance(completion, int):
                                usage = ProviderUsage(prompt, completion)
        except ProviderRateLimitError:
            raise
        except ProviderUnavailableError:
            raise
        except (httpx.HTTPError, ValueError, TypeError, KeyError) as exc:
            raise ProviderUnavailableError(type(exc).__name__) from exc
        yield ProviderDone(finish_reason=finish_reason, usage=usage)

    @staticmethod
    def _decode_chunk(data: str) -> dict[str, Any]:
        decoded = json.loads(data)
        if not isinstance(decoded, dict):
            raise ProviderUnavailableError("upstream stream chunk is not an object")
        return decoded
