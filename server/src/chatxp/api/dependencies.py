from typing import Annotated, cast

from fastapi import Depends, Header, Request

from chatxp.api.errors import AppError
from chatxp.services.auth import AuthPrincipal, AuthService


async def current_principal(
    request: Request,
    authorization: Annotated[str | None, Header()] = None,
) -> AuthPrincipal:
    if authorization is None or not authorization.startswith("Bearer "):
        raise AppError(401, "AUTH_INVALID", "Bearer token is required")
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise AppError(401, "AUTH_INVALID", "Bearer token is required")
    service = cast(AuthService, request.app.state.auth_service)
    return await service.access_token_principal(token)


async def current_user_id(
    principal: Annotated[AuthPrincipal, Depends(current_principal)],
) -> str:
    return principal.user_id
