from __future__ import annotations

import base64
import binascii
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator


class AnonymousAuthRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    installation_id: UUID
    installation_secret: str = Field(min_length=32, max_length=256)
    platform: Literal["android"]
    app_version: str = Field(min_length=1, max_length=50)

    @field_validator("installation_secret")
    @classmethod
    def validate_secret_entropy(cls, value: str) -> str:
        try:
            raw = base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
        except (ValueError, binascii.Error) as exc:
            raise ValueError("must be base64url encoded") from exc
        if len(raw) < 32:
            raise ValueError("must encode at least 32 bytes")
        return value


class RefreshRequest(BaseModel):
    model_config = ConfigDict(str_strip_whitespace=True)

    refresh_token: str = Field(min_length=32, max_length=512)


class TokenResponse(BaseModel):
    user_id: UUID
    access_token: str
    token_type: Literal["Bearer"] = "Bearer"
    expires_in: int
    refresh_token: str
    refresh_expires_in: int
