from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator, Sequence

from chatxp.providers.base import (
    ProviderDelta,
    ProviderDone,
    ProviderEvent,
    ProviderMessage,
    ProviderUsage,
)


class FakeChatProvider:
    def __init__(self, chunk_size: int = 4, delay_seconds: float = 0) -> None:
        self.chunk_size = chunk_size
        self.delay_seconds = delay_seconds
        self.call_count = 0

    async def stream_chat(
        self, provider_model: str, messages: Sequence[ProviderMessage]
    ) -> AsyncIterator[ProviderEvent]:
        del provider_model
        self.call_count += 1
        last_user = next((item.content for item in reversed(messages) if item.role == "user"), "")
        response = f"Echo: {last_user}"
        for offset in range(0, len(response), self.chunk_size):
            if self.delay_seconds:
                await asyncio.sleep(self.delay_seconds)
            yield ProviderDelta(response[offset : offset + self.chunk_size])
        yield ProviderDone(
            finish_reason="stop",
            usage=ProviderUsage(
                prompt_tokens=max(1, len(last_user) // 4),
                completion_tokens=max(1, len(response) // 4),
            ),
        )

