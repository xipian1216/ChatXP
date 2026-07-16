from __future__ import annotations

import base64
import binascii
from typing import Literal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, EmailStr, Field, field_validator


class AuthUser(BaseModel):
    id: UUID
    account_type: Literal["guest", "registered"]
    display_name: str | None
    email: str | None
    avatar_text: str | None


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


class RegisterRequest(BaseModel):
    display_name: str = Field(min_length=1, max_length=30)
    email: EmailStr = Field(max_length=320)
    password: str = Field(min_length=8, max_length=72)

    @field_validator("display_name", "email", mode="before")
    @classmethod
    def normalize_identity_fields(cls, value: object) -> object:
        return value.strip() if isinstance(value, str) else value


class LoginRequest(BaseModel):
    email: str = Field(min_length=1, max_length=320)
    password: str = Field(min_length=1, max_length=72)

    @field_validator("email", mode="before")
    @classmethod
    def normalize_email(cls, value: object) -> object:
        return value.strip() if isinstance(value, str) else value


class TokenResponse(BaseModel):
    user_id: UUID
    user: AuthUser
    access_token: str
    token_type: Literal["Bearer"] = "Bearer"
    expires_in: int
    refresh_token: str
    refresh_expires_in: int
