from typing import Any

from fastapi import APIRouter

from chatxp.api.v1 import auth, chat, generations, models, sessions
from chatxp.schemas.common import ErrorEnvelope

ERROR_RESPONSES: dict[int | str, dict[str, Any]] = {
    status_code: {"model": ErrorEnvelope}
    for status_code in (400, 401, 404, 409, 429, 500, 502)
}

api_router = APIRouter(prefix="/api/v1", responses=ERROR_RESPONSES)
api_router.include_router(auth.router)
api_router.include_router(models.router)
api_router.include_router(sessions.router)
api_router.include_router(chat.router)
api_router.include_router(generations.router)
