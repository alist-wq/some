#!/usr/bin/env python3
"""Create or verify a synthetic, encrypted acceptance-key probe."""

from __future__ import annotations

import argparse
import base64
import hashlib
import hmac
import os
from pathlib import Path
import subprocess


PAYLOAD = b"RENAV synthetic acceptance-key probe v1\n"
MAC_CONTEXT = b"RENAV acceptance-key probe MAC v1"
CIPHER_NAME = "probe.enc"
MAC_NAME = "probe.mac"


def decode_key(encoded: str) -> bytes:
    try:
        key = base64.b64decode(encoded, validate=True)
    except (ValueError, base64.binascii.Error) as error:
        raise ValueError("invalid acceptance probe key") from error
    if len(key) != 32 or base64.b64encode(key).decode("ascii") != encoded:
        raise ValueError("acceptance probe key must encode exactly 32 bytes")
    return key


def mac_for(ciphertext: bytes, key: bytes) -> bytes:
    mac_key = hmac.digest(key, MAC_CONTEXT, "sha256")
    return hmac.digest(mac_key, ciphertext, "sha256")


def openssl_args(decrypt: bool, source: Path | None = None, output: Path | None = None) -> list[str]:
    args = ["openssl", "enc"]
    if decrypt:
        args.append("-d")
    args.extend(["-aes-256-cbc", "-salt", "-pbkdf2", "-iter", "600000"])
    if source is not None:
        args.extend(["-in", str(source)])
    if output is not None:
        args.extend(["-out", str(output)])
    args.extend(["-pass", "env:RENAV_ACCEPTANCE_ARTIFACT_KEY"])
    return args


def create_probe(directory: Path, encoded_key: str) -> None:
    key = decode_key(encoded_key)
    directory.mkdir(parents=True, exist_ok=True)
    cipher = directory / CIPHER_NAME
    mac = directory / MAC_NAME
    if cipher.exists() or mac.exists():
        raise ValueError("probe outputs already exist")
    result = subprocess.run(openssl_args(False, output=cipher), input=PAYLOAD, capture_output=True, check=False)
    if result.returncode or not cipher.is_file():
        cipher.unlink(missing_ok=True)
        raise ValueError("probe encryption failed")
    ciphertext = cipher.read_bytes()
    if not ciphertext.startswith(b"Salted__"):
        cipher.unlink(missing_ok=True)
        raise ValueError("probe ciphertext header is invalid")
    mac.write_text(mac_for(ciphertext, key).hex() + "\n", encoding="ascii")


def verify_probe(directory: Path, encoded_key: str) -> None:
    key = decode_key(encoded_key)
    ciphertext = (directory / CIPHER_NAME).read_bytes()
    expected = (directory / MAC_NAME).read_text(encoding="ascii").strip()
    if len(expected) != 64 or not hmac.compare_digest(mac_for(ciphertext, key).hex(), expected):
        raise ValueError("probe authentication failed")
    result = subprocess.run(openssl_args(True, source=directory / CIPHER_NAME), capture_output=True, check=False)
    if result.returncode or not hmac.compare_digest(result.stdout, PAYLOAD):
        raise ValueError("probe decryption failed")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("operation", choices=("create", "verify"))
    parser.add_argument("directory", type=Path)
    args = parser.parse_args()
    encoded_key = os.environ.get("RENAV_ACCEPTANCE_ARTIFACT_KEY", "")
    try:
        if args.operation == "create":
            create_probe(args.directory, encoded_key)
        else:
            verify_probe(args.directory, encoded_key)
    except (OSError, UnicodeError, ValueError) as error:
        raise SystemExit(f"FAIL: {error}") from None
    print("PASS: synthetic acceptance-key probe")


if __name__ == "__main__":
    main()
