from __future__ import annotations

import json
from pathlib import Path

from chatxp.main import create_app


def main() -> None:
    destination = Path(__file__).resolve().parents[1] / "openapi.json"
    schema = create_app().openapi()
    destination.write_text(
        json.dumps(schema, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()

