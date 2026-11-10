"""Rebuild the already-signed release bytes from the pinned official base."""
import hashlib
import io
import json
import struct
import sys
import zipfile
import zlib
from pathlib import Path


def digest(data):
    return hashlib.sha256(data).hexdigest()


def restore(base_path, parts_dir, output):
    root = Path(parts_dir)
    out = Path(output)
    out.mkdir(parents=True, exist_ok=True)
    manifest = json.loads((root / "manifest.json").read_text())
    base = Path(base_path).read_bytes()
    if digest(base) != manifest["base_sha256"]:
        raise ValueError("Base APK checksum mismatch")
    packs = {}
    for name, parts in manifest["parts"].items():
        chunks = []
        for part in parts:
            data = (root / part["name"]).read_bytes()
            if digest(data) != part["sha256"]:
                raise ValueError("Release part checksum mismatch")
            chunks.append(data)
        packs[name] = b"".join(chunks)
    with zipfile.ZipFile(io.BytesIO(packs["delta"])) as patch, zipfile.ZipFile(io.BytesIO(base)) as apk:
        ops = json.loads(patch.read("recipe.json"))
        literal = patch.read("literal.bin")
        result = bytearray()
        for kind, *args in ops:
            if kind in ("base", "literal"):
                source = base if kind == "base" else literal
                start, length = args
                if start < 0 or length < 0 or start+length > len(source):
                    raise ValueError("Invalid byte range")
                result.extend(source[start:start+length])
            elif kind == "deflate":
                entry, edits, expected = args
                raw = bytearray(apk.read(entry))
                changes = patch.read(edits)
                for pos, value in struct.iter_unpack("<IB", changes):
                    raw[pos] = value
                compressor = zlib.compressobj(6, zlib.DEFLATED, -15)
                compressed = compressor.compress(raw) + compressor.flush()
                if digest(compressed) != expected:
                    raise ValueError("DEX compression result differs: "+entry)
                result.extend(compressed)
            else:
                raise ValueError("Unknown reconstruction operation")
    outputs = {"PhigrosScript_data1.0.9-r12.apk":result, "GestureRecorder_1.0.0.apk":packs["recorder"],
               "PhigrosScript_patch.zip":packs["source"]}
    outputs["SHA256SUMS.txt"] = "".join(digest(data)+"  "+name+"\n" for name,data in outputs.items()).encode()
    for name, data in outputs.items():
        if manifest["assets"][name] != {"sha256":digest(data), "size":len(data)}:
            raise ValueError("Restored asset checksum mismatch: "+name)
        (out/name).write_bytes(data)
    print("Verified all four release files.")


if __name__ == "__main__":
    restore(*sys.argv[1:])
