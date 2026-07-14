# ChatXP Server

FastAPI implementation of the ChatXP V1 anonymous text-chat backend. The HTTP and SSE
contract is defined in `../docs/frontend-backend-api.md`.

## Local development

```bash
cp .env.example .env
./run.sh
```

The server listens on `0.0.0.0:8000` by default so a phone on the same LAN can connect.
Override the address with `CHATXP_HOST` and `CHATXP_PORT`, or enable development reload
with `./run.sh --reload`.

The public `chat-default` model is displayed as `5.5 均衡` and maps internally to
`deepseek-v4-flash`; provider model names are never exposed through the client contract.
The default `fake` provider replies with `Echo: <last user message>`, so local development
does not require external credentials. To use the real OpenAI-compatible service, set
`CHATXP_CHAT_PROVIDER=openai`, `AI_BASE_URL`, `AI_API_KEY`, and the model
catalog variables from `.env.example`. `AI_BASE_URL` is the API root immediately before
`/chat/completions`.

The application runs Alembic migrations before it reports ready. Only one Uvicorn worker
and one service instance may use the SQLite database.

## Commands

```bash
./run.sh
uv run chatxp migrate
uv run ruff check .
uv run mypy src
uv run pytest
uv run pytest -m live
uv run python scripts/export_openapi.py
```

The live test is skipped unless the OpenAI-compatible provider environment is configured.
