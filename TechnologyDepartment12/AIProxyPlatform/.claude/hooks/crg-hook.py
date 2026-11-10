#!/usr/bin/env python3
"""Cross-platform code-review-graph hook for Claude/Grok/Cursor.

Drains hook stdin (hosts always send a JSON event), then optionally runs
``code-review-graph update`` or ``status``. Always exits 0 so a missing
binary or transient failure never fails the host tool pipeline.
"""

from __future__ import annotations

import shutil
import subprocess
import sys
from pathlib import Path


def _drain_stdin() -> None:
    try:
        sys.stdin.read()
    except Exception:
        pass


def _git_toplevel() -> Path | None:
    try:
        completed = subprocess.run(
            ["git", "rev-parse", "--show-toplevel"],
            capture_output=True,
            text=True,
            timeout=10,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        return None
    if completed.returncode != 0:
        return None
    repo = completed.stdout.strip()
    return Path(repo) if repo else None


def main(argv: list[str]) -> int:
    _drain_stdin()

    action = "update"
    if len(argv) > 1 and argv[1] in {"update", "status"}:
        action = argv[1]

    binary = shutil.which("code-review-graph")
    if not binary:
        return 0

    repo = _git_toplevel()
    if repo is None:
        if action == "status":
            print("Not a git repo, skipping", file=sys.stderr)
        return 0

    cmd = [binary, action]
    if action == "update":
        cmd.append("--skip-flows")
    cmd.extend(["--repo", str(repo)])

    try:
        subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=25,
            check=False,
        )
    except (OSError, subprocess.TimeoutExpired):
        pass
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
