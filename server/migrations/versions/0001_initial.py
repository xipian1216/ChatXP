"""Initial ChatXP V1 schema.

Revision ID: 0001_initial
Revises:
Create Date: 2026-07-13
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0001_initial"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "anonymous_users",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column("installation_id", sa.String(36), nullable=False, unique=True),
        sa.Column("installation_secret_hash", sa.String(64), nullable=False),
        sa.Column("platform", sa.String(30), nullable=False),
        sa.Column("app_version", sa.String(50), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("last_seen_at", sa.DateTime(timezone=True), nullable=False),
    )
    op.create_table(
        "refresh_tokens",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("anonymous_users.id", ondelete="CASCADE"),
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
    op.create_table(
        "sessions",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("anonymous_users.id", ondelete="CASCADE"),
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
            sa.ForeignKey("anonymous_users.id", ondelete="CASCADE"),
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
    op.create_index(
        "ix_messages_session_sequence", "messages", ["session_id", "sequence"]
    )
    op.create_table(
        "generations",
        sa.Column("id", sa.String(36), primary_key=True),
        sa.Column(
            "user_id",
            sa.String(36),
            sa.ForeignKey("anonymous_users.id", ondelete="CASCADE"),
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


def downgrade() -> None:
    op.drop_table("generations")
    op.drop_table("messages")
    op.drop_table("sessions")
    op.drop_table("refresh_tokens")
    op.drop_table("anonymous_users")

