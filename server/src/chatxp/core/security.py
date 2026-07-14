from __future__ import annotations

import hashlib
import hmac
import json
import secrets
from datetime import timedelta
from typing import Any
from uuid import uuid4

import jwt

from chatxp.core.time import utcnow


def keyed_hash(value: str, secret: str) -> str:
    return hmac.new(secret.encode(), value.encode(), hashlib.sha256).hexdigest()


def create_access_token(user_id: str, secret: str, ttl_seconds: int) -> str:
    now = utcnow()
    payload = {
        "sub": user_id,
        "iat": now,
        "exp": now + timedelta(seconds=ttl_seconds),
        "jti": str(uuid4()),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def decode_access_token(token: str, secret: str) -> str:
    payload = jwt.decode(token, secret, algorithms=["HS256"])
    subject = payload.get("sub")
    if not isinstance(subject, str) or not subject:
        raise jwt.InvalidTokenError("missing sub")
    return subject


def new_refresh_token() -> str:
    return secrets.token_urlsafe(48)


def request_fingerprint(payload: dict[str, Any]) -> str:
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode()).hexdigest()

