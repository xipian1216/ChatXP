from __future__ import annotations

import asyncio
import logging
import time
from dataclasses import dataclass

from sqlalchemy import func, select, text, update
from sqlalchemy.exc import IntegrityError
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from chatxp.api.errors import AppError
from chatxp.core.security import request_fingerprint
from chatxp.core.time import ensure_utc, utcnow
from chatxp.db.tables import ChatSession, Generation, Message, new_uuid
from chatxp.providers.base import (
    ChatProvider,
    ProviderDelta,
    ProviderDone,
    ProviderMessage,
    ProviderRateLimitError,
    ProviderUnavailableError,
    ProviderUsage,
)
from chatxp.repositories.sessions import session_projection
from chatxp.schemas.chat import (
    ChatStreamRequest,
    DoneEvent,
    GenerationDto,
    MessageDto,
    MetaEvent,
    SessionDto,
    StreamErrorEvent,
    UsageDto,
    default_title,
    model_json,
)
from chatxp.services.catalog import ModelCatalog
from chatxp.services.presenters import message_dto, session_dto
from chatxp.streaming.broker import GenerationBroker, StreamEvent, Subscription

logger = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class StreamContext:
    generation: GenerationDto
    session: SessionDto
    user_message: MessageDto
    session_created: bool

    def meta(self) -> MetaEvent:
        return MetaEvent(
            generation_id=self.generation.id,
            client_request_id=self.generation.client_request_id,
            session_created=self.session_created,
            session=self.session,
            user_message=self.user_message,
            assistant_message_id=self.generation.assistant_message.id,
        )


