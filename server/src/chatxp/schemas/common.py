from __future__ import annotations

from typing import Any

from pydantic import BaseModel


class DataEnvelope[T](BaseModel):
    data: T


class ErrorBody(BaseModel):
    code: str
    message: str
    request_id: str
    details: dict[str, Any]


class ErrorEnvelope(BaseModel):
    error: ErrorBody


class EmptyData(BaseModel):
    pass
