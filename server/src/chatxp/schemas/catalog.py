from pydantic import BaseModel


class ModelCapabilities(BaseModel):
    streaming: bool = True
    attachments: bool = False


class ModelOption(BaseModel):
    id: str
    display_name: str
    description: str | None
    is_default: bool
    capabilities: ModelCapabilities


class ModelListData(BaseModel):
    items: list[ModelOption]

