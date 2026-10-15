import base64
import importlib.util
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("validate_distribution", ROOT / "validate_distribution.py")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
KEY = base64.b64encode(bytes(range(32))).decode("ascii")


class DistributionPolicyTest(unittest.TestCase):
    def test_acceptance_requires_encryption_key(self):
        module.validate("acceptance-20261015-2", "true", KEY)
        for key in ("", "weak", base64.b64encode(b"short").decode("ascii")):
            with self.subTest(key=key), self.assertRaises(ValueError):
                module.validate("acceptance-20261015-2", "true", key)

    def test_acceptance_version_cannot_enter_release_lane(self):
        with self.assertRaises(ValueError):
            module.validate("acceptance-20261015-2", "false", KEY)

    def test_production_version_cannot_enter_acceptance_lane(self):
        with self.assertRaises(ValueError):
            module.validate("2026.10.15-2", "true", KEY)
        module.validate("2026.10.15-2", "false")

    def test_rejects_shell_metacharacters_and_ambiguous_mode(self):
        for version, mode in (("2026.10.15;touch x", "false"), ("2026.10.15-2", "")):
            with self.subTest(version=version, mode=mode), self.assertRaises(ValueError):
                module.validate(version, mode)

    def test_workflow_uploads_only_ciphertext_and_guards_publication(self):
        workflow = (ROOT.parents[1] / ".github/workflows/destination-index-release.yml").read_text(encoding="utf-8")
        self.assertIn("python3 tools/destination-index/validate_distribution.py", workflow)
        self.assertIn("RENAV_ACCEPTANCE_ARTIFACT_KEY: ${{ secrets.RENAV_ACCEPTANCE_ARTIFACT_KEY }}", workflow)
        self.assertIn("one/acceptance-package.tar.enc", workflow)
        upload = workflow.split("uses: actions/upload-artifact@v4", 1)[1]
        self.assertNotIn("one/destination-index.jsonl", upload)
        self.assertNotIn("one/manifest.json", upload)
        for step in ("Refuse an existing immutable version", "Publish immutable assets to R2", "Publish immutable assets"):
            self.assertIn("name: " + step + "\n        if: ${{ !inputs.acceptance_only }}", workflow)


if __name__ == "__main__":
    unittest.main()

