from __future__ import annotations

import asyncio
from pathlib import Path

from alembic import command
from alembic.config import Config

SERVER_ROOT = Path(__file__).resolve().parents[3]


async def upgrade_database(database_url: str) -> None:
    config = Config(SERVER_ROOT / "alembic.ini")
    config.set_main_option("script_location", str(SERVER_ROOT / "migrations"))
    config.set_main_option("sqlalchemy.url", database_url.replace("%", "%%"))
    await asyncio.to_thread(command.upgrade, config, "head")
