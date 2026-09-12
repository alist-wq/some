#!/usr/bin/env python3
"""Write the canonical unsigned manifest for a RENAV destination-index release."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def locked_values(path: Path) -> dict[str, str]:
    return dict(line.split("=", 1) for line in path.read_text(encoding="utf-8").splitlines() if line and not line.startswith("#"))


def bounds(records: list[dict[str, object]]) -> dict[str, float]:
    if not records:
        raise ValueError("index must contain at least one record")
    latitudes = [float(record["latitude"]) for record in records]
    longitudes = [float(record["longitude"]) for record in records]
    return {
        "minLatitude": min(latitudes), "maxLatitude": max(latitudes),
        "minLongitude": min(longitudes), "maxLongitude": max(longitudes),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--index", type=Path, required=True)
    parser.add_argument("--lock", type=Path, required=True)
    parser.add_argument("--package-version", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    lock = locked_values(args.lock)
    if "latest" in lock["PBF_URL"] or not args.package_version.strip():
        raise SystemExit("release input must be immutable")
    records = [json.loads(line) for line in args.index.read_text(encoding="utf-8").splitlines() if line]
    if records != sorted(records, key=lambda value: value["stableId"]) or len({value["stableId"] for value in records}) != len(records):
        raise SystemExit("index records must have unique sorted stable IDs")
    manifest = {
        "schemaVersion": 1, "regionId": lock["REGION_ID"], "packageVersion": args.package_version,
        "mapCompatibilityVersion": "openfreemap-2026-09",
        "source": {"url": lock["PBF_URL"], "dateUtc": lock["PBF_DATE_UTC"], "sha256": lock["PBF_SHA256"]},
        "generationBounds": bounds(records), "recordCount": len(records), "indexChecksumSha256": sha256(args.index),
        "artifacts": [{"name": args.index.name, "sha256": sha256(args.index), "bytes": args.index.stat().st_size}],
        "attribution": "© OpenStreetMap contributors, ODbL 1.0",
    }
    args.output.write_text(json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
