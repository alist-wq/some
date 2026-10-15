#!/usr/bin/env python3
"""Fail closed before signing if a package targets the wrong distribution lane."""

import base64
import os
import re


VERSION = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")


def validate(version: str, acceptance_only: str, acceptance_key: str = "") -> None:
    if not VERSION.fullmatch(version):
        raise ValueError("package version contains unsupported characters")
    if acceptance_only not in {"true", "false"}:
        raise ValueError("distribution mode must be explicit")
    is_acceptance = version.startswith("acceptance-")
    if (acceptance_only == "true") != is_acceptance:
        raise ValueError("acceptance versions and production publication must remain separate")
    if is_acceptance:
        try:
            key = base64.b64decode(acceptance_key, validate=True)
        except (ValueError, base64.binascii.Error) as error:
            raise ValueError("acceptance encryption key is missing or invalid") from error
        if len(key) != 32 or base64.b64encode(key).decode("ascii") != acceptance_key:
            raise ValueError("acceptance encryption key must be canonical base64 of 32 random bytes")


if __name__ == "__main__":
    try:
        validate(
            os.environ.get("PACKAGE_VERSION", ""),
            os.environ.get("ACCEPTANCE_ONLY", ""),
            os.environ.get("RENAV_ACCEPTANCE_ARTIFACT_KEY", ""),
        )
    except ValueError as error:
        raise SystemExit(str(error)) from error

