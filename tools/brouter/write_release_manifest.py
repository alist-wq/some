#!/usr/bin/env python3
"""Create the canonical unsigned manifest for a verified BRouter release artifact."""

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
    parser.add_argument("--artifacts", type=Path, required=True)
    parser.add_argument("--package-version", required=True)
    parser.add_argument("--profile-version", required=True)
    parser.add_argument("--map-compatibility-version", required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()

    evidence = json.loads((args.artifacts / "build-evidence.json").read_text(encoding="utf-8"))
    segment_names = [item["name"] for item in evidence["artifacts"] if item["name"].endswith(".rd5")]
    required = {"lookups.dat", "renav-pedestrian.brf", "build-evidence.json"} | set(segment_names)
    if not segment_names or {path.name for path in args.artifacts.iterdir() if path.is_file()} < required:
        raise SystemExit("release input does not contain the complete verified build artifact")
    evidence_artifacts = {item["name"]: item for item in evidence["artifacts"]}
    if set(evidence_artifacts) != {"lookups.dat", "renav-pedestrian.brf"} | set(segment_names):
        raise SystemExit("build evidence does not declare the approved Kharkiv artifact set")
    for name, item in evidence_artifacts.items():
        path = args.artifacts / name
        if sha256(path) != item["sha256"] or path.stat().st_size != item["bytes"]:
            raise SystemExit(f"artifact verification failed: {name}")

    manifest = {
        "schemaVersion": 1,
        "regionId": evidence["regionId"],
        "packageVersion": args.package_version,
        "mapCompatibilityVersion": args.map_compatibility_version,
        "source": evidence["source"],
        "brouterRevision": evidence["brouterRevision"],
        "profile": {**evidence_artifacts["renav-pedestrian.brf"], "version": args.profile_version},
        "lookup": evidence_artifacts["lookups.dat"],
        "segments": [evidence_artifacts[name] for name in segment_names],
        "regression": evidence["regression"],
        "attribution": "© OpenStreetMap contributors, ODbL 1.0",
    }
    args.output.write_text(json.dumps(manifest, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
