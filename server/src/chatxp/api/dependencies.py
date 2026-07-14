from typing import Annotated, cast

from fastapi import Header, Request

from chatxp.api.errors import AppError
from chatxp.services.auth import AuthService


async def current_user_id(
    request: Request,
    authorization: Annotated[str | None, Header()] = None,
) -> str:
    if authorization is None or not authorization.startswith("Bearer "):
        raise AppError(401, "AUTH_INVALID", "Bearer token is required")
    token = authorization.removeprefix("Bearer ").strip()
    if not token:
        raise AppError(401, "AUTH_INVALID", "Bearer token is required")
    service = cast(AuthService, request.app.state.auth_service)
    return await service.access_token_user(token)