class ChatCoordinator:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        catalog: ModelCatalog,
        provider: ChatProvider,
    ) -> None:
        self._session_factory = session_factory
        self._catalog = catalog
        self._provider = provider
        self._broker = GenerationBroker()
        self._tasks: dict[str, asyncio.Task[None]] = {}
        self._task_lock = asyncio.Lock()
        self._write_lock = asyncio.Lock()

    async def prepare(self, user_id: str, request: ChatStreamRequest) -> StreamContext:
        session_created = request.session_id is None
        async with self._write_lock, self._session_factory() as db:
            try:
                await db.execute(text("BEGIN IMMEDIATE"))
                existing = await db.scalar(
                    select(Generation).where(
                        Generation.user_id == user_id,
                        Generation.client_request_id == str(request.client_request_id),
                    )
                )
                if existing is not None:
                    model_id = request.model_id or existing.model_id
                    fingerprint = self._fingerprint(request, model_id)
                    if not _constant_string_equal(existing.request_fingerprint, fingerprint):
                        raise AppError(
                            409,
                            "VALIDATION_ERROR",
                            "Idempotency key was reused with different request data",
                            {"reason": "IDEMPOTENCY_KEY_REUSED"},
                        )
                    await db.commit()
                    return await self._context_by_generation(
                        user_id, existing.id, session_created=session_created
                    )

                reused_message = await db.scalar(
                    select(Message.id).where(
                        Message.user_id == user_id,
                        Message.client_message_id == str(request.client_message_id),
                    )
                )
                if reused_message is not None:
                    raise AppError(
                        409,
                        "VALIDATION_ERROR",
                        "Idempotency key was reused with different request data",
                        {"reason": "IDEMPOTENCY_KEY_REUSED"},
                    )

                chat_session: ChatSession
                if request.session_id is None:
                    if request.model_id is None:
                        raise AppError(
                            400,
                            "VALIDATION_ERROR",
                            "model_id is required for a new session",
                            {"fields": [{"path": "body.model_id", "message": "Field required"}]},
                        )
                    model_id = request.model_id
                    self._require_model(model_id)
                    now = utcnow()
                    chat_session = ChatSession(
                        id=new_uuid(),
                        user_id=user_id,
                        title=default_title(request.content),
                        model_id=model_id,
                        is_pinned=False,
                        created_at=now,
                        updated_at=now,
                    )
                    db.add(chat_session)
                    first_sequence = 1
                else:
                    existing_session = await db.scalar(
                        select(ChatSession).where(
                            ChatSession.id == str(request.session_id),
                            ChatSession.user_id == user_id,
                        )
                    )
                    if existing_session is None:
                        raise AppError(404, "SESSION_NOT_FOUND", "Session not found")
                    chat_session = existing_session
                    model_id = request.model_id or chat_session.model_id
                    self._require_model(model_id)
                    maximum = await db.scalar(
                        select(func.max(Message.sequence)).where(
                            Message.session_id == chat_session.id
                        )
                    )
                    first_sequence = int(maximum or 0) + 1
                    chat_session.updated_at = utcnow()

                now = utcnow()
                user_message = Message(
                    id=new_uuid(),
                    user_id=user_id,
                    session_id=chat_session.id,
                    role="user",
                    content=request.content,
                    status="completed",
                    sequence=first_sequence,
                    client_message_id=str(request.client_message_id),
                    created_at=now,
                    updated_at=now,
                )
                assistant_message = Message(
                    id=new_uuid(),
                    user_id=user_id,
                    session_id=chat_session.id,
                    role="assistant",
                    content="",
                    status="streaming",
                    sequence=first_sequence + 1,
                    model_id=model_id,
                    created_at=now,
                    updated_at=now,
                )
                generation = Generation(
                    id=new_uuid(),
                    user_id=user_id,
                    session_id=chat_session.id,
                    client_request_id=str(request.client_request_id),
                    user_message_id=user_message.id,
                    assistant_message_id=assistant_message.id,
                    model_id=model_id,
                    status="queued",
                    request_fingerprint=self._fingerprint(request, model_id),
                    created_at=now,
                    updated_at=now,
                )
                # Flush parent rows before the generation because the ORM objects intentionally
                # avoid relationships; the database foreign keys remain the source of truth.
                db.add_all([user_message, assistant_message])
                await db.flush()
                db.add(generation)
                await db.commit()
            except AppError:
                await db.rollback()
                raise
            except IntegrityError as exc:
                await db.rollback()
                raise AppError(
                    409,
                    "VALIDATION_ERROR",
                    "Concurrent or duplicate chat request",
                    {"reason": "IDEMPOTENCY_KEY_REUSED"},
                ) from exc

        return await self._context_by_generation(
            user_id, generation.id, session_created=session_created
        )

    async def generation_by_client_request(
        self, user_id: str, client_request_id: str
    ) -> GenerationDto:
        async with self._session_factory() as db:
            generation = await db.scalar(
                select(Generation).where(
                    Generation.user_id == user_id,
                    Generation.client_request_id == client_request_id,
                )
            )
        if generation is None:
            raise AppError(404, "GENERATION_NOT_FOUND", "Generation not found")
        return (await self._context_by_generation(user_id, generation.id, False)).generation

    def subscribe(self, generation_id: str) -> Subscription:
        return self._broker.subscribe(generation_id)

    def unsubscribe(self, subscription: Subscription) -> None:
        self._broker.unsubscribe(subscription)

    async def ensure_running(self, generation_id: str) -> None:
        async with self._task_lock:
            task = self._tasks.get(generation_id)
            if task is not None and not task.done():
                return
            task = asyncio.create_task(
                self._run_generation(generation_id), name=f"generation:{generation_id}"
            )
            self._tasks[generation_id] = task
            task.add_done_callback(lambda _: self._tasks.pop(generation_id, None))

    async def repair_interrupted(self) -> int:
        now = utcnow()
        async with self._session_factory() as db, db.begin():
            generation_ids = list(
                (
                    await db.scalars(
                        select(Generation.id).where(
                            Generation.status.in_(["queued", "streaming"])
                        )
                    )
                ).all()
            )
            if not generation_ids:
                return 0
            assistant_ids = list(
                (
                    await db.scalars(
                        select(Generation.assistant_message_id).where(
                            Generation.id.in_(generation_ids)
                        )
                    )
                ).all()
            )
            await db.execute(
                update(Generation)
                .where(Generation.id.in_(generation_ids))
                .values(
                    status="failed",
                    error_code="GENERATION_INTERRUPTED",
                    error_message="Generation interrupted by service restart",
                    updated_at=now,
                )
            )
            await db.execute(
                update(Message)
                .where(Message.id.in_(assistant_ids))
                .values(status="failed", error_code="GENERATION_INTERRUPTED", updated_at=now)
            )
            return len(generation_ids)

    async def shutdown(self) -> None:
        tasks = list(self._tasks.values())
        for task in tasks:
            task.cancel()
        if tasks:
            await asyncio.gather(*tasks, return_exceptions=True)
        self._tasks.clear()

    async def _run_generation(self, generation_id: str) -> None:
        accumulated = ""
        try:
            generation, provider_model, messages, accumulated = await self._start(generation_id)
            persisted_length = len(accumulated)
            last_persisted = time.monotonic()
            completed: ProviderDone | None = None
            async for event in self._provider.stream_chat(provider_model, messages):
                if isinstance(event, ProviderDelta):
                    accumulated += event.content
                    self._broker.publish(
                        generation_id,
                        StreamEvent(
                            "delta",
                            {
                                "assistant_message_id": generation.assistant_message_id,
                                "content_delta": event.content,
                            },
                        ),
                    )
                    now = time.monotonic()
                    if len(accumulated) - persisted_length >= 256 or now - last_persisted >= 0.5:
                        await self._persist_partial(generation_id, accumulated)
                        persisted_length = len(accumulated)
                        last_persisted = now
                elif isinstance(event, ProviderDone):
                    completed = event
            if completed is None:
                raise ProviderUnavailableError("upstream stream ended without a completion event")
            await self._complete(generation_id, accumulated, completed.usage)
            context = await self._context_by_generation(
                generation.user_id, generation_id, session_created=False
            )
            done = DoneEvent(
                assistant_message=context.generation.assistant_message,
                finish_reason=completed.finish_reason,
                usage=_usage_dto(completed.usage),
                session=context.session,
            )
            self._broker.publish(generation_id, StreamEvent("done", model_json(done)))
        except asyncio.CancelledError:
            raise
        except ProviderRateLimitError as exc:
            await self._fail(
                generation_id,
                accumulated,
                "PROVIDER_RATE_LIMIT",
                "Upstream provider rate limited the request",
                retryable=True,
                cause=exc,
            )
        except ProviderUnavailableError as exc:
            await self._fail(
                generation_id,
                accumulated,
                "PROVIDER_UNAVAILABLE",
                "Upstream provider unavailable",
                retryable=True,
                cause=exc,
            )
        except Exception as exc:
            await self._fail(
                generation_id,
                accumulated,
                "PROVIDER_UNAVAILABLE",
                "Upstream provider unavailable",
                retryable=True,
                cause=exc,
            )

    async def _start(
        self, generation_id: str
    ) -> tuple[Generation, str, list[ProviderMessage], str]:
        async with self._session_factory() as db, db.begin():
            generation = await db.get(Generation, generation_id)
            if generation is None:
                raise ProviderUnavailableError("generation disappeared")
            assistant = await db.get(Message, generation.assistant_message_id)
            user_message = await db.get(Message, generation.user_message_id)
            if assistant is None or user_message is None:
                raise ProviderUnavailableError("generation messages disappeared")
            generation.status = "streaming"
            generation.updated_at = utcnow()
            history = list(
                (
                    await db.scalars(
                        select(Message)
                        .where(
                            Message.session_id == generation.session_id,
                            Message.sequence <= user_message.sequence,
                            Message.status == "completed",
                        )
                        .order_by(Message.sequence.asc())
                    )
                ).all()
            )
            provider_model = self._catalog.provider_model(generation.model_id)
            if provider_model is None:
                raise ProviderUnavailableError("configured model disappeared")
            messages = [ProviderMessage(item.role, item.content) for item in history]
            accumulated = assistant.content
            return generation, provider_model, messages, accumulated

    async def _persist_partial(self, generation_id: str, content: str) -> None:
        now = utcnow()
        async with self._session_factory() as db, db.begin():
            generation = await db.get(Generation, generation_id)
            if generation is None:
                return
            assistant = await db.get(Message, generation.assistant_message_id)
            if assistant is None:
                return
            assistant.content = content
            assistant.updated_at = now
            generation.updated_at = now

    async def _complete(
        self, generation_id: str, content: str, usage: ProviderUsage | None
    ) -> None:
        now = utcnow()
        async with self._session_factory() as db, db.begin():
            generation = await db.get(Generation, generation_id)
            if generation is None:
                return
            assistant = await db.get(Message, generation.assistant_message_id)
            chat_session = await db.get(ChatSession, generation.session_id)
            if assistant is None or chat_session is None:
                return
            assistant.content = content
            assistant.status = "completed"
            assistant.error_code = None
            assistant.prompt_tokens = usage.prompt_tokens if usage else None
            assistant.completion_tokens = usage.completion_tokens if usage else None
            assistant.updated_at = now
            generation.status = "completed"
            generation.error_code = None
            generation.error_message = None
            generation.updated_at = now
            chat_session.updated_at = now

    async def _fail(
        self,
        generation_id: str,
        content: str,
        code: str,
        message: str,
        retryable: bool,
        cause: Exception,
    ) -> None:
        logger.warning(
            "generation failed generation_id=%s error_type=%s",
            generation_id,
            type(cause).__name__,
            extra={"request_id": "-"},
        )
        assistant_message_id: str | None = None
        now = utcnow()
        async with self._session_factory() as db, db.begin():
            generation = await db.get(Generation, generation_id)
            if generation is None:
                return
            assistant_message_id = generation.assistant_message_id
            assistant = await db.get(Message, generation.assistant_message_id)
            chat_session = await db.get(ChatSession, generation.session_id)
            if assistant is not None:
                assistant.content = content
                assistant.status = "failed"
                assistant.error_code = code
                assistant.updated_at = now
            if chat_session is not None:
                chat_session.updated_at = now
            generation.status = "failed"
            generation.error_code = code
            generation.error_message = message
            generation.updated_at = now
        if assistant_message_id:
            error = StreamErrorEvent(
                code=code,
                message=message,
                retryable=retryable,
                assistant_message_id=assistant_message_id,
            )
            self._broker.publish(generation_id, StreamEvent("error", model_json(error)))

    async def _context_by_generation(
        self, user_id: str, generation_id: str, session_created: bool
    ) -> StreamContext:
        async with self._session_factory() as db:
            generation = await db.scalar(
                select(Generation).where(
                    Generation.id == generation_id, Generation.user_id == user_id
                )
            )
            if generation is None:
                raise AppError(404, "GENERATION_NOT_FOUND", "Generation not found")
            user_message = await db.get(Message, generation.user_message_id)
            assistant_message = await db.get(Message, generation.assistant_message_id)
            session_result = (
                await db.execute(
                    session_projection().where(
                        ChatSession.id == generation.session_id,
                        ChatSession.user_id == user_id,
                    )
                )
            ).one_or_none()
            if user_message is None or assistant_message is None or session_result is None:
                raise AppError(500, "INTERNAL_ERROR", "Generation data is incomplete")
            generation_dto = GenerationDto(
                id=generation.id,
                client_request_id=generation.client_request_id,
                status=generation.status,
                session_id=generation.session_id,
                user_message_id=generation.user_message_id,
                assistant_message=message_dto(assistant_message),
                error_code=generation.error_code,
                error_message=generation.error_message,
                created_at=ensure_utc(generation.created_at),
                updated_at=ensure_utc(generation.updated_at),
            )
            return StreamContext(
                generation=generation_dto,
                session=session_dto(
                    session_result[0], int(session_result[1]), session_result[2]
                ),
                user_message=message_dto(user_message),
                session_created=session_created,
            )

    def _require_model(self, model_id: str) -> None:
        if not self._catalog.contains(model_id):
            raise AppError(404, "MODEL_NOT_FOUND", "Model not found")

    @staticmethod
    def _fingerprint(request: ChatStreamRequest, model_id: str) -> str:
        return request_fingerprint(
            {
                "client_message_id": str(request.client_message_id),
                "session_id": str(request.session_id) if request.session_id else None,
                "model_id": model_id,
                # Keep the original V1 fingerprint normalization so requests created before
                # Markdown whitespace preservation remain safely retryable after deployment.
                "content": request.content.strip(),
            }
        )


def _constant_string_equal(left: str, right: str) -> bool:
    import hmac

    return hmac.compare_digest(left, right)


def _usage_dto(usage: ProviderUsage | None) -> UsageDto | None:
    if usage is None:
        return None
    return UsageDto(
        prompt_tokens=usage.prompt_tokens,
        completion_tokens=usage.completion_tokens,
        total_tokens=usage.total_tokens,
    )
