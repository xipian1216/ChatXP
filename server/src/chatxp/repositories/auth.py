from __future__ import annotations

from datetime import datetime
from typing import cast

from sqlalchemy import delete, func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

from chatxp.db.tables import (
    ChatSession,
    Generation,
    Installation,
    Message,
    RefreshToken,
    User,
    new_uuid,
)


async def installation_by_id(db: AsyncSession, installation_id: str) -> Installation | None:
    return cast(Installation | None, await db.get(Installation, installation_id))


async def user_by_id(db: AsyncSession, user_id: str) -> User | None:
    return cast(User | None, await db.get(User, user_id))


async def registered_user_by_email(db: AsyncSession, email_normalized: str) -> User | None:
    return cast(
        User | None,
        await db.scalar(
            select(User).where(
                User.account_type == "registered",
                User.email_normalized == email_normalized,
            )
        ),
    )


async def refresh_by_hash(db: AsyncSession, token_hash: str) -> RefreshToken | None:
    return cast(
        RefreshToken | None,
        await db.scalar(select(RefreshToken).where(RefreshToken.token_hash == token_hash)),
    )


async def has_active_generation(db: AsyncSession, user_id: str) -> bool:
    count = await db.scalar(
        select(func.count(Generation.id)).where(
            Generation.user_id == user_id,
            Generation.status.in_(("queued", "streaming")),
        )
    )
    return bool(count)


async def revoke_installation_tokens(
    db: AsyncSession, installation_id: str, now: datetime
) -> None:
    await db.execute(
        update(RefreshToken)
        .where(
            RefreshToken.installation_id == installation_id,
            RefreshToken.revoked_at.is_(None),
        )
        .values(revoked_at=now)
    )


async def transfer_resources(db: AsyncSession, source_user_id: str, target_user_id: str) -> None:
    target_message_keys = set(
        await db.scalars(
            select(Message.client_message_id).where(
                Message.user_id == target_user_id,
                Message.client_message_id.is_not(None),
            )
        )
    )
    if target_message_keys:
        conflicting_messages = await db.scalars(
            select(Message).where(
                Message.user_id == source_user_id,
                Message.client_message_id.in_(target_message_keys),
            )
        )
        for message in conflicting_messages:
            message.client_message_id = new_uuid()

    target_request_keys = set(
        await db.scalars(
            select(Generation.client_request_id).where(Generation.user_id == target_user_id)
        )
    )
    if target_request_keys:
        conflicting_generations = await db.scalars(
            select(Generation).where(
                Generation.user_id == source_user_id,
                Generation.client_request_id.in_(target_request_keys),
            )
        )
        for generation in conflicting_generations:
            generation.client_request_id = new_uuid()
    await db.flush()

    for table in (ChatSession, Message, Generation):
        await db.execute(
            update(table).where(table.user_id == source_user_id).values(user_id=target_user_id)
        )


async def delete_user(db: AsyncSession, user_id: str) -> None:
    await db.execute(delete(User).where(User.id == user_id))
