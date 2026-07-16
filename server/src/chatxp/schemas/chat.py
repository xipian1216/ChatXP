from __future__ import annotations

import re
from datetime import datetime
from enum import StrEnum
from typing import Any
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


def _session_update_json_schema(schema: dict[str, Any]) -> None:
    schema["minProperties"] = 1
    properties = schema.get("properties", {})
    if not isinstance(properties, dict):
        return
    for field_name in ("title", "model_id", "reasoning_mode", "is_pinned"):
        field_schema = properties.get(field_name)
        if not isinstance(field_schema, dict):
            continue
        any_of = field_schema.get("anyOf")
        if not isinstance(any_of, list):
            continue
        non_null = [item for item in any_of if item.get("type") != "null"]
        if len(non_null) != 1:
            continue
        title = field_schema.get("title")
        field_schema.clear()
        field_schema.update(non_null[0])
        if title is not None:
            field_schema["title"] = title


class MessageRole(StrEnum):
    USER = "user"
    ASSISTANT = "assistant"


class MessageStatus(StrEnum):
    STREAMING = "streaming"
    COMPLETED = "completed"
    FAILED = "failed"


class GenerationStatus(StrEnum):
    QUEUED = "queued"
    STREAMING = "streaming"
    COMPLETED = "completed"
    FAILED = "failed"


class ReasoningMode(StrEnum):
    STANDARD = "standard"
    ADVANCED = "advanced"


class SessionDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    title: str
    model_id: str
    reasoning_mode: ReasoningMode
    is_pinned: bool
    last_message_preview: str | None
    message_count: int
    created_at: datetime
    updated_at: datetime


class MessageDto(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: UUID
    session_id: UUID
    role: MessageRole
    content: str
    status: MessageStatus
    sequence: int
    model_id: str | None
    reasoning_mode: ReasoningMode | None
    client_message_id: UUID | None
    error_code: str | None
    prompt_tokens: int | None
    completion_tokens: int | None
    created_at: datetime
    updated_at: datetime


class SessionListData(BaseModel):
    items: list[SessionDto]
    next_cursor: str | None
    has_more: bool


class MessageListData(BaseModel):
    items: list[MessageDto]
    next_before: str | None
    has_more: bool


class SessionUpdateRequest(BaseModel):
    model_config = ConfigDict(extra="forbid", json_schema_extra=_session_update_json_schema)

    title: str | None = Field(default=None, min_length=1, max_length=100)
    model_id: str | None = Field(default=None, min_length=1, max_length=100)
    reasoning_mode: ReasoningMode | None = None
    is_pinned: bool | None = None

    @model_validator(mode="before")
    @classmethod
    def validate_patch_fields(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value
        fields = {"title", "model_id", "reasoning_mode", "is_pinned"}
        provided = fields.intersection(value)
        if not provided:
            raise ValueError("At least one field must be provided")
        if null_fields := sorted(field for field in provided if value[field] is None):
            raise ValueError(f"Fields cannot be null: {', '.join(null_fields)}")
        return value

    @field_validator("title", "model_id", mode="before")
    @classmethod
    def normalize_text_fields(cls, value: Any) -> Any:
        return value.strip() if isinstance(value, str) else value


class ChatStreamRequest(BaseModel):
    client_request_id: UUID
    client_message_id: UUID
    session_id: UUID | None = None
    model_id: str | None = Field(default=None, min_length=1, max_length=100)
    reasoning_mode: ReasoningMode | None = None
    content: str = Field(min_length=1, max_length=20_000)

    @field_validator("content")
    @classmethod
    def validate_content(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("Content must include at least one non-whitespace character")
        return value

    @field_validator("model_id", mode="before")
    @classmethod
    def normalize_model_id(cls, value: Any) -> Any:
        return value.strip() if isinstance(value, str) else value


class UsageDto(BaseModel):
    prompt_tokens: int
    completion_tokens: int
    total_tokens: int


class GenerationDto(BaseModel):
    id: UUID
    client_request_id: UUID
    status: GenerationStatus
    session_id: UUID
    user_message_id: UUID
    reasoning_mode: ReasoningMode
    assistant_message: MessageDto
    error_code: str | None
    error_message: str | None
    created_at: datetime
    updated_at: datetime


class MetaEvent(BaseModel):
    generation_id: UUID
    client_request_id: UUID
    session_created: bool
    session: SessionDto
    user_message: MessageDto
    assistant_message_id: UUID


class DeltaEvent(BaseModel):
    assistant_message_id: UUID
    sequence: int
    content_delta: str


class DoneEvent(BaseModel):
    assistant_message: MessageDto
    finish_reason: str
    usage: UsageDto | None
    session: SessionDto


class StreamErrorEvent(BaseModel):
    code: str
    message: str
    retryable: bool
    assistant_message_id: UUID


def default_title(content: str) -> str:
    normalized = re.sub(r"[\r\n]+", " ", content.strip())
    return normalized if len(normalized) <= 30 else f"{normalized[:30]}…"


def preview(content: str | None) -> str | None:
    if content is None:
        return None
    return content[:80]


def model_json(model: BaseModel) -> dict[str, Any]:
    return model.model_dump(mode="json")
