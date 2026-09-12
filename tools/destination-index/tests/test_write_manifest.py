import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "write_manifest.py"


class DestinationIndexManifestTest(unittest.TestCase):
    def test_manifest_contains_deterministic_generation_bounds(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            index = root / "destination-index.jsonl"
            index.write_text(
                "{\"latitude\":50.1,\"longitude\":36.3,\"stableId\":\"node/1\"}\n"
                "{\"latitude\":49.9,\"longitude\":36.1,\"stableId\":\"node/2\"}\n",
                encoding="utf-8",
            )
            lock = root / "locked.env"
            lock.write_text(
                "REGION_ID=ua-kharkiv-pedestrian\nPBF_URL=https://download.geofabrik.de/europe/ukraine-260901.osm.pbf\nPBF_DATE_UTC=2026-09-01\nPBF_SHA256=" + "a" * 64 + "\n",
                encoding="utf-8",
            )
            output = root / "manifest.json"
            subprocess.run([sys.executable, str(SCRIPT), "--index", str(index), "--lock", str(lock), "--package-version", "2026.09.12-1", "--output", str(output)], check=True)
            manifest = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(
                {"minLatitude": 49.9, "maxLatitude": 50.1, "minLongitude": 36.1, "maxLongitude": 36.3},
                manifest["generationBounds"],
            )


if __name__ == "__main__":
    unittest.main()
