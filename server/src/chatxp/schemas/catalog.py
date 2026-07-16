from typing import Literal

from pydantic import BaseModel, Field


def default_reasoning_modes() -> list[Literal["standard", "advanced"]]:
    return ["standard", "advanced"]


class ModelCapabilities(BaseModel):
    streaming: bool = True
    attachments: bool = False
    reasoning_modes: list[Literal["standard", "advanced"]] = Field(
        default_factory=default_reasoning_modes
    )


class ModelOption(BaseModel):
    id: str
    display_name: str
    description: str | None
    is_default: bool
    capabilities: ModelCapabilities


class ModelListData(BaseModel):
    items: list[ModelOption]
