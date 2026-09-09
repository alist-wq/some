#!/usr/bin/env python3
"""Set BRouter's volatile per-file creation timestamp to a declared epoch."""

from __future__ import annotations

import argparse
import struct
from pathlib import Path

# WayLinker writes the timestamp immediately before a 4-byte index checksum,
# twenty-five 4-byte header checksums, and a 1-byte elevation marker.
TRAILER_BYTES_AFTER_TIMESTAMP = 4 + (25 * 4) + 1


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("rd5", type=Path)
    parser.add_argument("--epoch", required=True, type=int)
    args = parser.parse_args()

    if args.epoch < 0:
        raise SystemExit("epoch must be non-negative")
    with args.rd5.open("r+b") as stream:
        stream.seek(0, 2)
        if stream.tell() < TRAILER_BYTES_AFTER_TIMESTAMP + 8:
            raise SystemExit("not a complete rd5 file")
        stream.seek(-(TRAILER_BYTES_AFTER_TIMESTAMP + 8), 2)
        stream.write(struct.pack(">q", args.epoch * 1000))


if __name__ == "__main__":
    main()
