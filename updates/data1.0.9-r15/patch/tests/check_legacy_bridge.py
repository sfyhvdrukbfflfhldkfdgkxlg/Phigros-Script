"""Exercise each released APK's update selector on the first anonymous GitHub releases page."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile

from run_r16 import ROOT, compile_to, cp, run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("feed", type=Path, help="JSON from the public releases?per_page=30 endpoint")
    parser.add_argument("apks", type=Path, nargs="+", help="Original official client APKs (old and numbered)")
    parser.add_argument("--target", default="1.0.0")
    parser.add_argument("--bridge", default="data1.1.4-to-1.0.0")
    parser.add_argument("--published", action="store_true", help="Require the real published bridge in the feed")
    args = parser.parse_args()
    feed = args.feed.resolve()
    releases = {row["tag_name"]: row for row in json.loads(feed.read_bytes())}
    aapt = ROOT / "tools/build-tools/android-15" / ("aapt2.exe" if os.name == "nt" else "aapt2")
    with tempfile.TemporaryDirectory(prefix="update-bridge-", dir=ROOT, ignore_cleanup_errors=True) as directory:
        work = Path(directory)
        classes = work / "tests"
        for filepath in args.apks:
            apk = filepath.resolve()
            metadata = subprocess.check_output([aapt, "dump", "badging", apk]).decode("utf-8", "replace")
            match = re.search(r"^package: name='io\.github\.phiscript'[^\n]* versionName='([^']+)'", metadata, re.M)
            if not match:
                raise ValueError(f"No official app version in {apk}")
            version = match.group(1)
            asset = next(row for row in releases[version]["assets"] if row["name"] == f"PhigrosScript_{version}.apk")
            with apk.open("rb") as stream:
                if "sha256:" + hashlib.file_digest(stream, "sha256").hexdigest() != asset["digest"]:
                    raise ValueError(f"{apk} does not match its published GitHub digest")
            jar = work / "client.jar"
            run("java", "-Xmx1100m", "-cp", "tools/dex2jar/dex-tools-v2.4/lib/*",
                "com.googlecode.dex2jar.tools.Dex2jarCmd", "-f", "-o", jar, apk)
            classpath = cp(classes, jar, ROOT / "tools/json.jar")
            if not classes.exists():
                compile_to(classes, classpath, "patch/tests/update/LegacyBridgeRegression.java")
            run("java", "-cp", classpath, "io.github.phiscript.update.LegacyBridgeRegression",
                feed, version, args.bridge, args.target, "--published" if args.published else "--simulate")


if __name__ == "__main__":
    main()
