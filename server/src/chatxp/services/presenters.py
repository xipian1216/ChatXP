from chatxp.core.time import ensure_utc
from chatxp.db.tables import ChatSession, Message
from chatxp.schemas.chat import MessageDto, SessionDto, preview


def session_dto(
    session: ChatSession, message_count: int, last_content: str | None
) -> SessionDto:
    return SessionDto(
        id=session.id,
        title=session.title,
        model_id=session.model_id,
        is_pinned=session.is_pinned,
        last_message_preview=preview(last_content),
        message_count=message_count,
        created_at=ensure_utc(session.created_at),
        updated_at=ensure_utc(session.updated_at),
    )


def message_dto(message: Message) -> MessageDto:
    return MessageDto(
        id=message.id,
        session_id=message.session_id,
        role=message.role,
        content=message.content,
        status=message.status,
        sequence=message.sequence,
        model_id=message.model_id,
        client_message_id=message.client_message_id,
        error_code=message.error_code,
        prompt_tokens=message.prompt_tokens,
        completion_tokens=message.completion_tokens,
        created_at=ensure_utc(message.created_at),
        updated_at=ensure_utc(message.updated_at),
    )

