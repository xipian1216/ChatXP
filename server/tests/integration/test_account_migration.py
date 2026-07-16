from __future__ import annotations

import asyncio
import sqlite3
from pathlib import Path

from alembic import command
from alembic.config import Config


def _alembic_config(database_path: Path) -> Config:
    server_root = Path(__file__).resolve().parents[2]
    config = Config(server_root / "alembic.ini")
    config.set_main_option("script_location", str(server_root / "migrations"))
    config.set_main_option("sqlalchemy.url", f"sqlite+aiosqlite:///{database_path}")
    return config


async def test_existing_anonymous_data_is_preserved_by_account_migration(
    tmp_path: Path,
) -> None:
    database_path = tmp_path / "legacy.db"
    config = _alembic_config(database_path)
    await asyncio.to_thread(command.upgrade, config, "0001_initial")

    now = "2026-07-16 00:00:00+00:00"
    with sqlite3.connect(database_path) as db:
        db.execute(
            """
            INSERT INTO anonymous_users
            (id, installation_id, installation_secret_hash, platform, app_version,
             created_at, last_seen_at)
            VALUES ('user-1', 'installation-1', ?, 'android', '1.0.0', ?, ?)
            """,
            ("a" * 64, now, now),
        )
        db.execute(
            """
            INSERT INTO refresh_tokens
            (id, user_id, token_hash, expires_at, revoked_at, replaced_by_id, created_at)
            VALUES ('refresh-1', 'user-1', ?, ?, NULL, NULL, ?)
            """,
            ("b" * 64, "2027-07-16 00:00:00+00:00", now),
        )
        db.execute(
            """
            INSERT INTO sessions
            (id, user_id, title, model_id, is_pinned, created_at, updated_at)
            VALUES ('session-1', 'user-1', 'legacy', 'chat-default', 0, ?, ?)
            """,
            (now, now),
        )
        db.executemany(
            """
            INSERT INTO messages
            (id, user_id, session_id, role, content, status, sequence, model_id,
             client_message_id, error_code, prompt_tokens, completion_tokens,
             created_at, updated_at)
            VALUES (?, 'user-1', 'session-1', ?, ?, 'completed', ?, ?, ?, NULL,
                    NULL, NULL, ?, ?)
            """,
            (
                ("message-1", "user", "hello", 1, None, "client-message-1", now, now),
                ("message-2", "assistant", "world", 2, "chat-default", None, now, now),
            ),
        )
        db.execute(
            """
            INSERT INTO generations
            (id, user_id, session_id, client_request_id, user_message_id,
             assistant_message_id, model_id, status, request_fingerprint,
             error_code, error_message, created_at, updated_at)
            VALUES ('generation-1', 'user-1', 'session-1', 'client-request-1',
                    'message-1', 'message-2', 'chat-default', 'completed', ?,
                    NULL, NULL, ?, ?)
            """,
            ("c" * 64, now, now),
        )

    await asyncio.to_thread(command.upgrade, config, "head")

    with sqlite3.connect(database_path) as db:
        tables = {
            row[0]
            for row in db.execute("SELECT name FROM sqlite_master WHERE type = 'table'")
        }
        assert "users" in tables
        assert "installations" in tables
        assert "anonymous_users" not in tables
        assert db.execute(
            "SELECT account_type, email_normalized FROM users WHERE id = 'user-1'"
        ).fetchone() == ("guest", None)
        assert db.execute(
            "SELECT user_id, token_version FROM installations "
            "WHERE installation_id = 'installation-1'"
        ).fetchone() == ("user-1", 1)
        assert db.execute(
            "SELECT installation_id FROM refresh_tokens WHERE id = 'refresh-1'"
        ).fetchone() == ("installation-1",)
        assert db.execute(
            "SELECT model_id, reasoning_mode FROM sessions WHERE id = 'session-1'"
        ).fetchone() == ("chat-5.5", "standard")
        assert db.execute(
            "SELECT role, model_id, reasoning_mode FROM messages ORDER BY sequence"
        ).fetchall() == [
            ("user", None, None),
            ("assistant", "chat-5.5", "standard"),
        ]
        assert db.execute(
            "SELECT model_id, reasoning_mode FROM generations WHERE id = 'generation-1'"
        ).fetchone() == ("chat-5.5", "standard")
        assert tuple(
            db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
            for table in ("users", "sessions", "messages", "generations")
        ) == (1, 1, 2, 1)
        assert db.execute("PRAGMA foreign_key_check").fetchall() == []
        assert {row[2] for row in db.execute("PRAGMA foreign_key_list(sessions)")} == {
            "users"
        }
        assert {
            row[2] for row in db.execute("PRAGMA foreign_key_list(refresh_tokens)")
        } == {"users", "installations", "refresh_tokens"}
