"""Build the offline touch-plan ZIP from the published cloud index and its shards."""
import argparse
import hashlib
import json
import os
import tempfile
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile, ZipInfo

INDEX = "phigros-cloud-plans-v1.json"


def pack(library, output):
    index = library / INDEX
    manifest = json.loads(index.read_bytes())
    if manifest.get("version") != 1 or not manifest.get("shards") or not manifest.get("entries"):
        raise ValueError("Expected a published v1 cloud touch-plan index")
    members = [(index, None)]
    for number, shard in enumerate(manifest["shards"]):
        members.append((library / f"phigros-plans-v1-{number:03d}.bin", shard))

    # Dates, attributes, order and compression are part of the APK-pinned archive digest.
    with tempfile.NamedTemporaryFile(dir=output.parent, delete=False) as tmp:
        temporary = Path(tmp.name)
    try:
        with ZipFile(temporary, "w", ZIP_STORED, allowZip64=False) as archive:
            for source, expected in members:
                info = ZipInfo(source.name, date_time=(2026, 11, 22, 0, 0, 0))
                info.create_system = 3
                info.external_attr = 0o100644 << 16
                info.file_size = source.stat().st_size
                digest = hashlib.sha256()
                size = 0
                with source.open("rb") as src, archive.open(info, "w") as dst:
                    while block := src.read(1024 * 1024):
                        dst.write(block)
                        digest.update(block)
                        size += len(block)
                if expected and (size != expected["bytes"] or digest.hexdigest() != expected["sha256"]):
                    raise ValueError(f"Shard size or SHA-256 mismatch: {source.name}")
        with temporary.open("rb") as f:
            checksum = hashlib.file_digest(f, "sha256").hexdigest()
        os.replace(temporary, output)
    finally:
        temporary.unlink(missing_ok=True)
    print(f"{checksum}  {output.name} ({output.stat().st_size} bytes)")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("library", type=Path, help="Directory containing the published index and .bin shards")
    parser.add_argument("output", type=Path, help="Destination ZIP file")
    args = parser.parse_args()
    if args.output.suffix.lower() != ".zip":
        parser.error("Expected an output path ending in .zip")
    pack(args.library, args.output)
