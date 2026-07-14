from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Request

from chatxp.api.dependencies import current_user_id
from chatxp.schemas.chat import GenerationDto
from chatxp.schemas.common import DataEnvelope

router = APIRouter(prefix="/generations", tags=["generations"])


@router.get(
    "/by-client-request/{client_request_id}", response_model=DataEnvelope[GenerationDto]
)
async def generation_by_client_request(
    request: Request,
    client_request_id: UUID,
    user_id: Annotated[str, Depends(current_user_id)],
) -> DataEnvelope[GenerationDto]:
    data = await request.app.state.chat_coordinator.generation_by_client_request(
        user_id, str(client_request_id)
    )
    return DataEnvelope(data=data)

