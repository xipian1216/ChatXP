from __future__ import annotations

from typing import Literal

from pydantic import AliasChoices, BaseModel, Field, field_validator, model_validator
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
    default_admin_username: str = Field(
        default="admin@123.com", min_length=1, max_length=30
    )
    default_admin_password: str = Field(default="123456", min_length=1, max_length=72)
    ai_default_model: str = Field(
        default="chat-5.5",
        validation_alias=AliasChoices("AI_DEFAULT_MODEL", "CHATXP_AI_DEFAULT_MODEL"),
    )
    ai_models_json: list[ProviderModelConfig] = Field(
        default_factory=lambda: [
            ProviderModelConfig(
                id="chat-5.5",
                provider_model="deepseek-v4-flash",
                display_name="5.5",
                description="适合日常对话的 Flash 模型",
            ),
            ProviderModelConfig(
                id="chat-5.6",
                provider_model="deepseek-v4-pro",
                display_name="5.6",
                description="适合复杂任务的 Pro 模型",
            ),
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

    @field_validator("default_admin_username")
    @classmethod
    def normalize_default_admin_username(cls, value: str) -> str:
        normalized = value.strip()
        if not normalized:
            raise ValueError("CHATXP_DEFAULT_ADMIN_USERNAME must not be blank")
        return normalized

    @model_validator(mode="after")
    def validate_model_catalog(self) -> Settings:
        if len(self.ai_models_json) == 1 and self.ai_models_json[0].id == "chat-default":
            legacy = self.ai_models_json[0]
            self.ai_models_json = [
                legacy.model_copy(
                    update={
                        "id": "chat-5.5",
                        "display_name": "5.5",
                        "description": "适合日常对话的 Flash 模型",
                    }
                ),
                ProviderModelConfig(
                    id="chat-5.6",
                    provider_model="deepseek-v4-pro",
                    display_name="5.6",
                    description="适合复杂任务的 Pro 模型",
                ),
            ]
            if self.ai_default_model == "chat-default":
                self.ai_default_model = "chat-5.5"
        ids = [item.id for item in self.ai_models_json]
        if len(ids) != len(set(ids)):
            raise ValueError("AI model ids must be unique")
        if self.ai_default_model not in ids:
            raise ValueError("AI_DEFAULT_MODEL must reference an AI_MODELS_JSON id")
        if self.ai_default_model != "chat-5.5":
            raise ValueError("AI_DEFAULT_MODEL must be chat-5.5")
        if not {"chat-5.5", "chat-5.6"}.issubset(ids):
            raise ValueError("AI_MODELS_JSON must contain chat-5.5 and chat-5.6")
        if self.chat_provider == "openai" and (not self.ai_base_url or not self.ai_api_key):
            raise ValueError("AI_BASE_URL and AI_API_KEY are required for the openai provider")
        return self

    def provider_model(self, public_model_id: str) -> str | None:
        return next(
            (item.provider_model for item in self.ai_models_json if item.id == public_model_id),
            None,
        )
