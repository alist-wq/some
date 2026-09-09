#!/usr/bin/env python3
"""Fail closed unless a PBF exactly matches a complete immutable input lock."""

from __future__ import annotations

import hashlib
import re
import sys
from pathlib import Path


def values(lock: Path) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in lock.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            result[key] = value
    return result


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def main() -> None:
    lock, pbf = map(Path, sys.argv[1:3])
    config = values(lock)
    expected = config.get("PBF_SHA256", "")
    if "latest" in config.get("PBF_URL", ""):
        raise SystemExit("mutable PBF URL rejected")
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise SystemExit("PBF_SHA256 must be a locked SHA-256")
    if digest(pbf) != expected:
        raise SystemExit("PBF checksum mismatch")


if __name__ == "__main__":
    main()
