from __future__ import annotations

import logging
from typing import Any
from uuid import uuid4

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from chatxp.schemas.common import ErrorBody, ErrorEnvelope

logger = logging.getLogger(__name__)


class AppError(Exception):
    def __init__(
        self,
        status_code: int,
        code: str,
        message: str,
        details: dict[str, Any] | None = None,
    ) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message
        self.details = details or {}


def error_response(request: Request, error: AppError) -> JSONResponse:
    request_id = getattr(request.state, "request_id", str(uuid4()))
    body = ErrorEnvelope(
        error=ErrorBody(
            code=error.code,
            message=error.message,
            request_id=request_id,
            details=error.details,
        )
    )
    return JSONResponse(status_code=error.status_code, content=body.model_dump(mode="json"))


def install_error_handlers(app: FastAPI) -> None:
    @app.exception_handler(AppError)
    async def handle_app_error(request: Request, exc: AppError) -> JSONResponse:
        return error_response(request, exc)

    @app.exception_handler(RequestValidationError)
    async def handle_validation_error(
        request: Request, exc: RequestValidationError
    ) -> JSONResponse:
        fields = [
            {"path": ".".join(str(part) for part in item["loc"]), "message": item["msg"]}
            for item in exc.errors()
        ]
        return error_response(
            request,
            AppError(400, "VALIDATION_ERROR", "Request validation failed", {"fields": fields}),
        )

    @app.exception_handler(Exception)
    async def handle_unknown_error(request: Request, exc: Exception) -> JSONResponse:
        logger.exception(
            "unhandled application error",
            extra={"request_id": getattr(request.state, "request_id", "-")},
        )
        return error_response(request, AppError(500, "INTERNAL_ERROR", "Internal server error"))

