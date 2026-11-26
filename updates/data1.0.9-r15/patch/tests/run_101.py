"""Verify the signed 1.0.1 patch, its upgrade path and the released cloud catalog."""
import hashlib
import re
import subprocess
from zipfile import ZipFile

from run_111 import main
from run_r16 import ROOT
from version_manifest_check import inspect, semantic_nodes
from build_r16 import R15_CERT


PREVIOUS_SHA256 = "876b9c0de158afb645ce6b73f247d3e30a0dd0109f891b4e39c0798448c69811"
CLOUD_INDEXES = {
    "phigros-cloud-plans-v1.json": "a52d03b851c916dc5041a5e4729b7cddc0eead3877aecbb3bec56b71cff9f191",
    "phigros-swordalt-catalog-v1.json": "fa08bc6013aa1be5f1aed467a07880e7d0f7508d3cf936fe4fb380b967b99977",
}


def check_identity(apk, name, code):
    with ZipFile(apk) as archive:
        manifest = archive.read("AndroidManifest.xml")
        versions = inspect(manifest)[1]
        assert (versions[0x0101021c][0], versions[0x0101021b][0]) == (name, code), apk
        package = next(attr[4] for node, attrs in semantic_nodes(manifest) if node == "manifest"
                       for attr in attrs if attr[1] == "package")
        assert package == "io.github.phiscript", package
        for index, expected in CLOUD_INDEXES.items():
            actual = hashlib.sha256(archive.read("assets/" + index)).hexdigest()
            assert actual == expected, f"Unexpected 1.0.0 cloud data in {apk.name}: {index}"
    signer = ROOT / "tools/build-tools/android-15/lib/apksigner.jar"
    report = subprocess.check_output(["java", "-jar", str(signer), "verify", "--print-certs", str(apk)],
                                     text=True, encoding="utf-8")
    assert re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})", report) == [R15_CERT], report


def patch_checks(apk, previous):
    check_identity(apk, "1.0.1", 11009)
    with ZipFile(apk) as archive:
        app = archive.read("classes12.dex")
        for share in (b"https://share.feijipan.com/s/0hfX6hQd", b"https://share.feijipan.com/s/sqfXebbe"):
            assert share in app, f"Missing published Feijipan share in signed APK: {share!r}"
    if previous is not None:
        with previous.open("rb") as stream:
            assert hashlib.file_digest(stream, "sha256").hexdigest() == PREVIOUS_SHA256, "Expected the official 1.0.0 APK"
        check_identity(previous, "1.0.0", 11008)
    print("PASS Android package, signer, version code, Feijipan links and published 1.0.0 cloud indexes.")


if __name__ == "__main__":
    main("1.0.1", 11009, patch_checks)
