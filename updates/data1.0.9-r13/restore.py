"""Restore the signed release from the pinned data1.0.9 APK and checked parts."""
from hashlib import sha256
from pathlib import Path
import io
import json
import struct
import sys
import zipfile
import zlib

MAIN = "PhigrosScript_data1.0.9-r13.apk"
GESTURE = "GestureCapture_1.0.apk"
SOURCE = "PhigrosScript_patch.zip"


def checked(data, spec):
    if len(data) != spec["size"] or sha256(data).hexdigest() != spec["sha256"]:
        raise ValueError("Size or SHA-256 mismatch")
    return data


def group(root, description):
    blocks = []
    for part in description["parts"]:
        if Path(part["name"]).name != part["name"]:
            raise ValueError("Invalid part name")
        blocks.append(checked((root / part["name"]).read_bytes(), part))
    return checked(b"".join(blocks), description)


def restore(base_path, parts_path, out_path):
    parts = Path(parts_path)
    output = Path(out_path)
    manifest = json.loads((parts / "manifest.json").read_text())
    base = checked(Path(base_path).read_bytes(), manifest["base"])
    data = {name: group(parts, desc) for name, desc in manifest["parts"].items()}
    result = bytearray()
    with zipfile.ZipFile(io.BytesIO(data["delta"])) as archive, zipfile.ZipFile(io.BytesIO(base)) as original:
        recipe = json.loads(archive.read("recipe.json"))
        literal = archive.read("literal.bin")
        for op in recipe:
            action = op[0]
            if action == "copy" or action == "literal":
                _, start, length = op
                buf = base if action == "copy" else literal
                if start < 0 or length < 0 or start + length > len(buf):
                    raise ValueError("Invalid copy bounds")
                result.extend(buf[start:start+length])
            elif action == "deflate":
                _, entry, patch_file = op
                block = bytearray(original.read(entry))
                for offset, value in struct.iter_unpack("<IB", archive.read(patch_file)):
                    block[offset] = value
                enc = zlib.compressobj(6, zlib.DEFLATED, -15)
                result.extend(enc.compress(block) + enc.flush())
            else:
                raise ValueError("Unknown delta operation")
    assets = {MAIN: result, GESTURE: data["gesture"], SOURCE: data["source"]}
    sums = "".join(sha256(content).hexdigest() + "  " + name + "\n" for name, content in assets.items())
    assets["SHA256SUMS.txt"] = sums.encode("ascii")
    if set(assets) != set(manifest["assets"]):
        raise ValueError("Unexpected asset list")
    for name, content in assets.items():
        checked(content, manifest["assets"][name])
    output.mkdir(parents=True, exist_ok=True)
    for name, content in assets.items():
        (output / name).write_bytes(content)
    print("Verified and restored both APKs, source ZIP and SHA256SUMS.")


if __name__ == "__main__":
    restore(*sys.argv[1:])
