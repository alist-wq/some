from pathlib import Path
import importlib.util
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "build_destination_index.py"
SPEC = importlib.util.spec_from_file_location("destination_index", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class DestinationIndexBuildTest(unittest.TestCase):
    def test_address_and_place_records_are_deterministic_and_searchable(self):
        address = MODULE.record(2, 50.0, 36.2, {"addr:housenumber": "1", "addr:street": "Вулиця Харків"})
        place = MODULE.record(1, 50.1, 36.3, {"name": "Музей", "place": "museum"})
        self.assertEqual("node/2", address["stableId"])
        self.assertEqual(["1 вулиця харків", "вулиця харків"], address["searchTokens"])
        self.assertEqual("PLACE", place["sourceType"])
        self.assertIn("музей", place["searchTokens"])

    def test_unsupported_nodes_are_excluded(self):
        self.assertIsNone(MODULE.record(1, 50.0, 36.0, {"amenity": "bench"}))

    def test_tokenization_matches_the_unicode_contract(self):
        self.assertEqual("вулиця харків", MODULE.normalize("Вулиця_Харків"))
        self.assertEqual("cafe museum", MODULE.normalize("Café-Museum"))


if __name__ == "__main__":
    unittest.main()
