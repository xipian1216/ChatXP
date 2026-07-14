from __future__ import annotations

import json
from typing import Any


def encode_sse(event: str, data: dict[str, Any], event_id: int) -> str:
    payload = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
    return f"id: {event_id}\nevent: {event}\ndata: {payload}\n\n"


def heartbeat() -> str:
    return ": ping\n\n"

