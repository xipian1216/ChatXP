#!/usr/bin/env bash

set -Eeuo pipefail

SERVER_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SERVER_DIR"

if ! command -v uv >/dev/null 2>&1; then
    echo "Error: uv is not installed or is not available in PATH." >&2
    echo "Install uv from https://docs.astral.sh/uv/ and try again." >&2
    exit 1
fi

HOST="${CHATXP_HOST:-0.0.0.0}"
PORT="${CHATXP_PORT:-8000}"

exec uv run chatxp serve --host "$HOST" --port "$PORT" "$@"
