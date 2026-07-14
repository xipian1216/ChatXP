from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, Query, Request, Response, status

from chatxp.api.dependencies import current_user_id
from chatxp.schemas.chat import (
    MessageListData,
    SessionDto,
    SessionListData,
    SessionUpdateRequest,
)
from chatxp.schemas.common import DataEnvelope

router = APIRouter(prefix="/sessions", tags=["sessions"])


@router.get("", response_model=DataEnvelope[SessionListData])
async def list_sessions(
    request: Request,
    user_id: Annotated[str, Depends(current_user_id)],
    q: str | None = None,
    cursor: str | None = None,
    limit: Annotated[int, Query(ge=1, le=100)] = 30,
) -> DataEnvelope[SessionListData]:
    data = await request.app.state.session_service.list_sessions(user_id, q, cursor, limit)
    return DataEnvelope(data=data)


@router.get("/{session_id}", response_model=DataEnvelope[SessionDto])
async def get_session(
    request: Request,
    session_id: UUID,
    user_id: Annotated[str, Depends(current_user_id)],
) -> DataEnvelope[SessionDto]:
    data = await request.app.state.session_service.get_session(user_id, str(session_id))
    return DataEnvelope(data=data)


@router.patch("/{session_id}", response_model=DataEnvelope[SessionDto])
async def update_session(
    request: Request,
    session_id: UUID,
    payload: SessionUpdateRequest,
    user_id: Annotated[str, Depends(current_user_id)],
) -> DataEnvelope[SessionDto]:
    data = await request.app.state.session_service.update_session(
        user_id, str(session_id), payload
    )
    return DataEnvelope(data=data)


@router.delete("/{session_id}", status_code=status.HTTP_204_NO_CONTENT)
async def delete_session(
    request: Request,
    session_id: UUID,
    user_id: Annotated[str, Depends(current_user_id)],
) -> Response:
    await request.app.state.session_service.delete_session(user_id, str(session_id))
    return Response(status_code=status.HTTP_204_NO_CONTENT)


@router.get("/{session_id}/messages", response_model=DataEnvelope[MessageListData])
async def list_messages(
    request: Request,
    session_id: UUID,
    user_id: Annotated[str, Depends(current_user_id)],
    before: str | None = None,
    limit: Annotated[int, Query(ge=1, le=100)] = 50,
) -> DataEnvelope[MessageListData]:
    data = await request.app.state.session_service.list_messages(
        user_id, str(session_id), before, limit
    )
    return DataEnvelope(data=data)
