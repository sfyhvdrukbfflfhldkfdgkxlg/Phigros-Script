"""Restore the signed R15 APK from the R13 release and verify its SHA-256."""
from hashlib import sha256
from io import BytesIO
from pathlib import Path
from zipfile import ZipFile
import json
import sys


def restore(base_path, output_path):
    parts = Path(__file__).with_name("release-data")
    manifest = json.loads((parts / "manifest.json").read_text())
    base = Path(base_path).read_bytes()
    if sha256(base).hexdigest() != manifest["base_sha256"]:
        raise ValueError("Expected the signed data1.0.9-r13 release APK")
    delta = bytearray()
    for name, digest in manifest["parts"].items():
        data = (parts / name).read_bytes()
        if sha256(data).hexdigest() != digest:
            raise ValueError(f"Invalid delta part {name}")
        delta.extend(data)
    result = bytearray()
    with ZipFile(BytesIO(delta)) as archive:
        literal = archive.read("literal.bin")
        for source, offset, length in json.loads(archive.read("recipe.json")):
            buf = {"base": base, "literal": literal}[source]
            if offset < 0 or length < 0 or offset + length > len(buf):
                raise ValueError("Invalid delta range")
            result.extend(buf[offset:offset + length])
    if len(result) != manifest["size"] or sha256(result).hexdigest() != manifest["apk_sha256"]:
        raise ValueError("Restored APK digest differs from the tested build")
    path = Path(output_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(result)
    print(f"{manifest['apk_sha256']}  {path.name}")


if __name__ == "__main__":
    restore(*sys.argv[1:])
