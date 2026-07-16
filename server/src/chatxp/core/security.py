from __future__ import annotations

import hashlib
import hmac
import json
import secrets
from datetime import timedelta
from typing import Any
from uuid import uuid4

import jwt
from argon2 import PasswordHasher
from argon2.exceptions import InvalidHashError, VerificationError

from chatxp.core.time import utcnow


def keyed_hash(value: str, secret: str) -> str:
    return hmac.new(secret.encode(), value.encode(), hashlib.sha256).hexdigest()


_password_hasher = PasswordHasher(
    time_cost=2,
    memory_cost=19 * 1024,
    parallelism=1,
    hash_len=32,
    salt_len=16,
)


def create_access_token(
    user_id: str,
    installation_id: str,
    token_version: int,
    secret: str,
    ttl_seconds: int,
) -> str:
    now = utcnow()
    payload = {
        "sub": user_id,
        "installation_id": installation_id,
        "token_version": token_version,
        "iat": now,
        "exp": now + timedelta(seconds=ttl_seconds),
        "jti": str(uuid4()),
    }
    return jwt.encode(payload, secret, algorithm="HS256")


def decode_access_token(token: str, secret: str) -> tuple[str, str, int]:
    payload = jwt.decode(token, secret, algorithms=["HS256"])
    subject = payload.get("sub")
    if not isinstance(subject, str) or not subject:
        raise jwt.InvalidTokenError("missing sub")
    installation_id = payload.get("installation_id")
    token_version = payload.get("token_version")
    if not isinstance(installation_id, str) or not installation_id:
        raise jwt.InvalidTokenError("missing installation_id")
    if not isinstance(token_version, int) or token_version < 1:
        raise jwt.InvalidTokenError("invalid token_version")
    return subject, installation_id, token_version


def hash_password(password: str) -> str:
    return _password_hasher.hash(password)


def verify_password(password_hash: str, password: str) -> bool:
    try:
        return _password_hasher.verify(password_hash, password)
    except (InvalidHashError, VerificationError):
        return False


DUMMY_PASSWORD_HASH = hash_password("chatxp-invalid-password")


def new_refresh_token() -> str:
    return secrets.token_urlsafe(48)


def request_fingerprint(payload: dict[str, Any]) -> str:
    canonical = json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode()).hexdigest()
