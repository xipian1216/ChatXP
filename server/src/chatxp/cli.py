from __future__ import annotations

import argparse
import asyncio

import uvicorn

from chatxp.core.config import Settings
from chatxp.db.migrations import upgrade_database


def main() -> None:
    parser = argparse.ArgumentParser(prog="chatxp")
    subparsers = parser.add_subparsers(dest="command", required=True)
    serve = subparsers.add_parser("serve", help="run migrations and start the API")
    serve.add_argument("--host", default="0.0.0.0")
    serve.add_argument("--port", type=int, default=8000)
    serve.add_argument("--reload", action="store_true")
    subparsers.add_parser("migrate", help="upgrade the database to the latest revision")
    arguments = parser.parse_args()

    if arguments.command == "migrate":
        asyncio.run(upgrade_database(Settings().database_url))
        return
    uvicorn.run(
        "chatxp.main:app",
        host=arguments.host,
        port=arguments.port,
        reload=arguments.reload,
        workers=1,
    )


if __name__ == "__main__":
    main()
