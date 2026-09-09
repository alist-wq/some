#!/usr/bin/env python3
"""Write canonical, reproducible evidence for an unsigned BRouter build."""

from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--lock", required=True, type=Path)
    parser.add_argument("--lookup", required=True, type=Path)
    parser.add_argument("--profile", required=True, type=Path)
    parser.add_argument("--segment", required=True, type=Path)
    parser.add_argument("--regression", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()

    values: dict[str, str] = {}
    for line in args.lock.read_text(encoding="utf-8").splitlines():
        if line and not line.startswith("#"):
            key, value = line.split("=", 1)
            values[key] = value
    expected = {"REGION_ID", "PBF_URL", "PBF_DATE_UTC", "PBF_SHA256", "BROUTER_REVISION", "SEGMENT", "SOURCE_DATE_EPOCH"}
    if set(values) != expected or not all(values.values()) or "PENDING" in values["PBF_SHA256"]:
        raise SystemExit("input lock is incomplete")
    if "latest" in values["PBF_URL"]:
        raise SystemExit("mutable latest PBF inputs are forbidden")
    if args.segment.name != values["SEGMENT"]:
        raise SystemExit("segment does not match input lock")

    evidence = {
        "schemaVersion": 1,
        "regionId": values["REGION_ID"],
        "source": {
            "pbfUrl": values["PBF_URL"],
            "pbfDateUtc": values["PBF_DATE_UTC"],
            "pbfSha256": values["PBF_SHA256"],
        },
        "brouterRevision": values["BROUTER_REVISION"],
        "sourceDateEpoch": int(values["SOURCE_DATE_EPOCH"]),
        "artifacts": [
            {"name": "lookups.dat", "sha256": sha256(args.lookup), "bytes": args.lookup.stat().st_size},
            {"name": "renav-pedestrian.brf", "sha256": sha256(args.profile), "bytes": args.profile.stat().st_size},
            {"name": args.segment.name, "sha256": sha256(args.segment), "bytes": args.segment.stat().st_size},
        ],
        "regression": json.loads(args.regression.read_text(encoding="utf-8")),
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(evidence, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
