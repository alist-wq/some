#!/usr/bin/env python3
"""Extract only deterministic BRouter route geometry for regression evidence."""

from __future__ import annotations

import hashlib
import json
import sys
import xml.etree.ElementTree as xml
from pathlib import Path


def main() -> None:
    source, output = map(Path, sys.argv[1:3])
    points = [
        (element.attrib["lat"], element.attrib["lon"])
        for element in xml.parse(source).iter()
        if element.tag.endswith("trkpt")
    ]
    if len(points) < 2:
        raise SystemExit("route GPX contained fewer than two track points")
    canonical = json.dumps(points, separators=(",", ":"), ensure_ascii=True)
    output.write_text(json.dumps({"pointCount": len(points), "geometrySha256": hashlib.sha256(canonical.encode()).hexdigest()}, sort_keys=True, separators=(",", ":")) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
