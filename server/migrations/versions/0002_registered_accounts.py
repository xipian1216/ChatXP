"""Add registered accounts and installation-bound tokens.

Revision ID: 0002_registered_accounts
Revises: 0001_initial
Create Date: 2026-07-16
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0002_registered_accounts"
down_revision: str | None = "0001_initial"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    connection = op.get_bind()
    before = _resource_counts(connection, legacy=True)
    connection.exec_driver_sql("PRAGMA foreign_keys = OFF")

    for index_name in (
        "ix_generations_session_status",
        "ix_messages_session_sequence",
        "uq_messages_user_client_message",
        "ix_sessions_user_pinned_updated",
        "ix_sessions_user_updated",
        "ix_refresh_tokens_user_id",
    ):
        op.drop_index(index_name)

    for table_name in (
        "generations",
        "messages",
        "sessions",
        "refresh_tokens",
        "anonymous_users",
    ):
        op.rename_table(table_name, f"{table_name}_v1")

    _create_v2_tables()

    op.execute(
        sa.text(
            """
            INSERT INTO users (
                id, account_type, display_name, email_normalized, password_hash,
                created_at, updated_at
            )
            SELECT id, 'guest', NULL, NULL, NULL, created_at, last_seen_at
            FROM anonymous_users_v1
            """
        )
    )
    op.execute(
        sa.text(
            """
            INSERT INTO installations (
                installation_id, installation_secret_hash, user_id, token_version,
                platform, app_version, created_at, last_seen_at
            )
            SELECT installation_id, installation_secret_hash, id, 1,
                   platform, app_version, created_at, last_seen_at
            FROM anonymous_users_v1
            """
        )
    )
    op.execute(
        sa.text(
            """
            INSERT INTO sessions
            SELECT id, user_id, title, model_id, is_pinned, created_at, updated_at
            FROM sessions_v1
            """
        )
    )
    op.execute(
        sa.text(
            """
            INSERT INTO messages
            SELECT id, user_id, session_id, role, content, status, sequence, model_id,
                   client_message_id, error_code, prompt_tokens, completion_tokens,
                   created_at, updated_at
            FROM messages_v1
            """
        )
    )
    op.execute(
        sa.text(
            """
            INSERT INTO generations
            SELECT id, user_id, session_id, client_request_id, user_message_id,
                   assistant_message_id, model_id, status, request_fingerprint,
                   error_code, error_message, created_at, updated_at
            FROM generations_v1
            """
        )
    )
    op.execute(
        sa.text(
            """
            INSERT INTO refresh_tokens (
                id, user_id, installation_id, token_hash, expires_at, revoked_at,
                replaced_by_id, created_at
            )
            SELECT r.id, r.user_id, u.installation_id, r.token_hash, r.expires_at,
                   r.revoked_at, r.replaced_by_id, r.created_at
            FROM refresh_tokens_v1 AS r
            JOIN anonymous_users_v1 AS u ON u.id = r.user_id
            """
        )
    )

    for table_name in (
        "generations_v1",
        "messages_v1",
        "sessions_v1",
        "refresh_tokens_v1",
        "anonymous_users_v1",
    ):
        op.drop_table(table_name)

    after = _resource_counts(connection, legacy=False)
    if before != after:
        raise RuntimeError(f"Account migration changed resource counts: {before!r} != {after!r}")
    violations = connection.exec_driver_sql("PRAGMA foreign_key_check").fetchall()
    if violations:
        raise RuntimeError(f"Account migration introduced foreign key violations: {violations!r}")
    connection.exec_driver_sql("PRAGMA foreign_keys = ON")


def _create_v2_tables() -> None:
    op.create_table(
        "users",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("account_type", sa.String(20), nullable=False),
        sa.Column("display_name", sa.String(30)),
        sa.Column("email_normalized", sa.String(320)),
        sa.Column("password_hash", sa.String(255)),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "account_type IN ('guest', 'registered')", name="ck_users_account_type"
        ),
        sa.CheckConstraint(
            "(account_type = 'guest' AND display_name IS NULL "
            "AND email_normalized IS NULL AND password_hash IS NULL) OR "
            "(account_type = 'registered' AND length(display_name) BETWEEN 1 AND 30 "
            "AND email_normalized IS NOT NULL AND password_hash IS NOT NULL)",
            name="ck_users_account_fields",
        ),
    )
    op.create_index(
        "uq_users_email_normalized",
        "users",
        ["email_normalized"],
        unique=True,
        sqlite_where=sa.text("email_normalized IS NOT NULL"),
    )
    op.create_table(
        "installations",
        sa.Column("installation_id", sa.String(36), primary_key=True),
        sa.Column("installation_secret_hash", sa.String(64), nullable=False),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("token_version", sa.Integer(), nullable=False, server_default="1"),
        sa.Column("platform", sa.String(30), nullable=False),
        sa.Column("app_version", sa.String(50), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_installations_user_id", "installations", ["user_id"])
    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "installation_id",
            sa.String(36),
            sa.ForeignKey("installations.installation_id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("token_hash", sa.String(64), nullable=False, unique=True),
        sa.Column("expires_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("revoked_at", sa.DateTime(timezone=True)),
        sa.Column(
            "replaced_by_id",
            sa.String(36),
            sa.ForeignKey("refresh_tokens.id", ondelete="SET NULL"),
        ),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_refresh_tokens_user_id", "refresh_tokens", ["user_id"])
    op.create_index(
        "ix_refresh_tokens_installation_id", "refresh_tokens", ["installation_id"]
    )
    op.create_table(
        "sessions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("title", sa.String(100), nullable=False),
        sa.Column("model_id", sa.String(100), nullable=False),
        sa.Column("is_pinned", sa.Boolean(), nullable=False, server_default=sa.false()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_index("ix_sessions_user_updated", "sessions", ["user_id", "updated_at"])
    op.create_index(
        "ix_sessions_user_pinned_updated", "sessions", ["user_id", "is_pinned", "updated_at"]
    )
    op.create_table(
        "messages",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "session_id",
            sa.String(36),
            sa.ForeignKey("sessions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("role", sa.String(20), nullable=False),
        sa.Column("content", sa.Text(), nullable=False),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("sequence", sa.Integer(), nullable=False),
        sa.Column("model_id", sa.String(100)),
        sa.Column("client_message_id", sa.String(36)),
        sa.Column("error_code", sa.String(100)),
        sa.Column("prompt_tokens", sa.Integer()),
        sa.Column("completion_tokens", sa.Integer()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint("role IN ('user', 'assistant')", name="ck_messages_role"),
        sa.CheckConstraint(
            "status IN ('streaming', 'completed', 'failed')", name="ck_messages_status"
        ),
        sa.UniqueConstraint("session_id", "sequence", name="uq_messages_session_sequence"),
    )
    op.create_index(
        "uq_messages_user_client_message",
        "messages",
        ["user_id", "client_message_id"],
        unique=True,
        sqlite_where=sa.text("client_message_id IS NOT NULL"),
    )
    op.create_index("ix_messages_session_sequence", "messages", ["session_id", "sequence"])
    op.create_table(
        "generations",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("users.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "session_id",
            sa.String(36),
            sa.ForeignKey("sessions.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("client_request_id", sa.String(36), nullable=False),
        sa.Column(
            "user_message_id",
            sa.String(36),
            sa.ForeignKey("messages.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "assistant_message_id",
            sa.String(36),
            sa.ForeignKey("messages.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column("model_id", sa.String(100), nullable=False),
        sa.Column("status", sa.String(20), nullable=False),
        sa.Column("request_fingerprint", sa.String(64), nullable=False),
        sa.Column("error_code", sa.String(100)),
        sa.Column("error_message", sa.Text()),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.CheckConstraint(
            "status IN ('queued', 'streaming', 'completed', 'failed')",
            name="ck_generations_status",
        ),
        sa.UniqueConstraint("user_id", "client_request_id", name="uq_generations_user_request"),
    )
    op.create_index(
        "ix_generations_session_status", "generations", ["session_id", "status"]
    )


def _resource_counts(connection: sa.Connection, *, legacy: bool) -> tuple[int, int, int, int]:
    names = ("anonymous_users" if legacy else "users", "sessions", "messages", "generations")
    return tuple(
        int(connection.exec_driver_sql(f'SELECT COUNT(*) FROM "{name}"').scalar_one())
        for name in names
    )  # type: ignore[return-value]


def downgrade() -> None:
    raise RuntimeError("The registered-account migration cannot be safely downgraded")
