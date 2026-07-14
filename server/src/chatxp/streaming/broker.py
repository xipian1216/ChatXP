from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any
from uuid import uuid4


@dataclass(frozen=True, slots=True)
class StreamEvent:
    kind: str
    data: dict[str, Any]


@dataclass(frozen=True, slots=True)
class Subscription:
    id: str
    generation_id: str
    queue: asyncio.Queue[StreamEvent]


class GenerationBroker:
    def __init__(self, queue_size: int = 128) -> None:
        self._queue_size = queue_size
        self._subscribers: dict[str, dict[str, asyncio.Queue[StreamEvent]]] = {}

    def subscribe(self, generation_id: str) -> Subscription:
        subscription_id = str(uuid4())
        queue: asyncio.Queue[StreamEvent] = asyncio.Queue(maxsize=self._queue_size)
        self._subscribers.setdefault(generation_id, {})[subscription_id] = queue
        return Subscription(subscription_id, generation_id, queue)

    def unsubscribe(self, subscription: Subscription) -> None:
        subscribers = self._subscribers.get(subscription.generation_id)
        if subscribers is None:
            return
        subscribers.pop(subscription.id, None)
        if not subscribers:
            self._subscribers.pop(subscription.generation_id, None)

    def publish(self, generation_id: str, event: StreamEvent) -> None:
        subscribers = self._subscribers.get(generation_id, {})
        slow: list[str] = []
        for subscription_id, queue in subscribers.items():
            try:
                queue.put_nowait(event)
            except asyncio.QueueFull:
                while not queue.empty():
                    queue.get_nowait()
                queue.put_nowait(StreamEvent("disconnect", {}))
                slow.append(subscription_id)
        for subscription_id in slow:
            subscribers.pop(subscription_id, None)
        if not subscribers:
            self._subscribers.pop(generation_id, None)
