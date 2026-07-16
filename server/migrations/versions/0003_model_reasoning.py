"""Add public model aliases and reasoning modes.

Revision ID: 0003_model_reasoning
Revises: 0002_registered_accounts
Create Date: 2026-07-16
"""

from __future__ import annotations

import hashlib
import json
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0003_model_reasoning"
down_revision: str | None = "0002_registered_accounts"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    connection = op.get_bind()
    before = _counts(connection)

    op.add_column(
        "sessions",
        sa.Column(
            "reasoning_mode",
            sa.String(20),
            nullable=False,
            server_default="standard",
        ),
    )
    op.add_column("messages", sa.Column("reasoning_mode", sa.String(20)))
    op.add_column(
        "generations",
        sa.Column(
            "reasoning_mode",
            sa.String(20),
            nullable=False,
            server_default="standard",
        ),
    )

    op.execute(sa.text("UPDATE sessions SET model_id = 'chat-5.5' WHERE model_id = 'chat-default'"))
    op.execute(
        sa.text(
            "UPDATE messages SET model_id = 'chat-5.5' "
            "WHERE role = 'assistant' AND model_id = 'chat-default'"
        )
    )
    op.execute(
        sa.text(
            "UPDATE messages SET reasoning_mode = 'standard' WHERE role = 'assistant'"
        )
    )
    op.execute(
        sa.text(
            "UPDATE generations SET model_id = 'chat-5.5' WHERE model_id = 'chat-default'"
        )
    )

    rows = connection.execute(
        sa.text(
            """
            SELECT g.id, g.model_id, g.session_id, m.sequence, m.client_message_id, m.content
            FROM generations AS g
            JOIN messages AS m ON m.id = g.user_message_id
            """
        )
    ).mappings().all()
    for row in rows:
        payload = {
            "client_message_id": row["client_message_id"],
            "session_id": None if int(row["sequence"]) == 1 else row["session_id"],
            "model_id": row["model_id"],
            "reasoning_mode": "standard",
            "content": str(row["content"]).strip(),
        }
        canonical = json.dumps(
            payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")
        )
        fingerprint = hashlib.sha256(canonical.encode()).hexdigest()
        connection.execute(
            sa.text(
                "UPDATE generations SET request_fingerprint = :fingerprint WHERE id = :id"
            ),
            {"fingerprint": fingerprint, "id": row["id"]},
        )

    with op.batch_alter_table("sessions") as batch:
        batch.create_check_constraint(
            "ck_sessions_reasoning_mode",
            "reasoning_mode IN ('standard', 'advanced')",
        )
    with op.batch_alter_table("messages") as batch:
        batch.create_check_constraint(
            "ck_messages_reasoning_mode",
            "reasoning_mode IS NULL OR reasoning_mode IN ('standard', 'advanced')",
        )
    with op.batch_alter_table("generations") as batch:
        batch.create_check_constraint(
            "ck_generations_reasoning_mode",
            "reasoning_mode IN ('standard', 'advanced')",
        )

    after = _counts(connection)
    if before != after:
        raise RuntimeError(f"Model migration changed record counts: {before!r} != {after!r}")
    if connection.exec_driver_sql("PRAGMA foreign_key_check").fetchall():
        raise RuntimeError("Model migration introduced foreign key violations")


def _counts(connection: sa.Connection) -> tuple[int, int, int]:
    return tuple(
        int(connection.exec_driver_sql(f"SELECT COUNT(*) FROM {table}").scalar_one())
        for table in ("sessions", "messages", "generations")
    )  # type: ignore[return-value]


def downgrade() -> None:
    raise RuntimeError("The model-reasoning migration cannot be safely downgraded")
