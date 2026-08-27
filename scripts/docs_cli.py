"""Entrypoint behind `uv run docs`: see the pyproject scripts table."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

DOCS_DIR = Path(__file__).resolve().parent.parent / "docs"
BUILDERS = {"build": "sphinx-build", "watch": "sphinx-autobuild"}


def docs() -> None:
    """Build the documentation, or serve it with live reload on `watch`."""
    command, *args = sys.argv[1:] or ["build"]
    if command not in BUILDERS:
        sys.exit(f"usage: docs [{'|'.join(BUILDERS)}] [sphinx options]")

    sys.exit(subprocess.run(
        [BUILDERS[command], str(DOCS_DIR), str(DOCS_DIR / "_build"), "-b", "dirhtml", *args],
        check=False,
    ).returncode)
