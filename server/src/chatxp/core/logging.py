import logging

SENSITIVE_NAMES = (
    "authorization",
    "refresh_token",
    "installation_secret",
    "api_key",
    "prompt",
    "content",
)


class SensitiveDataFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        if not hasattr(record, "request_id"):
            record.request_id = "-"
        message = record.getMessage().lower()
        return not any(name in message for name in SENSITIVE_NAMES)


def configure_logging(level: str) -> None:
    handler = logging.StreamHandler()
    handler.addFilter(SensitiveDataFilter())
    handler.setFormatter(
        logging.Formatter(
            "%(asctime)s %(levelname)s %(name)s request_id=%(request_id)s %(message)s"
        )
    )
    root = logging.getLogger()
    root.handlers.clear()
    root.addHandler(handler)
    root.setLevel(level.upper())
