import base64
import importlib.util
import os
from pathlib import Path
import shutil
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "acceptance_key_probe.py"
spec = importlib.util.spec_from_file_location("acceptance_key_probe", SCRIPT)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
KEY = base64.b64encode(bytes(range(32))).decode("ascii")
WRONG_KEY = base64.b64encode(bytes(range(32, 64))).decode("ascii")


class AcceptanceKeyProbeTest(unittest.TestCase):
    def test_rejects_missing_and_malformed_key(self):
        for value in ("", "not-base64!", base64.b64encode(b"short").decode("ascii")):
            with self.subTest(value=value), self.assertRaises(ValueError):
                module.decode_key(value)

    def test_mac_rejects_wrong_key_or_modified_ciphertext(self):
        sample = b"synthetic ciphertext"
        valid = module.mac_for(sample, module.decode_key(KEY))
        self.assertNotEqual(valid, module.mac_for(sample, module.decode_key(WRONG_KEY)))
        self.assertNotEqual(valid, module.mac_for(sample + b"!", module.decode_key(KEY)))

    @unittest.skipUnless(shutil.which("openssl"), "OpenSSL is required for the integration round-trip")
    def test_round_trip_and_tamper_rejection(self):
        with tempfile.TemporaryDirectory() as temporary, patch.dict(os.environ, {"RENAV_ACCEPTANCE_ARTIFACT_KEY": KEY}):
            directory = Path(temporary)
            module.create_probe(directory, KEY)
            module.verify_probe(directory, KEY)
            with self.assertRaises(ValueError):
                module.verify_probe(directory, WRONG_KEY)
            cipher = directory / module.CIPHER_NAME
            cipher.write_bytes(cipher.read_bytes() + b"tampered")
            with self.assertRaises(ValueError):
                module.verify_probe(directory, KEY)

    def test_protected_workflow_excludes_signing_and_pr_secrets(self):
        workflow = (SCRIPT.parents[2] / ".github/workflows/acceptance-key-probe.yml").read_text(encoding="utf-8")
        self.assertIn("github.ref == 'refs/heads/main'", workflow)
        self.assertIn("environment: destination-index-release", workflow)
        self.assertIn("RENAV_ACCEPTANCE_ARTIFACT_KEY: ${{ secrets.RENAV_ACCEPTANCE_ARTIFACT_KEY }}", workflow)
        self.assertNotIn("RENAV_DESTINATION_INDEX_ED25519_PRIVATE_KEY", workflow)
        self.assertNotIn("gh release", workflow)
        self.assertNotIn("aws s3", workflow)
        upload = workflow.split("uses: actions/upload-artifact@v4", 1)[1]
        self.assertIn("probe-output/probe.enc", upload)
        self.assertIn("probe-output/probe.mac", upload)
        self.assertNotIn(".jsonl", upload)


if __name__ == "__main__":
    unittest.main()
