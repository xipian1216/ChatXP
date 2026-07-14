from __future__ import annotations

import asyncio
import hmac
from datetime import datetime, timedelta

import jwt
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from chatxp.api.errors import AppError
from chatxp.core.config import Settings
from chatxp.core.security import (
    create_access_token,
    decode_access_token,
    keyed_hash,
    new_refresh_token,
)
from chatxp.core.time import ensure_utc, utcnow
from chatxp.db.tables import AnonymousUser, RefreshToken, new_uuid
from chatxp.repositories import auth as auth_repository
from chatxp.schemas.auth import AnonymousAuthRequest, TokenResponse


class AuthService:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        settings: Settings,
    ) -> None:
        self._session_factory = session_factory
        self._settings = settings
        self._write_lock = asyncio.Lock()

    async def authenticate_anonymous(self, request: AnonymousAuthRequest) -> TokenResponse:
        now = utcnow()
        installation_id = str(request.installation_id)
        supplied_hash = keyed_hash(request.installation_secret, self._settings.token_hash_secret)
        async with self._write_lock, self._session_factory() as db, db.begin():
            user = await auth_repository.user_by_installation(db, installation_id)
            if user is None:
                user = AnonymousUser(
                    id=new_uuid(),
                    installation_id=installation_id,
                    installation_secret_hash=supplied_hash,
                    platform=request.platform,
                    app_version=request.app_version,
                    created_at=now,
                    last_seen_at=now,
                )
                db.add(user)
            else:
                if not hmac.compare_digest(user.installation_secret_hash, supplied_hash):
                    raise AppError(401, "AUTH_INVALID", "Installation secret is invalid")
                user.platform = request.platform
                user.app_version = request.app_version
                user.last_seen_at = now
            response, _ = self._issue_token_pair(db, user.id, now)
            return response

    async def refresh(self, raw_refresh_token: str) -> TokenResponse:
        now = utcnow()
        token_hash = keyed_hash(raw_refresh_token, self._settings.token_hash_secret)
        async with self._write_lock, self._session_factory() as db, db.begin():
            existing = await auth_repository.refresh_by_hash(db, token_hash)
            if (
                existing is None
                or existing.revoked_at is not None
                or ensure_utc(existing.expires_at) <= now
            ):
                raise AppError(401, "AUTH_INVALID", "Refresh token is invalid")
            user = await auth_repository.user_by_id(db, existing.user_id)
            if user is None:
                raise AppError(401, "AUTH_INVALID", "Refresh token is invalid")
            existing.revoked_at = now
            await db.flush()
            response, replacement_id = self._issue_token_pair(db, user.id, now)
            await db.flush()
            existing.replaced_by_id = replacement_id
            return response

    async def access_token_user(self, token: str) -> str:
        try:
            user_id = decode_access_token(token, self._settings.jwt_secret)
        except jwt.ExpiredSignatureError as exc:
            raise AppError(401, "AUTH_EXPIRED", "Access token has expired") from exc
        except jwt.InvalidTokenError as exc:
            raise AppError(401, "AUTH_INVALID", "Access token is invalid") from exc
        async with self._session_factory() as db:
            if await auth_repository.user_by_id(db, user_id) is None:
                raise AppError(401, "AUTH_INVALID", "Access token is invalid")
        return user_id

    def _issue_token_pair(
        self, db: AsyncSession, user_id: str, now: datetime
    ) -> tuple[TokenResponse, str]:
        refresh_token = new_refresh_token()
        refresh_record = RefreshToken(
            id=new_uuid(),
            user_id=user_id,
            token_hash=keyed_hash(refresh_token, self._settings.token_hash_secret),
            expires_at=now + timedelta(seconds=self._settings.refresh_token_ttl_seconds),
            created_at=now,
        )
        db.add(refresh_record)
        return (
            TokenResponse(
                user_id=user_id,
                access_token=create_access_token(
                    user_id, self._settings.jwt_secret, self._settings.access_token_ttl_seconds
                ),
                expires_in=self._settings.access_token_ttl_seconds,
                refresh_token=refresh_token,
                refresh_expires_in=self._settings.refresh_token_ttl_seconds,
            ),
            refresh_record.id,
        )
