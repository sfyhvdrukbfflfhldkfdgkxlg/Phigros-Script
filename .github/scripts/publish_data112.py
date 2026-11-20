"""Restore, verify, and publish the precomputed chart library with its signed APK."""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.parse import quote
from urllib.request import Request, urlopen
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).parent / "data1_1_1"))
from publish import REPO, api
from restore import manifest_at, require, restore

TAG = "data1.1.2"
CODE = 11002
APK = f"PhigrosScript_{TAG}.apk"
INDEX = "phigros-cloud-plans-v1.json"
DATA = Path("updates/data1.1.2/release-data")
PATCH = Path("updates/data1.0.9-r15/patch")


def digest(path):
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def cloud_index(directory):
    with ZipFile(directory / APK) as package:
        raw = package.read("assets/" + INDEX)
        tracked = (PATCH / "assets" / INDEX).read_bytes()
        require(raw.replace(b"\r\n", b"\n") == tracked.replace(b"\r\n", b"\n"), "APK and tracked index differ")
        catalog = json.loads(package.read("assets/phigros-swordalt-catalog-v1.json"))
    cloud = json.loads(raw)
    require(cloud["version"] == 1 and cloud["chartCommit"] == catalog["chartCommit"], "Chart version mismatch")
    require(set(cloud["entries"]) == {c["sha1"] for c in catalog["entries"]}, "Incomplete chart index")
    base = f"https://github.com/{REPO}/releases/download/{TAG}/"
    for i, row in enumerate(cloud["shards"]):
        name = f"phigros-plans-v1-{i:03d}.bin"
        require(row["github"] == base + name and re.fullmatch(r"[0-9a-f]{64}", row["sha256"]), "Invalid shard index")
        require(0 < row["bytes"] <= 32 * 1024 * 1024, "Invalid shard size")
    return cloud


def get_shard(out, i, row):
    for attempt in range(3):
        try:
            download_shard(out, i, row)
            return
        except OSError:
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)


def download_shard(out, i, row):
    target = out / f"phigros-plans-v1-{i:03d}.bin"
    if target.is_file() and target.stat().st_size == row["bytes"] and digest(target) == row["sha256"]:
        return
    file_id = row["drive"]
    require(re.fullmatch(r"[A-Za-z0-9_-]{10,128}", file_id), "No Drive ID for " + target.name)
    url = "https://drive.usercontent.google.com/download?export=download&confirm=t&id=" + file_id
    part = target.with_suffix(".part")
    try:
        with urlopen(Request(url, headers={"User-Agent": "Phigros-Script release"}), timeout=60) as response, part.open("wb") as stream:
            remaining = row["bytes"]
            while remaining:
                block = response.read(min(1024 * 1024, remaining))
                require(block, "Short Drive file: " + target.name)
                stream.write(block)
                remaining -= len(block)
            require(not response.read(1), "Oversized Drive file: " + target.name)
        require(digest(part) == row["sha256"], "Drive shard digest mismatch: " + target.name)
        part.replace(target)
    finally:
        part.unlink(missing_ok=True)


def mirror(out, work):
    cloud = cloud_index(out)
    try:
        get_shard(out, 0, cloud["shards"][0])
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            jobs = [pool.submit(get_shard, out, i, row) for i, row in enumerate(cloud["shards"][1:], 1)]
            for job in jobs:
                job.result()
        print("Verified all shards downloaded from Google Drive.")
    except (OSError, ValueError) as error:
        print(f"Drive files are not directly downloadable ({error}). Rebuilding the signed chart index.", flush=True)
        raw = "Phigros_Charts_4.0.1.2_20261120.zip"
        subprocess.run(["gh", "release", "download", "charts-4.0.1.2-20261120", "-R", REPO, "-p", raw, "-D", str(work)], check=True)
        subprocess.run([sys.executable, "updates/data1.1.2/ci_build_plans.py", work / raw, out / APK, work / "compiler", out], check=True)
    with ZipFile(out / APK) as apk:
        (out / INDEX).write_bytes(apk.read("assets/" + INDEX))


