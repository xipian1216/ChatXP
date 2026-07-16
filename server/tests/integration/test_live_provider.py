from __future__ import annotations

import pytest

from chatxp.core.config import Settings
from chatxp.providers.base import ProviderDelta, ProviderDone, ProviderMessage
from chatxp.providers.openai_compatible import OpenAICompatibleProvider

pytestmark = pytest.mark.live


@pytest.mark.parametrize(
    ("model_id", "reasoning_mode"),
    [
        ("chat-5.5", "standard"),
        ("chat-5.5", "advanced"),
        ("chat-5.6", "standard"),
        ("chat-5.6", "advanced"),
    ],
)
async def test_configured_openai_compatible_provider_streams(
    model_id: str, reasoning_mode: str
) -> None:
    settings = Settings()
    if settings.chat_provider != "openai" or not settings.ai_base_url or not settings.ai_api_key:
        pytest.skip("external provider is not configured")
    provider_model = settings.provider_model(model_id)
    assert provider_model is not None
    provider = OpenAICompatibleProvider(settings.ai_base_url, settings.ai_api_key)
    events = [
        event
        async for event in provider.stream_chat(
            provider_model,
            reasoning_mode,
            [
                ProviderMessage(
                    "user", "Reply with exactly this Markdown and nothing else: **OK**"
                )
            ],
        )
    ]
    content = "".join(
        event.content for event in events if isinstance(event, ProviderDelta)
    )
    assert "**OK**" in content
    assert isinstance(events[-1], ProviderDone)
