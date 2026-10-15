#!/usr/bin/env bash
set -euo pipefail

input_dir="$1"
encrypted_output="$2"
test -n "${RENAV_ACCEPTANCE_ARTIFACT_KEY:-}"

archive="$(mktemp)"
trap 'rm -f "$archive"' EXIT
tar -cf "$archive" -C "$input_dir" \
  destination-index.jsonl manifest.json manifest.json.sig renav-destination-index-ed25519.pub
openssl enc -aes-256-cbc -salt -pbkdf2 -iter 600000 \
  -in "$archive" -out "$encrypted_output" -pass env:RENAV_ACCEPTANCE_ARTIFACT_KEY
test "$(head -c 8 "$encrypted_output")" = "Salted__"