def assets(out):
    manifest = manifest_at(DATA / "manifest.json", TAG, CODE)
    cloud = cloud_index(out)
    expected = dict(manifest["assets"])
    for i, row in enumerate(cloud["shards"]):
        expected[f"phigros-plans-v1-{i:03d}.bin"] = {"size": row["bytes"], "sha256": row["sha256"]}
    with ZipFile(out / APK) as apk:
        raw = apk.read("assets/" + INDEX)
    expected[INDEX] = {"size": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}
    sums = out / "SHA256SUMS.txt"
    sums.write_bytes("".join(f"{expected[n]['sha256']}  {n}\n" for n in sorted(expected)).encode("ascii"))
    expected[sums.name] = {"size": sums.stat().st_size, "sha256": digest(sums)}
    for name, file in expected.items():
        require((out / name).stat().st_size == file["size"] and digest(out / name) == file["sha256"],
                "Release asset differs from signed inputs: " + name)
    return expected


def check_remote(release, expected):
    found = set()
    page = 1
    files = []
    while True:
        batch = api("GET", f"releases/{release['id']}/assets?per_page=100&page={page}")
        files.extend(batch)
        if len(batch) < 100:
            break
        page += 1
    for file in files:
        name = file["name"]
        require(name in expected and name not in found, "Unexpected remote asset: " + name)
        if release["draft"] and file["state"] == "starter" and file["size"] == 0:
            api("DELETE", f"releases/assets/{file['id']}")
            continue
        require(file["state"] == "uploaded" and file["size"] == expected[name]["size"] and
                file["digest"] == "sha256:" + expected[name]["sha256"], "Remote asset mismatch: " + name)
        found.add(name)
    return found


def publish(out):
    require(os.environ.get("GITHUB_REPOSITORY") == REPO and os.environ.get("GITHUB_REF") == "refs/heads/main", "Unexpected job identity")
    commit = os.environ["GITHUB_SHA"]
    manifest = manifest_at(DATA / "manifest.json", TAG, CODE)
    tree = subprocess.check_output(["git", "rev-parse", f"{commit}:{PATCH.as_posix()}"], text=True).strip()
    require(tree == manifest["patch_tree"], "Source differs from signed release inputs")
    expected = assets(out)
    tag = api("GET", "git/ref/tags/" + TAG, optional=True)
    require(not tag or tag["object"]["sha"] == commit, "Tag points to different source")
    release = api("GET", "releases/tags/" + TAG, optional=True)
    if release is None:
        for row in api("GET", "releases?per_page=100"):
            if row["tag_name"] == TAG:
                release = row
                break
    if release is None:
        notes = ("识别 Phigros 曲目和难度后自动下载已处理的时间轴与触点，支持本机缓存。"
                 "含噪域的谱面使用手势，两项小飞机云盘导入入口已隐藏。\n\n"
                 f"安装请下载 `{APK}`。应用会自行选择谱面分片。"
                 f"[使用说明](https://github.com/{REPO}/blob/{TAG}/updates/data1.1.2/README.md)")
        release = api("POST", "releases", body={"tag_name": TAG, "target_commitish": commit, "name": "Phigros Script " + TAG,
                                                  "draft": True, "body": notes})
    require(release["target_commitish"] == commit and release["tag_name"] == TAG and not release["prerelease"], "Unexpected release identity")
    resource = f"releases/{release['id']}"
    found = check_remote(release, expected)
    if release["draft"]:
        for name in sorted(expected.keys() - found, key=lambda name: (name != APK, name)):
            api("POST", resource + "/assets?name=" + quote(name), upload=(out / name).read_bytes())
        require(check_remote(api("GET", resource), expected) == set(expected), "Draft is incomplete")
        api("PATCH", resource, body={"draft": False, "make_latest": "true"})
    public = api("GET", "releases/tags/" + TAG)
    require(not public["draft"] and not public["prerelease"] and check_remote(public, expected) == set(expected), "Public assets differ")
    require(api("GET", "git/ref/tags/" + TAG)["object"]["sha"] == commit, "Public tag differs")
    require(api("GET", "releases/latest")["id"] == public["id"], "Latest APK release differs")
    print(public["html_url"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("restore", "mirror", "publish"))
    parser.add_argument("dist", type=Path)
    parser.add_argument("input", type=Path, nargs="?")
    args = parser.parse_args()
    if args.action == "restore":
        restore(args.input, DATA, args.dist, TAG, CODE)
    elif args.action == "mirror":
        args.input.mkdir(parents=True, exist_ok=True)
        mirror(args.dist, args.input)
    else:
        publish(args.dist)
