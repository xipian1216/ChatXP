from __future__ import annotations

from collections.abc import AsyncIterator
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, Request
from fastapi.openapi.utils import get_openapi
from fastapi.responses import JSONResponse

from chatxp.api.errors import install_error_handlers
from chatxp.api.middleware import RequestIdMiddleware
from chatxp.api.router import api_router
from chatxp.core.config import Settings
from chatxp.core.logging import configure_logging
from chatxp.db.engine import create_database_engine, create_session_factory, database_is_ready
from chatxp.db.migrations import upgrade_database
from chatxp.providers.base import ChatProvider
from chatxp.providers.fake import FakeChatProvider
from chatxp.providers.openai_compatible import OpenAICompatibleProvider
from chatxp.services.auth import AuthService
from chatxp.services.catalog import ModelCatalog
from chatxp.services.chat import ChatCoordinator
from chatxp.services.sessions import SessionService


def create_app(settings: Settings | None = None) -> FastAPI:
    application_settings = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        app.state.ready = False
        await upgrade_database(application_settings.database_url)
        configure_logging(application_settings.log_level)
        engine = create_database_engine(application_settings.database_url)
        session_factory = create_session_factory(engine)
        catalog = ModelCatalog(
            application_settings.ai_models_json, application_settings.ai_default_model
        )
        if application_settings.chat_provider == "openai":
            assert application_settings.ai_base_url is not None
            assert application_settings.ai_api_key is not None
            provider: ChatProvider = OpenAICompatibleProvider(
                application_settings.ai_base_url, application_settings.ai_api_key
            )
        else:
            provider = FakeChatProvider()
        coordinator = ChatCoordinator(session_factory, catalog, provider)

        app.state.settings = application_settings
        app.state.engine = engine
        app.state.session_factory = session_factory
        app.state.model_catalog = catalog
        app.state.auth_service = AuthService(session_factory, application_settings)
        app.state.session_service = SessionService(
            session_factory, application_settings.token_hash_secret, catalog
        )
        app.state.chat_coordinator = coordinator
        app.state.provider = provider
        await coordinator.repair_interrupted()
        app.state.ready = True
        try:
            yield
        finally:
            app.state.ready = False
            await coordinator.shutdown()
            await engine.dispose()

    app = FastAPI(
        title="ChatXP API",
        version="1.0.0",
        lifespan=lifespan,
    )
    app.state.settings = application_settings
    app.state.ready = False
    app.add_middleware(RequestIdMiddleware)
    install_error_handlers(app)
    app.include_router(api_router)

    def contract_openapi() -> dict[str, Any]:
        if app.openapi_schema is not None:
            return app.openapi_schema
        schema = get_openapi(title=app.title, version=app.version, routes=app.routes)
        for path in schema.get("paths", {}).values():
            if not isinstance(path, dict):
                continue
            for operation in path.values():
                if isinstance(operation, dict):
                    responses = operation.get("responses")
                    if isinstance(responses, dict):
                        responses.pop("422", None)
        app.openapi_schema = schema
        return schema

    app.openapi = contract_openapi  # type: ignore[method-assign]

    @app.get("/health/live", tags=["health"])
    async def health_live() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/health/ready", tags=["health"])
    async def health_ready(request: Request) -> JSONResponse:
        if not request.app.state.ready:
            return JSONResponse(status_code=503, content={"status": "not_ready"})
        ready = await database_is_ready(request.app.state.engine)
        return JSONResponse(
            status_code=200 if ready else 503,
            content={"status": "ok" if ready else "not_ready"},
        )

    return app


app = create_app()
