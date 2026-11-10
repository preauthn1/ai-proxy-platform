#!/usr/bin/env python3
"""Search bundled easy-query skill references.

Usage:
    python scripts/search_references.py selectAutoInclude NavigateFlat
"""

from __future__ import annotations

import re
import sys
from pathlib import Path


def compile_patterns(terms: list[str]) -> list[re.Pattern[str]]:
    return [re.compile(re.escape(term), re.IGNORECASE) for term in terms]


def iter_matches(root: Path, terms: list[str]) -> list[str]:
    refs = root / "references"
    patterns = compile_patterns(terms)
    matches: list[str] = []

    for path in sorted(refs.glob("*.md")):
        try:
            lines = path.read_text(encoding="utf-8").splitlines()
        except UnicodeDecodeError:
            lines = path.read_text().splitlines()

        for lineno, line in enumerate(lines, 1):
            if any(pattern.search(line) for pattern in patterns):
                rel = path.relative_to(root).as_posix()
                matches.append(f"{rel}:{lineno}: {line}")
    return matches


def main() -> int:
    terms = sys.argv[1:]
    if not terms:
        print("usage: search_references.py <term> [term...]")
        return 2

    root = Path(__file__).resolve().parents[1]
    for match in iter_matches(root, terms):
        print(match)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
