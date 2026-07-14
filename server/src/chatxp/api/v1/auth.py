from fastapi import APIRouter, Request

from chatxp.schemas.auth import AnonymousAuthRequest, RefreshRequest, TokenResponse
from chatxp.schemas.common import DataEnvelope

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
