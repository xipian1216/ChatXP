from typing import Annotated

from fastapi import APIRouter, Depends, Request

from chatxp.api.dependencies import current_user_id
from chatxp.schemas.catalog import ModelListData
from chatxp.schemas.common import DataEnvelope

router = APIRouter(prefix="/models", tags=["models"])


@router.get("", response_model=DataEnvelope[ModelListData])
async def list_models(
    request: Request,
    _user_id: Annotated[str, Depends(current_user_id)],
) -> DataEnvelope[ModelListData]:
    return DataEnvelope(data=request.app.state.model_catalog.public_models())

