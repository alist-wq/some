#!/usr/bin/env python3
"""Remove one out-of-beta record for a distinct, non-production signed test index.

The Kharkiv coverage is unchanged.  This file is only called by the acceptance-only
CI path; it is not part of the production destination-index build.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


KHARKIV_BOUNDS = (49.80, 35.90, 50.20, 36.50)


def make_variant(source: Path, destination: Path) -> tuple[str, str]:
    selected_id: str | None = None
    original = hashlib.sha256()
    changed = hashlib.sha256()
    with source.open("rb") as incoming, destination.open("wb") as outgoing:
        for line in incoming:
            original.update(line)
            item = json.loads(line)
            latitude = float(item["latitude"])
            longitude = float(item["longitude"])
            inside = (KHARKIV_BOUNDS[0] <= latitude <= KHARKIV_BOUNDS[2]
                      and KHARKIV_BOUNDS[1] <= longitude <= KHARKIV_BOUNDS[3])
            if selected_id is None and not inside:
                selected_id = str(item["stableId"])
                continue
            outgoing.write(line)
            changed.update(line)
    if selected_id is None or original.digest() == changed.digest():
        destination.unlink(missing_ok=True)
        raise ValueError("acceptance variant requires a distinct out-of-Kharkiv record")
    return selected_id, changed.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    removed, digest = make_variant(args.input, args.output)
    print(json.dumps({"removedStableId": removed, "variantSha256": digest}, sort_keys=True))


if __name__ == "__main__":
    main()

