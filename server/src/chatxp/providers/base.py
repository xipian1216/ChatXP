from __future__ import annotations

from collections.abc import AsyncIterator, Sequence
from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True, slots=True)
class ProviderMessage:
    role: str
    content: str


@dataclass(frozen=True, slots=True)
class ProviderDelta:
    content: str


@dataclass(frozen=True, slots=True)
class ProviderUsage:
    prompt_tokens: int
    completion_tokens: int

    @property
    def total_tokens(self) -> int:
        return self.prompt_tokens + self.completion_tokens


@dataclass(frozen=True, slots=True)
class ProviderDone:
    finish_reason: str
    usage: ProviderUsage | None = None


ProviderEvent = ProviderDelta | ProviderDone


class ProviderRateLimitError(RuntimeError):
    pass


class ProviderUnavailableError(RuntimeError):
    pass


class ChatProvider(Protocol):
    def stream_chat(
        self, provider_model: str, messages: Sequence[ProviderMessage]
    ) -> AsyncIterator[ProviderEvent]: ...

