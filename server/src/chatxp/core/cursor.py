from __future__ import annotations

import base64
import hashlib
import hmac
import json
from typing import Any


class InvalidCursorError(ValueError):
    pass


def encode_cursor(payload: dict[str, Any], secret: str) -> str:
    body = base64.urlsafe_b64encode(
        json.dumps(payload, sort_keys=True, separators=(",", ":")).encode()
    ).rstrip(b"=")
    signature = hmac.new(secret.encode(), body, hashlib.sha256).digest()
    encoded_signature = base64.urlsafe_b64encode(signature).rstrip(b"=")
    return f"{body.decode()}.{encoded_signature.decode()}"


def decode_cursor(value: str, secret: str) -> dict[str, Any]:
    try:
        body, encoded_signature = value.split(".", 1)
        expected = hmac.new(secret.encode(), body.encode(), hashlib.sha256).digest()
        signature = base64.urlsafe_b64decode(_pad(encoded_signature))
        if not hmac.compare_digest(signature, expected):
            raise InvalidCursorError("invalid cursor signature")
        decoded = base64.urlsafe_b64decode(_pad(body)).decode()
        payload = json.loads(decoded)
    except (ValueError, UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise InvalidCursorError("invalid cursor") from exc
    if not isinstance(payload, dict):
        raise InvalidCursorError("invalid cursor payload")
    return payload


def _pad(value: str) -> str:
    return value + "=" * (-len(value) % 4)

