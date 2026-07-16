from __future__ import annotations

import asyncio
import hmac
from dataclasses import dataclass
from datetime import datetime, timedelta

import jwt
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from chatxp.api.errors import AppError
from chatxp.core.config import Settings
from chatxp.core.security import (
    DUMMY_PASSWORD_HASH,
    create_access_token,
    decode_access_token,
    hash_password,
    keyed_hash,
    new_refresh_token,
    verify_password,
)
from chatxp.core.time import ensure_utc, utcnow
from chatxp.db.tables import Installation, RefreshToken, User, new_uuid
from chatxp.repositories import auth as auth_repository
from chatxp.schemas.auth import (
    AnonymousAuthRequest,
    AuthUser,
    LoginRequest,
    RegisterRequest,
    TokenResponse,
)


@dataclass(frozen=True)
class AuthPrincipal:
    user_id: str
    installation_id: str
    token_version: int


class AuthService:
    _rate_window = timedelta(minutes=15)
    _rate_limit = 5

    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        settings: Settings,
    ) -> None:
        self._session_factory = session_factory
        self._settings = settings
        self._write_lock = asyncio.Lock()
        self._login_failures: dict[tuple[str, str], list[datetime]] = {}
        self._dummy_password_hash = DUMMY_PASSWORD_HASH

    async def ensure_default_admin(self) -> None:
        username = self._normalize_email(self._settings.default_admin_username)
        async with self._write_lock, self._session_factory() as db, db.begin():
            if await auth_repository.registered_user_by_email(db, username) is not None:
                return
            now = utcnow()
            if username == "admin@123.com":
                legacy_admin = await auth_repository.registered_user_by_email(db, "admin")
                if (
                    legacy_admin is not None
                    and legacy_admin.password_hash is not None
                    and await asyncio.to_thread(
                        verify_password, legacy_admin.password_hash, "123"
                    )
                ):
                    legacy_admin.display_name = self._settings.default_admin_username
                    legacy_admin.email_normalized = username
                    legacy_admin.password_hash = await asyncio.to_thread(
                        hash_password, self._settings.default_admin_password
                    )
                    legacy_admin.updated_at = now
                    return
            db.add(
                User(
                    id=new_uuid(),
                    account_type="registered",
                    display_name=self._settings.default_admin_username,
                    email_normalized=username,
                    password_hash=await asyncio.to_thread(
                        hash_password, self._settings.default_admin_password
                    ),
                    created_at=now,
                    updated_at=now,
                )
            )

    async def authenticate_anonymous(self, request: AnonymousAuthRequest) -> TokenResponse:
        now = utcnow()
        installation_id = str(request.installation_id)
        supplied_hash = keyed_hash(request.installation_secret, self._settings.token_hash_secret)
        async with self._write_lock, self._session_factory() as db, db.begin():
            installation = await auth_repository.installation_by_id(db, installation_id)
            user: User | None
            if installation is None:
                user = User(
                    id=new_uuid(),
                    account_type="guest",
                    created_at=now,
                    updated_at=now,
                )
                db.add(user)
                await db.flush()
                installation = Installation(
                    installation_id=installation_id,
                    installation_secret_hash=supplied_hash,
                    user_id=user.id,
                    token_version=1,
                    platform=request.platform,
                    app_version=request.app_version,
                    created_at=now,
                    last_seen_at=now,
                )
                db.add(installation)
            else:
                if not hmac.compare_digest(
                    installation.installation_secret_hash, supplied_hash
                ):
                    raise AppError(401, "AUTH_INVALID", "Installation secret is invalid")
                installation.platform = request.platform
                installation.app_version = request.app_version
                installation.last_seen_at = now
                user = await auth_repository.user_by_id(db, installation.user_id)
            if user is None:
                raise AppError(401, "AUTH_INVALID", "Installation is invalid")
            response, _ = self._issue_token_pair(db, user, installation, now)
            return response

    async def me(self, principal: AuthPrincipal) -> AuthUser:
        async with self._session_factory() as db:
            user = await auth_repository.user_by_id(db, principal.user_id)
            if user is None:
                raise AppError(401, "AUTH_INVALID", "Access token is invalid")
            return self._present_user(user)

    async def register(
        self, principal: AuthPrincipal, request: RegisterRequest
    ) -> TokenResponse:
        email = self._normalize_email(str(request.email))
        password_hash = await asyncio.to_thread(hash_password, request.password)
        now = utcnow()
        try:
            async with self._write_lock, self._session_factory() as db, db.begin():
                user, installation = await self._transition_entities(db, principal)
                if user.account_type != "guest":
                    raise AppError(409, "ALREADY_AUTHENTICATED", "User is already registered")
                await self._require_idle(db, user.id)
                if await auth_repository.registered_user_by_email(db, email) is not None:
                    raise AppError(
                        409, "EMAIL_ALREADY_REGISTERED", "Email is already registered"
                    )
                user.account_type = "registered"
                user.display_name = request.display_name
                user.email_normalized = email
                user.password_hash = password_hash
                user.updated_at = now
                await self._rotate_installation(db, installation, now)
                response, _ = self._issue_token_pair(db, user, installation, now)
                return response
        except IntegrityError as exc:
            raise AppError(409, "EMAIL_ALREADY_REGISTERED", "Email is already registered") from exc

    async def login(self, principal: AuthPrincipal, request: LoginRequest) -> TokenResponse:
        email = self._normalize_email(request.email)
        rate_key = (email, principal.installation_id)
        now = utcnow()
        async with self._write_lock:
            self._require_not_rate_limited(rate_key, now)
            async with self._session_factory() as db, db.begin():
                guest, installation = await self._transition_entities(db, principal)
                if guest.account_type != "guest":
                    raise AppError(409, "ALREADY_AUTHENTICATED", "User is already registered")
                await self._require_idle(db, guest.id)
                target = await auth_repository.registered_user_by_email(db, email)
                password_hash = target.password_hash if target is not None else None
                valid = await asyncio.to_thread(
                    verify_password,
                    password_hash or self._dummy_password_hash,
                    request.password,
                )
                if target is None or password_hash is None or not valid:
                    self._record_login_failure(rate_key, now)
                    raise AppError(401, "INVALID_CREDENTIALS", "Email or password is invalid")

                await auth_repository.transfer_resources(db, guest.id, target.id)
                installation.user_id = target.id
                await self._rotate_installation(db, installation, now)
                await db.flush()
                await auth_repository.delete_user(db, guest.id)
                target.updated_at = now
                self._login_failures.pop(rate_key, None)
                response, _ = self._issue_token_pair(db, target, installation, now)
                return response

    async def logout(self, principal: AuthPrincipal) -> TokenResponse:
        now = utcnow()
        async with self._write_lock, self._session_factory() as db, db.begin():
            user, installation = await self._transition_entities(db, principal)
            if user.account_type != "registered":
                raise AppError(409, "ALREADY_AUTHENTICATED", "User is already a guest")
            await self._require_idle(db, user.id)
            guest = User(
                id=new_uuid(),
                account_type="guest",
                created_at=now,
                updated_at=now,
            )
            db.add(guest)
            await db.flush()
            installation.user_id = guest.id
            await self._rotate_installation(db, installation, now)
            response, _ = self._issue_token_pair(db, guest, installation, now)
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
            installation = await auth_repository.installation_by_id(
                db, existing.installation_id
            )
            if installation is None or installation.user_id != existing.user_id:
                raise AppError(401, "AUTH_INVALID", "Refresh token is invalid")
            user = await auth_repository.user_by_id(db, existing.user_id)
            if user is None:
                raise AppError(401, "AUTH_INVALID", "Refresh token is invalid")
            existing.revoked_at = now
            await db.flush()
            response, replacement_id = self._issue_token_pair(db, user, installation, now)
            await db.flush()
            existing.replaced_by_id = replacement_id
            return response

    async def access_token_principal(self, token: str) -> AuthPrincipal:
        try:
            user_id, installation_id, token_version = decode_access_token(
                token, self._settings.jwt_secret
            )
        except jwt.ExpiredSignatureError as exc:
            raise AppError(401, "AUTH_EXPIRED", "Access token has expired") from exc
        except jwt.InvalidTokenError as exc:
            raise AppError(401, "AUTH_INVALID", "Access token is invalid") from exc
        async with self._session_factory() as db:
            installation = await auth_repository.installation_by_id(db, installation_id)
            if (
                installation is None
                or installation.user_id != user_id
                or installation.token_version != token_version
                or await auth_repository.user_by_id(db, user_id) is None
            ):
                raise AppError(401, "AUTH_INVALID", "Access token is invalid")
        return AuthPrincipal(user_id, installation_id, token_version)

    async def access_token_user(self, token: str) -> str:
        return (await self.access_token_principal(token)).user_id

    async def _transition_entities(
        self, db: AsyncSession, principal: AuthPrincipal
    ) -> tuple[User, Installation]:
        installation = await auth_repository.installation_by_id(
            db, principal.installation_id
        )
        if (
            installation is None
            or installation.user_id != principal.user_id
            or installation.token_version != principal.token_version
        ):
            raise AppError(401, "AUTH_INVALID", "Access token is invalid")
        user = await auth_repository.user_by_id(db, principal.user_id)
        if user is None:
            raise AppError(401, "AUTH_INVALID", "Access token is invalid")
        return user, installation

    async def _require_idle(self, db: AsyncSession, user_id: str) -> None:
        if await auth_repository.has_active_generation(db, user_id):
            raise AppError(
                409,
                "AUTH_TRANSITION_BUSY",
                "Authentication cannot change while a generation is active",
            )

    async def _rotate_installation(
        self, db: AsyncSession, installation: Installation, now: datetime
    ) -> None:
        await auth_repository.revoke_installation_tokens(db, installation.installation_id, now)
        installation.token_version += 1
        installation.last_seen_at = now

    def _issue_token_pair(
        self, db: AsyncSession, user: User, installation: Installation, now: datetime
    ) -> tuple[TokenResponse, str]:
        refresh_token = new_refresh_token()
        refresh_record = RefreshToken(
            id=new_uuid(),
            user_id=user.id,
            installation_id=installation.installation_id,
            token_hash=keyed_hash(refresh_token, self._settings.token_hash_secret),
            expires_at=now + timedelta(seconds=self._settings.refresh_token_ttl_seconds),
            created_at=now,
        )
        db.add(refresh_record)
        return (
            TokenResponse(
                user_id=user.id,
                user=self._present_user(user),
                access_token=create_access_token(
                    user.id,
                    installation.installation_id,
                    installation.token_version,
                    self._settings.jwt_secret,
                    self._settings.access_token_ttl_seconds,
                ),
                expires_in=self._settings.access_token_ttl_seconds,
                refresh_token=refresh_token,
                refresh_expires_in=self._settings.refresh_token_ttl_seconds,
            ),
            refresh_record.id,
        )

    def _require_not_rate_limited(self, key: tuple[str, str], now: datetime) -> None:
        cutoff = now - self._rate_window
        recent = [attempt for attempt in self._login_failures.get(key, []) if attempt > cutoff]
        if recent:
            self._login_failures[key] = recent
        else:
            self._login_failures.pop(key, None)
        if len(recent) >= self._rate_limit:
            raise AppError(429, "LOGIN_RATE_LIMITED", "Too many login attempts")

    def _record_login_failure(self, key: tuple[str, str], now: datetime) -> None:
        self._login_failures.setdefault(key, []).append(now)

    @staticmethod
    def _normalize_email(value: str) -> str:
        return value.strip().casefold()

    @staticmethod
    def _present_user(user: User) -> AuthUser:
        display_name = user.display_name
        avatar_text: str | None = None
        if display_name:
            first = display_name[0]
            avatar_text = first.upper() if "a" <= first <= "z" else first
        return AuthUser(
            id=user.id,
            account_type=user.account_type,
            display_name=display_name,
            email=user.email_normalized,
            avatar_text=avatar_text,
        )
