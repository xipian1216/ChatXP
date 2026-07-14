from __future__ import annotations

import pytest

from chatxp.core.config import Settings
from chatxp.providers.base import ProviderDelta, ProviderDone, ProviderMessage
from chatxp.providers.openai_compatible import OpenAICompatibleProvider

pytestmark = pytest.mark.live


async def test_configured_openai_compatible_provider_streams() -> None:
    settings = Settings()
    if settings.chat_provider != "openai" or not settings.ai_base_url or not settings.ai_api_key:
        pytest.skip("external provider is not configured")
    provider_model = settings.provider_model(settings.ai_default_model)
    assert provider_model is not None
    provider = OpenAICompatibleProvider(settings.ai_base_url, settings.ai_api_key)
    events = [
        event
        async for event in provider.stream_chat(
            provider_model,
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
