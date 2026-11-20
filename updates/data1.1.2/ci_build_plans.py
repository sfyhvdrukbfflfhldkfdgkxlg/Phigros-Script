"""Rebuild the cloud chart shards in the signed data1.1.2 APK on a Windows x64 runner."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
import sys
import tempfile
import time
from urllib.error import URLError
from urllib.request import Request, urlopen
import zipfile

PATCH = Path(__file__).resolve().parents[1] / "data1.0.9-r15/patch"
MAVEN = "https://repo.maven.apache.org/maven2/"
JARS = (
    ("ecj.jar", "org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar",
     "01f5a92ac19bb2b3bf85e295a68f2c73c264369109158b566ce9b490af982948"),
    ("json.jar", "org/json/json/20240303/json-20240303.jar",
     "3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed"),
    ("kotlin.jar", "org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar",
     "f31cc53f105a7e48c093683bbd5437561d1233920513774b470805641bedbc09"),
)
DEX = ("https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip",
       "ee7c45eb3c1d2474a6145d8d447e651a736a22d9664b6d3d3be5a5a817dda23a")
SDK = ("https://dl.google.com/android/repository/platform-35_r02.zip",
       "0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0")
JDK = ("https://download.oracle.com/java/25/archive/jdk-25.0.4.1_windows-x64_bin.zip",
       "0e9726dc51724f262a32c796705abfe49e41574c0b134827bc72d6cef7a36ff4")
SOURCE_SHA = "d6c7a8e31744bd9e7fad1b41d638a6d115dcf6f50e4e9f386904d7088c823dab"
INDEX = "phigros-cloud-plans-v1.json"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def fetch(dest, url, sha):
    if dest.is_file() and digest(dest) == sha:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    for attempt in range(3):
        try:
            with tempfile.NamedTemporaryFile(dir=dest.parent, delete=False) as stream:
                partial = Path(stream.name)
                try:
                    request = Request(url, headers={"User-Agent": "Phigros-Script chart publisher"})
                    with urlopen(request, timeout=120) as source:
                        shutil.copyfileobj(source, stream)
                finally:
                    stream.close()
            if digest(partial) != sha:
                raise ValueError(f"Downloaded dependency has unexpected SHA-256: {url}")
            os.replace(partial, dest)
            return dest
        except (TimeoutError, URLError, ConnectionError):
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)
        finally:
            partial.unlink(missing_ok=True)


def compiler_environment(work):
    if os.name != "nt" or platform.machine().lower() not in {"amd64", "x86_64"}:
        raise RuntimeError("Run the release compiler on Windows x64 to reproduce its JVM and compression output.")
    cache, tools = work / "downloads", work / "tools"
    for name, suffix, sha in JARS:
        fetch(tools / name, MAVEN + suffix, sha)
    with zipfile.ZipFile(fetch(cache / "dex-tools.zip", *DEX)) as archive:
        for name in archive.namelist():
            if name.startswith("dex-tools-v2.4/lib/") and name.endswith(".jar"):
                archive.extract(name, tools / "dex2jar")
    with zipfile.ZipFile(fetch(cache / "platform.zip", *SDK)) as archive:
        archive.extract("android-35/android.jar", tools / "platform")
    env = os.environ.copy()
    env["PHIGROS_PLAN_TOOLS"] = str(tools)
    current = shutil.which("java")
    if current:
        info = subprocess.run([current, "-version"], capture_output=True, text=True, check=True).stderr
        if "Java(TM) SE Runtime Environment (build 25.0.4.1+1-LTS-5)" in info:
            return env
    with zipfile.ZipFile(fetch(cache / "jdk.zip", *JDK)) as archive:
        java, = [name for name in archive.namelist() if name.endswith("/bin/java.exe")]
        archive.extractall(tools / "jdk")
    java_home = (tools / "jdk" / java).parents[1]
    env["JAVA_HOME"] = str(java_home)
    env["PATH"] = str(java_home / "bin") + os.pathsep + env["PATH"]
    return env


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("original_zip", "signed_apk", "work", "output"):
        parser.add_argument(name, type=Path)
    args = parser.parse_args()
    charts, apk, work, out = (p.resolve() for p in (args.original_zip, args.signed_apk, args.work, args.output))
    if digest(charts) != SOURCE_SHA:
        raise ValueError("The input is not the published 2026-11-20 Phigros 4.0.1.2 chart ZIP.")
    with zipfile.ZipFile(apk) as package:
        expected = json.loads(package.read("assets/" + INDEX))
    drive_ids = {row["github"].rsplit("/", 1)[1]: row["drive"] for row in expected["shards"]}
    work.mkdir(parents=True, exist_ok=True)
    ids_file = work / "drive-ids.json"
    ids_file.write_text(json.dumps(drive_ids), encoding="ascii")
    env = compiler_environment(work)
    subprocess.run([sys.executable, PATCH / "tools/build_cloud_plans.py", charts,
                    "--apk", apk, "--work", work / digest(apk), "--out", out,
                    "--drive-ids", ids_file, "--github-base", expected["shards"][0]["github"].rsplit("/", 1)[0]],
                   env=env, check=True)
    if json.loads((out / INDEX).read_bytes()) != expected:
        raise ValueError("Generated chart index differs from the one in the signed APK.")
    for shard in expected["shards"]:
        path = out / shard["github"].rsplit("/", 1)[1]
        if path.stat().st_size != shard["bytes"] or digest(path) != shard["sha256"]:
            raise ValueError(f"Generated shard differs from signed index: {path.name}")
    print(f"Verified {len(expected['entries'])} chart plans in {len(expected['shards'])} shards.")


if __name__ == "__main__":
    main()
