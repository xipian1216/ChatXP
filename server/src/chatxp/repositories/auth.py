from typing import cast

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from chatxp.db.tables import AnonymousUser, RefreshToken


async def user_by_installation(
    db: AsyncSession, installation_id: str
) -> AnonymousUser | None:
    return cast(
        AnonymousUser | None,
        await db.scalar(
            select(AnonymousUser).where(AnonymousUser.installation_id == installation_id)
        ),
    )


async def user_by_id(db: AsyncSession, user_id: str) -> AnonymousUser | None:
    return cast(AnonymousUser | None, await db.get(AnonymousUser, user_id))


async def refresh_by_hash(db: AsyncSession, token_hash: str) -> RefreshToken | None:
    return cast(
        RefreshToken | None,
        await db.scalar(select(RefreshToken).where(RefreshToken.token_hash == token_hash)),
    )
