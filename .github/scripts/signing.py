"""Keep the CI debug signer stable; refuse a new key once v0.1.3 is published."""
import hashlib
import json
import os
import re
import subprocess
import sys
from pathlib import Path

SIGNER_PATTERN = r"(?im)^(?:V[\d.]+ )?Signer[^\n]*?certificate SHA-256 digest:\s*([0-9a-f]{64})\s*$"
MIGRATION_VERSION = (0, 1, 3)

def command(args):
    return subprocess.run(args, check=True, capture_output=True,
                          text=True, timeout=120).stdout

def apk_certificates(path):
    signer = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0/apksigner"
    output = command([str(signer), "verify", "--print-certs", str(path)])
    values = {value.lower() for value in re.findall(SIGNER_PATTERN, output)}
    if not values:
        raise SystemExit("Cannot read APK signing certificate")
    return values

def prepare():
    pages = json.loads(Path("published-releases.json").read_text())
    anchors = []
    for release in [item for page in pages for item in page]:
        match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", release["tag_name"])
        if release["draft"] or match is None:
            continue
        version = tuple(map(int, match.groups()))
        if version >= MIGRATION_VERSION:
            anchors.append((version, release))
    anchor = max(anchors, key=lambda item: item[0])[1] if anchors else None
    keystore = Path(os.environ["PHISCRIPT_DEBUG_KEYSTORE"])
    state = Path(os.environ["SIGNING_STATE_DIR"])
    state.mkdir(parents=True, exist_ok=True)
    if not keystore.is_file():
        if anchor is not None:
            raise SystemExit("Signing key cache is missing. Refusing to generate a replacement.")
        source = Path("app/build.gradle.kts").read_text()
        if not re.search(r'versionName\s*=\s*"0\.1\.3"', source):
            raise SystemExit("First signing-key initialization is allowed only for v0.1.3.")
        keystore.parent.mkdir(parents=True, exist_ok=True)
        command([
            "keytool", "-genkeypair", "-noprompt",
            "-keystore", str(keystore), "-storetype", "JKS",
            "-storepass", "android", "-keypass", "android",
            "-alias", "androiddebugkey", "-keyalg", "RSA",
            "-keysize", "2048", "-validity", "10000",
            "-dname", "CN=Android Debug,O=Phigros Script,C=US"
        ])
        print("Initialized the v0.1.3 debug signing identity.")
    keystore.chmod(0o600)
    certificate = subprocess.run([
        "keytool", "-exportcert", "-keystore", str(keystore),
        "-storepass", "android", "-alias", "androiddebugkey"
    ], check=True, capture_output=True, timeout=30).stdout
    expected = hashlib.sha256(certificate).hexdigest()
    if anchor is not None:
        tag = anchor["tag_name"]
        name = "Phigros-Script-" + tag + ".apk"
        matches = [a for a in anchor["assets"] if a["name"] == name and a["state"] == "uploaded"]
        if len(matches) != 1:
            raise SystemExit("Latest signing-anchor release has no unique uploaded APK.")
        previous = state / "previous"
        previous.mkdir()
        command(["gh", "release", "download", tag, "--repo", os.environ["GITHUB_REPOSITORY"],
                 "--pattern", name, "--dir", str(previous)])
        if apk_certificates(previous / name) != {expected}:
            raise SystemExit("Cached key differs from released APK. Refusing to build.")
        print("Signing identity matches " + tag)
    else:
        print("No v0.1.3-or-later signing anchor exists; first migration build.")
    (state / "certificate.sha256").write_text(expected + "\n")
    print("Debug signing certificate SHA-256: " + expected)

def verify():
    expected = (Path(os.environ["SIGNING_STATE_DIR"]) / "certificate.sha256").read_text().strip()
    if apk_certificates("app/build/outputs/apk/debug/app-debug.apk") != {expected}:
        raise SystemExit("Built APK has an unexpected signer.")
    print("Verified APK signer SHA-256: " + expected)

if __name__ == "__main__":
    if sys.argv[1:] == ["prepare"]:
        prepare()
    elif sys.argv[1:] == ["verify"]:
        verify()
    else:
        raise SystemExit("Usage: signing.py prepare|verify")
