from __future__ import annotations

import asyncio
from collections.abc import AsyncIterator
from typing import Annotated, Any, cast

from fastapi import APIRouter, Depends, Request
from fastapi.responses import StreamingResponse

from chatxp.api.dependencies import current_user_id
from chatxp.schemas.chat import ChatStreamRequest, DoneEvent, StreamErrorEvent, UsageDto, model_json
from chatxp.services.chat import ChatCoordinator, StreamContext
from chatxp.streaming.sse import encode_sse, heartbeat

router = APIRouter(prefix="/chat", tags=["chat"])


@router.post(
    "/streams",
    response_class=StreamingResponse,
    responses={200: {"content": {"text/event-stream": {}}}},
)
async def stream_chat(
    request: Request,
    body: ChatStreamRequest,
    user_id: Annotated[str, Depends(current_user_id)],
) -> StreamingResponse:
    coordinator = cast(ChatCoordinator, request.app.state.chat_coordinator)
    context = await coordinator.prepare(user_id, body)
    generation_id = str(context.generation.id)
    active = context.generation.status in {"queued", "streaming"}
    subscription = coordinator.subscribe(generation_id) if active else None
    if active:
        await coordinator.ensure_running(generation_id)

    async def events() -> AsyncIterator[str]:
        event_id = 1
        delta_sequence = 0
        yield encode_sse("meta", model_json(context.meta()), event_id)
        event_id += 1
        try:
            if context.generation.status == "completed":
                yield encode_sse("done", _done_from_context(context), event_id)
                return
            if context.generation.status == "failed":
                yield encode_sse("error", _error_from_context(context), event_id)
                return
            if subscription is None:
                return
            while True:
                try:
                    async with asyncio.timeout(15):
                        event = await subscription.queue.get()
                except TimeoutError:
                    yield heartbeat()
                    continue
                if event.kind == "disconnect":
                    return
                data = event.data
                if event.kind == "delta":
                    delta_sequence += 1
                    data = {**data, "sequence": delta_sequence}
                yield encode_sse(event.kind, data, event_id)
                event_id += 1
                if event.kind in {"done", "error"}:
                    return
        finally:
            if subscription is not None:
                coordinator.unsubscribe(subscription)

    return StreamingResponse(
        events(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


def _done_from_context(context: StreamContext) -> dict[str, Any]:
    message = context.generation.assistant_message
    usage = None
    if message.prompt_tokens is not None and message.completion_tokens is not None:
        usage = UsageDto(
            prompt_tokens=message.prompt_tokens,
            completion_tokens=message.completion_tokens,
            total_tokens=message.prompt_tokens + message.completion_tokens,
        )
    return model_json(
        DoneEvent(
            assistant_message=message,
            finish_reason="stop",
            usage=usage,
            session=context.session,
        )
    )


def _error_from_context(context: StreamContext) -> dict[str, Any]:
    code = context.generation.error_code or "INTERNAL_ERROR"
    return model_json(
        StreamErrorEvent(
            code=code,
            message=context.generation.error_message or "Generation failed",
            retryable=code in {"PROVIDER_RATE_LIMIT", "PROVIDER_UNAVAILABLE"},
            assistant_message_id=context.generation.assistant_message.id,
        )
    )
