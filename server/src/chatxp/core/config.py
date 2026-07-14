from __future__ import annotations

from typing import Literal

from pydantic import AliasChoices, BaseModel, Field, model_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class ProviderModelConfig(BaseModel):
    id: str = Field(min_length=1, max_length=100)
    provider_model: str = Field(min_length=1, max_length=200)
    display_name: str = Field(min_length=1, max_length=100)
    description: str | None = None


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="CHATXP_",
        env_file=".env",
        env_file_encoding="utf-8",
        env_ignore_empty=True,
        extra="ignore",
        populate_by_name=True,
    )

    database_url: str = "sqlite+aiosqlite:///./chatxp.db"
    jwt_secret: str = Field(
        default="development-jwt-secret-change-me-32-bytes", min_length=32
    )
    token_hash_secret: str = Field(
        default="development-token-hash-change-me-32-bytes", min_length=32
    )
    access_token_ttl_seconds: int = Field(default=3600, gt=0)
    refresh_token_ttl_seconds: int = Field(default=7_776_000, gt=0)
    ai_default_model: str = Field(
        default="chat-default",
        validation_alias=AliasChoices("AI_DEFAULT_MODEL", "CHATXP_AI_DEFAULT_MODEL"),
    )
    ai_models_json: list[ProviderModelConfig] = Field(
        default_factory=lambda: [
            ProviderModelConfig(
                id="chat-default",
                provider_model="deepseek-v4-flash",
                display_name="5.5 均衡",
                description="适合日常问答与通用任务",
            )
        ],
        validation_alias=AliasChoices("AI_MODELS_JSON", "CHATXP_AI_MODELS_JSON"),
    )
    chat_provider: Literal["fake", "openai"] = "fake"
    ai_base_url: str | None = Field(
        default=None, validation_alias=AliasChoices("AI_BASE_URL", "CHATXP_AI_BASE_URL")
    )
    ai_api_key: str | None = Field(
        default=None, validation_alias=AliasChoices("AI_API_KEY", "CHATXP_AI_API_KEY")
    )
    log_level: str = "INFO"

    @model_validator(mode="after")
    def validate_model_catalog(self) -> Settings:
        ids = [item.id for item in self.ai_models_json]
        if len(ids) != len(set(ids)):
            raise ValueError("AI model ids must be unique")
        if self.ai_default_model not in ids:
            raise ValueError("AI_DEFAULT_MODEL must reference an AI_MODELS_JSON id")
        if self.chat_provider == "openai" and (not self.ai_base_url or not self.ai_api_key):
            raise ValueError("AI_BASE_URL and AI_API_KEY are required for the openai provider")
        return self

    def provider_model(self, public_model_id: str) -> str | None:
        return next(
            (item.provider_model for item in self.ai_models_json if item.id == public_model_id),
            None,
        )
