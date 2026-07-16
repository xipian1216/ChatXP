from __future__ import annotations

import hashlib
from datetime import datetime

from sqlalchemy import and_, or_, select, text
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker

from chatxp.api.errors import AppError
from chatxp.core.cursor import InvalidCursorError, decode_cursor, encode_cursor
from chatxp.core.time import ensure_utc, utcnow
from chatxp.db.tables import ChatSession, Message
from chatxp.repositories.sessions import (
    session_entity,
    session_has_active_generation,
    session_projection,
    session_row,
)
from chatxp.schemas.chat import (
    MessageListData,
    SessionDto,
    SessionListData,
    SessionUpdateRequest,
)
from chatxp.services.catalog import ModelCatalog
from chatxp.services.presenters import message_dto, session_dto


class SessionService:
    def __init__(
        self,
        session_factory: async_sessionmaker[AsyncSession],
        cursor_secret: str,
        model_catalog: ModelCatalog,
    ) -> None:
        self._session_factory = session_factory
        self._cursor_secret = cursor_secret
        self._model_catalog = model_catalog

    async def list_sessions(
        self, user_id: str, query: str | None, cursor: str | None, limit: int
    ) -> SessionListData:
        normalized_query = (query or "").strip()
        if len(normalized_query) > 100:
            raise AppError(
                400,
                "VALIDATION_ERROR",
                "Search query is too long",
                {"fields": [{"path": "query.q", "message": "Must be at most 100 characters"}]},
            )
        query_hash = hashlib.sha256(normalized_query.casefold().encode()).hexdigest()
        statement = session_projection().where(ChatSession.user_id == user_id)

        if normalized_query:
            latest_content = (
                select(Message.content)
                .where(Message.session_id == ChatSession.id, Message.content != "")
                .order_by(Message.sequence.desc())
                .limit(1)
                .correlate(ChatSession)
                .scalar_subquery()
            )
            escaped_query = (
                normalized_query.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
            )
            pattern = f"%{escaped_query}%"
            statement = statement.where(
                or_(
                    ChatSession.title.ilike(pattern, escape="\\"),
                    latest_content.ilike(pattern, escape="\\"),
                )
            )

        if cursor:
            payload = self._decode(cursor)
            if payload.get("kind") != "sessions" or payload.get("q") != query_hash:
                raise AppError(400, "VALIDATION_ERROR", "Cursor does not match this query")
            try:
                pinned = bool(payload["p"])
                updated_at = datetime.fromisoformat(str(payload["u"]).replace("Z", "+00:00"))
                session_id = str(payload["i"])
            except (KeyError, TypeError, ValueError) as exc:
                raise AppError(400, "VALIDATION_ERROR", "Invalid session cursor") from exc
            same_bucket_after = and_(
                ChatSession.is_pinned.is_(pinned),
                or_(
                    ChatSession.updated_at < updated_at,
                    and_(ChatSession.updated_at == updated_at, ChatSession.id < session_id),
                ),
            )
            if pinned:
                statement = statement.where(
                    or_(ChatSession.is_pinned.is_(False), same_bucket_after)
                )
            else:
                statement = statement.where(same_bucket_after)

        statement = statement.order_by(
            ChatSession.is_pinned.desc(), ChatSession.updated_at.desc(), ChatSession.id.desc()
        ).limit(limit + 1)
        async with self._session_factory() as db:
            rows = (await db.execute(statement)).all()

        has_more = len(rows) > limit
        page = rows[:limit]
        items = [session_dto(row[0], int(row[1]), row[2]) for row in page]
        next_cursor = None
        if has_more and page:
            last = page[-1][0]
            next_cursor = encode_cursor(
                {
                    "kind": "sessions",
                    "p": last.is_pinned,
                    "u": ensure_utc(last.updated_at).isoformat().replace("+00:00", "Z"),
                    "i": last.id,
                    "q": query_hash,
                },
                self._cursor_secret,
            )
        return SessionListData(items=items, next_cursor=next_cursor, has_more=has_more)

    async def get_session(self, user_id: str, session_id: str) -> SessionDto:
        async with self._session_factory() as db:
            row = await session_row(db, user_id, session_id)
            if row is None:
                raise AppError(404, "SESSION_NOT_FOUND", "Session not found")
            return session_dto(row[0], int(row[1]), row[2])

    async def update_session(
        self, user_id: str, session_id: str, request: SessionUpdateRequest
    ) -> SessionDto:
        async with self._session_factory() as db:
            try:
                await db.execute(text("BEGIN IMMEDIATE"))
                chat_session = await session_entity(db, user_id, session_id)
                if chat_session is None:
                    raise AppError(404, "SESSION_NOT_FOUND", "Session not found")
                if request.model_id is not None and not self._model_catalog.contains(
                    request.model_id
                ):
                    raise AppError(
                        404,
                        "MODEL_NOT_FOUND",
                        "Model not found",
                    )
                effective_model = request.model_id or chat_session.model_id
                effective_reasoning = (
                    request.reasoning_mode or chat_session.reasoning_mode
                )
                if not self._model_catalog.supports_reasoning(
                    effective_model, effective_reasoning
                ):
                    raise AppError(
                        400,
                        "VALIDATION_ERROR",
                        "Reasoning mode is not supported by this model",
                        {
                            "fields": [
                                {
                                    "path": "body.reasoning_mode",
                                    "message": "Reasoning mode is not supported",
                                }
                            ]
                        },
                    )
                if request.title is not None:
                    chat_session.title = request.title
                if request.model_id is not None:
                    chat_session.model_id = request.model_id
                if request.reasoning_mode is not None:
                    chat_session.reasoning_mode = request.reasoning_mode
                if request.is_pinned is not None:
                    chat_session.is_pinned = request.is_pinned
                chat_session.updated_at = utcnow()
                await db.flush()
                row = await session_row(db, user_id, session_id)
                assert row is not None
                result = session_dto(row[0], int(row[1]), row[2])
                await db.commit()
                return result
            except AppError:
                await db.rollback()
                raise

    async def delete_session(self, user_id: str, session_id: str) -> None:
        async with self._session_factory() as db:
            try:
                await db.execute(text("BEGIN IMMEDIATE"))
                chat_session = await session_entity(db, user_id, session_id)
                if chat_session is None:
                    raise AppError(404, "SESSION_NOT_FOUND", "Session not found")
                if await session_has_active_generation(db, user_id, session_id):
                    raise AppError(
                        409,
                        "SESSION_BUSY",
                        "Session has an active generation",
                    )
                await db.delete(chat_session)
                await db.commit()
            except AppError:
                await db.rollback()
                raise

    async def list_messages(
        self, user_id: str, session_id: str, before: str | None, limit: int
    ) -> MessageListData:
        async with self._session_factory() as db:
            if await session_row(db, user_id, session_id) is None:
                raise AppError(404, "SESSION_NOT_FOUND", "Session not found")
            statement = select(Message).where(
                Message.user_id == user_id, Message.session_id == session_id
            )
            if before:
                payload = self._decode(before)
                if payload.get("kind") != "messages" or payload.get("s") != session_id:
                    raise AppError(400, "VALIDATION_ERROR", "Cursor does not match this session")
                try:
                    raw_sequence = payload["before"]
                    if not isinstance(raw_sequence, int | str):
                        raise ValueError
                    sequence = int(raw_sequence)
                except (KeyError, TypeError, ValueError) as exc:
                    raise AppError(400, "VALIDATION_ERROR", "Invalid message cursor") from exc
                statement = statement.where(Message.sequence < sequence)
            rows = list(
                (
                    await db.scalars(statement.order_by(Message.sequence.desc()).limit(limit + 1))
                ).all()
            )

        has_more = len(rows) > limit
        page_desc = rows[:limit]
        page = list(reversed(page_desc))
        next_before = None
        if has_more and page:
            next_before = encode_cursor(
                {"kind": "messages", "s": session_id, "before": page[0].sequence},
                self._cursor_secret,
            )
        return MessageListData(
            items=[message_dto(item) for item in page],
            next_before=next_before,
            has_more=has_more,
        )

    def _decode(self, cursor: str) -> dict[str, object]:
        try:
            return decode_cursor(cursor, self._cursor_secret)
        except InvalidCursorError as exc:
            raise AppError(400, "VALIDATION_ERROR", "Invalid cursor") from exc
