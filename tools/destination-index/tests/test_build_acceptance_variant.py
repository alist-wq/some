import json
import tempfile
import unittest
from pathlib import Path
from importlib.util import module_from_spec, spec_from_file_location


spec = spec_from_file_location("build_acceptance_variant", Path(__file__).resolve().parents[1] / "build_acceptance_variant.py")
module = module_from_spec(spec)
spec.loader.exec_module(module)


class AcceptanceVariantTest(unittest.TestCase):
    def test_removes_only_one_outside_record_and_changes_payload(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / "base.jsonl", Path(directory) / "variant.jsonl"
            rows = [
                {"stableId": "node/1", "latitude": 48.0, "longitude": 30.0},
                {"stableId": "node/2", "latitude": 49.99, "longitude": 36.23},
                {"stableId": "node/3", "latitude": 51.0, "longitude": 31.0},
            ]
            source.write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")
            removed, digest = module.make_variant(source, output)
            self.assertEqual("node/1", removed)
            self.assertEqual(rows[1:], [json.loads(line) for line in output.read_text().splitlines()])
            self.assertEqual(64, len(digest))

    def test_refuses_to_change_kharkiv_only_source(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / "base.jsonl", Path(directory) / "variant.jsonl"
            source.write_text('{"stableId":"node/2","latitude":49.99,"longitude":36.23}\n')
            with self.assertRaises(ValueError):
                module.make_variant(source, output)
            self.assertFalse(output.exists())


if __name__ == "__main__":
    unittest.main()

