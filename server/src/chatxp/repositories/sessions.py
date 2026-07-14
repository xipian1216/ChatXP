from __future__ import annotations

from typing import cast

from sqlalchemy import Select, exists, func, select
from sqlalchemy.ext.asyncio import AsyncSession

from chatxp.db.tables import ChatSession, Generation, Message


def session_projection() -> Select[tuple[ChatSession, int, str | None]]:
    message_count = (
        select(func.count(Message.id))
        .where(Message.session_id == ChatSession.id)
        .correlate(ChatSession)
        .scalar_subquery()
    )
    last_content = (
        select(Message.content)
        .where(Message.session_id == ChatSession.id, Message.content != "")
        .order_by(Message.sequence.desc())
        .limit(1)
        .correlate(ChatSession)
        .scalar_subquery()
    )
    return select(ChatSession, message_count, last_content)


async def session_row(
    db: AsyncSession, user_id: str, session_id: str
) -> tuple[ChatSession, int, str | None] | None:
    result = await db.execute(
        session_projection().where(
            ChatSession.id == session_id,
            ChatSession.user_id == user_id,
        )
    )
    row = result.one_or_none()
    return cast(tuple[ChatSession, int, str | None] | None, tuple(row) if row else None)


async def session_entity(
    db: AsyncSession, user_id: str, session_id: str
) -> ChatSession | None:
    return cast(
        ChatSession | None,
        await db.scalar(
            select(ChatSession).where(
                ChatSession.id == session_id,
                ChatSession.user_id == user_id,
            )
        ),
    )


async def session_has_active_generation(
    db: AsyncSession, user_id: str, session_id: str
) -> bool:
    return bool(
        await db.scalar(
            select(
                exists().where(
                    Generation.user_id == user_id,
                    Generation.session_id == session_id,
                    Generation.status.in_(["queued", "streaming"]),
                )
            )
        )
    )


async def message_entity(db: AsyncSession, user_id: str, message_id: str) -> Message | None:
    return cast(
        Message | None,
        await db.scalar(
            select(Message).where(Message.id == message_id, Message.user_id == user_id)
        ),
    )
