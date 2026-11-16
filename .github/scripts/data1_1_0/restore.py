"""Restore the exact preverified data1.1.0 assets without building or signing."""

import hashlib
import io
import json
import re
import stat
import sys
import zipfile
from pathlib import Path


VERSION = "data1.1.0"
SOURCE_COMMIT = "94039e20249b22f41696931a81ffcd6d7dc21276"
PATCH_TREE = "22953eff5bb35388db84e7b8572bb91f4d0421b4"
BASE_SHA256 = "d7d38b37f3ba821261c5c9917366c5600d7432c5836a1b8fb4e2b27bba58c5b8"
APK_NAME = "PhigrosScript_data1.1.0.apk"
ASSET_NAMES = frozenset((
    APK_NAME,
    "PhigrosScript_data1.1.0_Source.zip",
    "使用说明_data1.1.0.md",
    "验证记录_data1.1.0.md",
    "回放对照_data1.1.0.md",
    "回放对照_data1.1.0.json",
    "PhigrosScript_r15_to_data1.1.0.patch",
    "PhigrosScript_data1.1.0.hooks.json",
    "PhigrosScript_Icon_data1.1.0.png",
    "PhigrosScript_Icon_data1.1.0.svg",
    "RELEASE_NOTES_data1.1.0.md",
    "SHA256SUMS_data1.1.0.txt",
))
MAX_BYTES = 128 * 1024 * 1024


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def valid_digest(value):
    return isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) is not None


def validate_item(item, label):
    require(isinstance(item, dict), label + ": invalid metadata")
    require(type(item.get("size")) is int and 0 < item["size"] <= MAX_BYTES,
            label + ": invalid size")
    require(valid_digest(item.get("sha256")), label + ": invalid digest")


def load_manifest(path):
    manifest = json.loads(Path(path).read_text(encoding="utf-8"))
    require(isinstance(manifest, dict), "Invalid manifest")
    require(manifest.get("version") == VERSION and manifest.get("versionCode") == 11000,
            "Wrong release version")
    require(manifest.get("verified_source_commit") == SOURCE_COMMIT,
            "Wrong verified source commit")
    require(manifest.get("verified_patch_tree") == PATCH_TREE, "Wrong verified patch tree")
    require(manifest.get("base_sha256") == BASE_SHA256, "Wrong base APK digest")
    assets = manifest.get("assets")
    require(isinstance(assets, dict) and set(assets) == ASSET_NAMES,
            "Unexpected release asset names")
    for name, item in assets.items():
        validate_item(item, name)
    require(sum(item["size"] for item in assets.values()) <= MAX_BYTES,
            "Release assets exceed size limit")
    transport = manifest.get("transport")
    validate_item(transport, "transport")
    parts = transport.get("parts")
    require(isinstance(parts, list) and 0 < len(parts) <= 1024, "Invalid transport parts")
    seen = set()
    for index, item in enumerate(parts):
        validate_item(item, "transport part")
        name = item.get("name")
        require(isinstance(name, str) and re.fullmatch(r"transport\.part[0-9]{3,}", name),
                "Invalid transport part name")
        require(name == "transport.part" + str(index).zfill(3), "Transport parts out of order")
        require(name not in seen, "Duplicate transport part")
        seen.add(name)
    require(sum(item["size"] for item in parts) == transport["size"],
            "Transport part sizes do not match")
    return manifest


def check_bytes(data, item, label):
    require(len(data) == item["size"] and sha256(data) == item["sha256"],
            label + ": size or SHA256 mismatch")


def read_archive(data, expected_names):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        entries = archive.infolist()
        require(len(entries) == len(expected_names) and
                {entry.filename for entry in entries} == set(expected_names),
                "Unexpected or duplicate ZIP entries")
        require(sum(entry.file_size for entry in entries) <= MAX_BYTES,
                "ZIP exceeds expanded size limit")
        for entry in entries:
            require(not entry.is_dir() and not (entry.flag_bits & 1) and
                    not stat.S_ISLNK(entry.external_attr >> 16), "Invalid ZIP entry")
            require(0 <= entry.file_size <= MAX_BYTES, "Invalid ZIP entry size")
        return {entry.filename: archive.read(entry) for entry in entries}


def restore(base_path, parts_dir, out_dir):
    parts_dir = Path(parts_dir)
    manifest = load_manifest(parts_dir / "manifest.json")
    base_path = Path(base_path)
    require(base_path.is_file() and not base_path.is_symlink() and
            base_path.stat().st_size <= MAX_BYTES, "Invalid base APK file")
    base = base_path.read_bytes()
    require(sha256(base) == BASE_SHA256, "Base APK checksum mismatch")
    blocks = []
    for item in manifest["transport"]["parts"]:
        path = parts_dir / item["name"]
        require(path.is_file() and not path.is_symlink() and path.stat().st_size == item["size"],
                "Transport part file mismatch")
        block = path.read_bytes()
        check_bytes(block, item, item["name"])
        blocks.append(block)
    transport = b"".join(blocks)
    check_bytes(transport, manifest["transport"], "transport")
    files = read_archive(transport, (ASSET_NAMES - {APK_NAME}) | {"apk-patch.zip"})
    patch = read_archive(files.pop("apk-patch.zip"), {"recipe.json", "literal.bin"})
    require(len(patch["recipe.json"]) <= 16 * 1024 * 1024, "Patch recipe exceeds size limit")
    recipe = json.loads(patch["recipe.json"])
    require(isinstance(recipe, dict), "Invalid patch recipe")
    target_item = manifest["assets"][APK_NAME]
    require(recipe.get("base_sha256") == BASE_SHA256 and
            recipe.get("target_sha256") == target_item["sha256"] and
            type(recipe.get("target_size")) is int and
            recipe["target_size"] == target_item["size"], "Patch recipe identity mismatch")
    ops = recipe.get("ops")
    require(isinstance(ops, list) and 0 < len(ops) <= 1000000, "Invalid patch operations")
    target = bytearray()
    for op in ops:
        require(isinstance(op, list) and len(op) == 3, "Invalid patch operation")
        kind, offset, length = op
        require(kind in ("b", "l") and type(offset) is int and type(length) is int and
                offset >= 0 and length >= 0, "Invalid patch operation bounds")
        source = base if kind == "b" else patch["literal.bin"]
        require(offset + length <= len(source) and
                len(target) + length <= target_item["size"], "Patch operation outside bounds")
        target.extend(source[offset:offset + length])
    files[APK_NAME] = bytes(target)
    for name, item in manifest["assets"].items():
        check_bytes(files[name], item, name)
    root = Path(out_dir)
    require(not root.is_symlink(), "Output directory must not be a symlink")
    require(not root.exists() or (root.is_dir() and not any(root.iterdir())),
            "Output directory must be empty")
    root.mkdir(parents=True, exist_ok=True)
    for name in sorted(ASSET_NAMES):
        (root / name).write_bytes(files[name])
    print("Restored all 12 exact data1.1.0 assets; sizes and SHA256 values match.")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: restore.py BASE_APK PARTS_DIR OUTPUT_DIR")
    restore(*sys.argv[1:])
