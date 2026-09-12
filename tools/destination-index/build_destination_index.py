#!/usr/bin/env python3
"""Create a deterministic node-backed RENAV destination index from a locked OSM PBF."""

from __future__ import annotations

import argparse
import json
import unicodedata
from pathlib import Path


def normalize(value: str) -> str:
    decomposed = unicodedata.normalize("NFD", value)
    value = "".join(
        char for index, char in enumerate(decomposed)
        if not (unicodedata.combining(char) and index and decomposed[index - 1].isascii() and decomposed[index - 1].isalpha())
    )
    value = unicodedata.normalize("NFC", value)
    return " ".join("".join(character if character.isalnum() else " " for character in value.casefold()).split())


def tokens(*values: str | None) -> list[str]:
    return sorted({item for value in values if value for item in [normalize(value)] if item})


def record(node_id: int, latitude: float, longitude: float, tags: dict[str, str]) -> dict[str, object] | None:
    name = tags.get("name")
    house_number, street = tags.get("addr:housenumber"), tags.get("addr:street")
    if house_number and street:
        label = f"{house_number} {street}"
        return {"stableId": f"node/{node_id}", "sourceType": "ADDRESS", "category": None,
                "latitude": latitude, "longitude": longitude, "displayLabel": label,
                "searchTokens": tokens(label, street, tags.get("addr:city"))}
    if name and tags.get("place"):
        return {"stableId": f"node/{node_id}", "sourceType": "PLACE", "category": tags["place"],
                "latitude": latitude, "longitude": longitude, "displayLabel": name,
                "searchTokens": tokens(name, tags.get("name:uk"), tags.get("name:en"), tags["place"])}
    return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    try:
        import osmium
    except ImportError as error:
        raise SystemExit("PyOsmium is required only by the CI PBF build") from error

    records: list[dict[str, object]] = []
    class Handler(osmium.SimpleHandler):
        def node(self, node):
            if not node.location.valid():
                return
            value = record(node.id, node.location.lat, node.location.lon, dict(node.tags))
            if value is not None:
                records.append(value)
    Handler().apply_file(str(args.input), locations=False)
    records.sort(key=lambda value: str(value["stableId"]))
    if len({value["stableId"] for value in records}) != len(records):
        raise SystemExit("duplicate stable IDs")
    args.output.write_text("".join(json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n" for value in records), encoding="utf-8")


if __name__ == "__main__":
    main()
