from typing import Annotated

from fastapi import APIRouter, Depends, Request

from chatxp.api.dependencies import current_principal
from chatxp.schemas.auth import (
    AnonymousAuthRequest,
    AuthUser,
    LoginRequest,
    RefreshRequest,
    RegisterRequest,
    TokenResponse,
)
from chatxp.schemas.common import DataEnvelope
from chatxp.services.auth import AuthPrincipal

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/anonymous", response_model=DataEnvelope[TokenResponse])
async def anonymous_auth(
    request: Request, body: AnonymousAuthRequest
) -> DataEnvelope[TokenResponse]:
    result = await request.app.state.auth_service.authenticate_anonymous(body)
    return DataEnvelope(data=result)


@router.post("/refresh", response_model=DataEnvelope[TokenResponse])
async def refresh_auth(request: Request, body: RefreshRequest) -> DataEnvelope[TokenResponse]:
    result = await request.app.state.auth_service.refresh(body.refresh_token)
    return DataEnvelope(data=result)


@router.get("/me", response_model=DataEnvelope[AuthUser])
async def current_user(
    request: Request,
    principal: Annotated[AuthPrincipal, Depends(current_principal)],
) -> DataEnvelope[AuthUser]:
    return DataEnvelope(data=await request.app.state.auth_service.me(principal))


@router.post("/register", response_model=DataEnvelope[TokenResponse])
async def register(
    request: Request,
    body: RegisterRequest,
    principal: Annotated[AuthPrincipal, Depends(current_principal)],
) -> DataEnvelope[TokenResponse]:
    result = await request.app.state.auth_service.register(principal, body)
    return DataEnvelope(data=result)


@router.post("/login", response_model=DataEnvelope[TokenResponse])
async def login(
    request: Request,
    body: LoginRequest,
    principal: Annotated[AuthPrincipal, Depends(current_principal)],
) -> DataEnvelope[TokenResponse]:
    result = await request.app.state.auth_service.login(principal, body)
    return DataEnvelope(data=result)


@router.post("/logout", response_model=DataEnvelope[TokenResponse])
async def logout(
    request: Request,
    principal: Annotated[AuthPrincipal, Depends(current_principal)],
) -> DataEnvelope[TokenResponse]:
    result = await request.app.state.auth_service.logout(principal)
    return DataEnvelope(data=result)
