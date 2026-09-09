from __future__ import annotations

import hashlib
import json
import os
import shutil
import struct
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
NORMALISE = ROOT / "tools/brouter/normalise_rd5_timestamp.py"
EVIDENCE = ROOT / "tools/brouter/write_build_evidence.py"
VERIFY_INPUT = ROOT / "tools/brouter/verify_locked_input.py"


class BuildToolsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.directory = Path(tempfile.mkdtemp())

    def tearDown(self) -> None:
        shutil.rmtree(self.directory)

    def write_lock(self, pbf_hash: str, url: str = "https://example.invalid/ukraine-260901.osm.pbf") -> Path:
        lock = self.directory / "input.env"
        lock.write_text(
            "\n".join((
                "REGION_ID=ua-kharkiv-pedestrian",
                f"PBF_URL={url}",
                "PBF_DATE_UTC=2026-09-04T11:12:00Z",
                f"PBF_SHA256={pbf_hash}",
                "BROUTER_REVISION=d5c0a75d29b5a036525fd5e9466a6376ee54cc3e",
                "SEGMENT=E35_N50.rd5",
                "SOURCE_DATE_EPOCH=1788220800",
                "",
            )),
            encoding="utf-8",
        )
        return lock

    def test_normalises_volatile_rd5_timestamp(self) -> None:
        rd5 = self.directory / "E35_N50.rd5"
        rd5.write_bytes(b"x" * 256 + struct.pack(">q", 1) + b"y" * 105)
        subprocess.run([sys.executable, NORMALISE, rd5, "--epoch", "42"], check=True)
        with rd5.open("rb") as stream:
            stream.seek(-113, os.SEEK_END)
            self.assertEqual(struct.unpack(">q", stream.read(8))[0], 42000)

    def test_canonical_evidence_is_repeatable(self) -> None:
        source = self.directory / "source.pbf"
        source.write_bytes(b"pbf")
        lock = self.write_lock(hashlib.sha256(source.read_bytes()).hexdigest())
        lookup = self.directory / "lookups.dat"
        profile = self.directory / "renav-pedestrian.brf"
        segment = self.directory / "E35_N50.rd5"
        regression = self.directory / "regression.json"
        lookup.write_bytes(b"lookup")
        profile.write_text("profile", encoding="utf-8")
        segment.write_bytes(b"segment")
        regression.write_text('{"geometrySha256":"abc","pointCount":2}\n', encoding="utf-8")
        first, second = self.directory / "one.json", self.directory / "two.json"
        arguments = [sys.executable, EVIDENCE, "--lock", lock, "--lookup", lookup, "--profile", profile, "--segment", segment, "--regression", regression]
        subprocess.run(arguments + ["--output", first], check=True)
        subprocess.run(arguments + ["--output", second], check=True)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        self.assertEqual(json.loads(first.read_text())["artifacts"][2]["name"], "E35_N50.rd5")

    def test_corrupt_or_mutable_input_is_rejected_before_build(self) -> None:
        pbf = self.directory / "source.pbf"
        pbf.write_bytes(b"expected")
        valid_hash = hashlib.sha256(pbf.read_bytes()).hexdigest()
        lock = self.write_lock(valid_hash)
        subprocess.run([sys.executable, VERIFY_INPUT, lock, pbf], check=True)
        pbf.write_bytes(b"corrupt")
        self.assertNotEqual(subprocess.run([sys.executable, VERIFY_INPUT, lock, pbf]).returncode, 0)
        mutable_lock = self.write_lock(valid_hash, "https://example.invalid/ukraine-latest.osm.pbf")
        self.assertNotEqual(subprocess.run([sys.executable, VERIFY_INPUT, mutable_lock, pbf]).returncode, 0)


if __name__ == "__main__":
    unittest.main()
