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
RELEASE_MANIFEST = ROOT / "tools/brouter/write_release_manifest.py"
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
                "SEGMENTS=E35_N45.rd5 E35_N50.rd5",
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
        first_segment = self.directory / "E35_N45.rd5"
        second_segment = self.directory / "E35_N50.rd5"
        first_regression = self.directory / "regression-0.json"
        second_regression = self.directory / "regression-1.json"
        lookup.write_bytes(b"lookup")
        profile.write_text("profile", encoding="utf-8")
        first_segment.write_bytes(b"first segment")
        second_segment.write_bytes(b"second segment")
        first_regression.write_text('{"geometrySha256":"abc","pointCount":2}\n', encoding="utf-8")
        second_regression.write_text('{"geometrySha256":"def","pointCount":3}\n', encoding="utf-8")
        first, second = self.directory / "one.json", self.directory / "two.json"
        arguments = [sys.executable, EVIDENCE, "--lock", lock, "--lookup", lookup, "--profile", profile,
                     "--segment", first_segment, "--segment", second_segment,
                     "--regression", first_regression, "--regression", second_regression]
        subprocess.run(arguments + ["--output", first], check=True)
        subprocess.run(arguments + ["--output", second], check=True)
        self.assertEqual(first.read_bytes(), second.read_bytes())
        evidence = json.loads(first.read_text())
        self.assertEqual(["E35_N45.rd5", "E35_N50.rd5"], [item["name"] for item in evidence["artifacts"][2:]])
        self.assertEqual(2, len(evidence["regression"]))

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

    def test_release_manifest_requires_exact_evidence_and_artifacts(self) -> None:
        artifacts = self.directory / "artifacts"
        artifacts.mkdir()
        lookup, profile = artifacts / "lookups.dat", artifacts / "renav-pedestrian.brf"
        first_segment, second_segment = artifacts / "E35_N45.rd5", artifacts / "E35_N50.rd5"
        lookup.write_bytes(b"lookup")
        profile.write_bytes(b"profile")
        first_segment.write_bytes(b"first segment")
        second_segment.write_bytes(b"second segment")
        regression = [{"geometrySha256": "abc", "pointCount": 2}, {"geometrySha256": "def", "pointCount": 3}]
        evidence = {
            "regionId": "ua-kharkiv-pedestrian",
            "source": {"pbfUrl": "https://example.invalid/ukraine-260901.osm.pbf", "pbfDateUtc": "2026-09-04T11:12:00Z", "pbfSha256": "a" * 64},
            "brouterRevision": "d5c0a75d29b5a036525fd5e9466a6376ee54cc3e",
            "artifacts": [
                {"name": path.name, "sha256": hashlib.sha256(path.read_bytes()).hexdigest(), "bytes": path.stat().st_size}
                for path in (lookup, profile, first_segment, second_segment)
            ],
            "regression": regression,
        }
        (artifacts / "build-evidence.json").write_text(json.dumps(evidence), encoding="utf-8")
        output = artifacts / "manifest.json"
        subprocess.run([sys.executable, RELEASE_MANIFEST, "--artifacts", artifacts, "--package-version", "2026.09.04-1", "--profile-version", "renav-pedestrian-1", "--map-compatibility-version", "openfreemap-2026-09", "--output", output], check=True)
        manifest = json.loads(output.read_text())
        self.assertEqual("ua-kharkiv-pedestrian", manifest["regionId"])
        self.assertEqual("renav-pedestrian-1", manifest["profile"]["version"])
        self.assertEqual(["E35_N45.rd5", "E35_N50.rd5"], [entry["name"] for entry in manifest["segments"]])
        first_segment.write_bytes(b"corrupt")
        self.assertNotEqual(subprocess.run([sys.executable, RELEASE_MANIFEST, "--artifacts", artifacts, "--package-version", "2026.09.04-1", "--profile-version", "renav-pedestrian-1", "--map-compatibility-version", "openfreemap-2026-09", "--output", output]).returncode, 0)


if __name__ == "__main__":
    unittest.main()
