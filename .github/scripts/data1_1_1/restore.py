"""Restore the data1.1.1 signing output from the pinned public R15 APK."""
import hashlib
import io
import json
import re
import sys
from pathlib import Path
from zipfile import ZipFile


TAG = "data1.1.1"
APK = "PhigrosScript_data1.1.1.apk"
SOURCE = "PhigrosScript_data1.1.1_Source.zip"
BASE_SHA = "d7d38b37f3ba821261c5c9917366c5600d7432c5836a1b8fb4e2b27bba58c5b8"
LIMIT = 128 * 1024 * 1024
PART_SIZE = 192 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def metadata(data):
    return {"size": len(data), "sha256": hashlib.sha256(data).hexdigest()}


def manifest_at(path, tag=TAG, code=11001):
    manifest = json.loads(path.read_text(encoding="utf-8"))
    require(manifest["version"] == tag and manifest["version_code"] == code, "Incorrect version")
    require(manifest["base_sha256"] == BASE_SHA, "Incorrect base")
    require(re.fullmatch(r"[0-9a-f]{40}", manifest["patch_tree"]), "Incorrect patch tree")
    require(set(manifest["assets"]) == {f"PhigrosScript_{tag}.apk", f"PhigrosScript_{tag}_Source.zip"}, "Unexpected asset list")
    for item in manifest["assets"].values():
        require(type(item["size"]) is int and 0 < item["size"] <= LIMIT, "Invalid asset size")
        require(re.fullmatch(r"[0-9a-f]{64}", item["sha256"]), "Invalid asset SHA-256")
    return manifest


def restore(base, data, destination, tag=TAG, code=11001):
    manifest = manifest_at(data / "manifest.json", tag, code)
    apk_name, source_name = f"PhigrosScript_{tag}.apk", f"PhigrosScript_{tag}_Source.zip"
    baseline = base.read_bytes()
    require(metadata(baseline)["sha256"] == BASE_SHA, "R15 baseline SHA-256 mismatch")
    pieces = manifest["parts"]
    require(0 < len(pieces) <= (LIMIT + PART_SIZE - 1) // PART_SIZE, "Invalid part count")
    payload = bytearray()
    for index, expected in enumerate(pieces):
        part = data / f"transport.part{index:03d}"
        require(not part.is_symlink() and 0 < part.stat().st_size <= PART_SIZE, "Invalid part")
        block = part.read_bytes()
        require(metadata(block) == expected, f"Invalid transport part {index}")
        payload.extend(block)
    with ZipFile(io.BytesIO(payload)) as bundle:
        members = bundle.infolist()
        require(len(members) == 3 and set(bundle.namelist()) == {"ops.json", "literal.bin", source_name},
                "Unexpected transport contents")
        require(sum(item.file_size for item in members) <= LIMIT, "Transport exceeds limit")
        ops = json.loads(bundle.read("ops.json"))
        literal = bundle.read("literal.bin")
        source = bundle.read(source_name)
    expected_size = manifest["assets"][apk_name]["size"]
    require(isinstance(ops, list) and 0 < len(ops) <= 100000, "Invalid patch recipe")
    target = bytearray()
    for kind, offset, size in ops:
        require(kind in ("b", "l") and type(offset) is int and type(size) is int and offset >= 0 and size > 0,
                "Invalid patch slice")
        segment = baseline if kind == "b" else literal
        require(offset + size <= len(segment) and len(target) + size <= expected_size, "Slice outside APK")
        target.extend(segment[offset:offset + size])
    for name, contents in ((apk_name, target), (source_name, source)):
        require(metadata(contents) == manifest["assets"][name], f"Restored {name} SHA-256 mismatch")
    destination.mkdir(parents=True, exist_ok=True)
    for name, contents in ((apk_name, target), (source_name, source)):
        (destination / name).write_bytes(contents)
    print("Verified both release assets against the signed build manifest.")


if __name__ == "__main__":
    restore(*(Path(arg) for arg in sys.argv[1:]))
