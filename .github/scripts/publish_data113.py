"""Restore and publish a verified, signed data1.1.3 build."""
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.error import URLError
from urllib.parse import quote
from urllib.request import urlopen
from zipfile import ZipFile

sys.path.insert(0, str(Path(__file__).parent / "data1_1_1"))
from publish import REPO, api, check_assets
from restore import manifest_at, metadata, require, restore

VERSIONS = {"data1.1.3": 11005, "data1.1.3-r2": 11006, "data1.1.3-r3": 11007, "1.0.0": 11008}
TAG = sys.argv[3] if len(sys.argv) > 3 else "data1.1.3"
CODE = VERSIONS[TAG]
DATA = Path("updates") / TAG / "release-data"
PATCH = "updates/data1.0.9-r15/patch"


def find_release():
    named = api("GET", f"releases/tags/{TAG}", optional=True)
    if named is not None:
        return named
    # Drafts can be listed before GitHub creates the tag ref.
    page = 1
    while True:
        releases = api("GET", f"releases?per_page=100&page={page}")
        for release in releases:
            if release["tag_name"] == TAG:
                return release
        if len(releases) < 100:
            return None
        page += 1


def stage_r3_shard(dist):
    name = "phigros-plans-v1-032.bin"
    signed_apk = dist / f"PhigrosScript_{TAG}.apk"
    with ZipFile(signed_apk) as apk:
        index = json.loads(apk.read("assets/phigros-cloud-plans-v1.json"))
    url = f"https://github.com/{REPO}/releases/download/{TAG}/{name}"
    require(len(index["shards"]) == 33 and index["shards"][32]["github"] == url,
            "Expected the new chart shard in the signed app index")
    shard = index["shards"][32]
    raw = (DATA / name).read_bytes()
    info = metadata(raw)
    require(info == {"size": shard["bytes"], "sha256": shard["sha256"]}, "Chart shard mismatch")
    (dist / name).write_bytes(raw)
    return {name: info}


def stage_drive_mirrors(dist):
    with ZipFile(dist / f"PhigrosScript_{TAG}.apk") as apk:
        index = json.loads(apk.read("assets/phigros-cloud-plans-v1.json"))
    assets = {}
    for i, shard in enumerate(index["shards"]):
        name = f"phigros-plans-v1-{i:03d}.bin"
        require(shard["github"] == f"https://github.com/{REPO}/releases/download/{TAG}/{name}",
                f"Unexpected mirror URL for {name}")
        require(re.fullmatch(r"[A-Za-z0-9_-]+", shard["drive"]) and 0 < shard["bytes"] <= 32 * 1024 * 1024,
                f"Invalid Drive file or length for {name}")
        url = "https://drive.usercontent.google.com/download?export=download&confirm=t&id=" + shard["drive"]
        for attempt in range(3):
            try:
                with urlopen(url, timeout=90) as response:
                    raw = response.read(shard["bytes"] + 1)
                info = metadata(raw)
                require(info == {"size": shard["bytes"], "sha256": shard["sha256"]}, f"{name} checksum mismatch")
                break
            except (TimeoutError, URLError, ValueError):
                if attempt == 2:
                    raise
                time.sleep(2 ** attempt)
        (dist / name).write_bytes(raw)
        assets[name] = info
    require(len(assets) == 33 and len(index["entries"]) == 1065, "Incomplete 1.0.0 chart library")
    return assets


def publish(dist, baseline):
    require(os.environ["GITHUB_REPOSITORY"] == REPO and os.environ["GITHUB_REF"] == "refs/heads/main",
            "Run on the main branch of the release repository")
    commit = os.environ["GITHUB_SHA"]
    patch = subprocess.check_output(["git", "rev-parse", f"{commit}:{PATCH}"], text=True).strip()
    manifest = manifest_at(DATA / "manifest.json", TAG, CODE)
    require(patch == manifest["patch_tree"], "Signed patch source mismatch")
    restore(baseline, DATA, dist, TAG, CODE)
    assets = manifest["assets"].copy()
    if TAG == "data1.1.3-r3":
        assets.update(stage_r3_shard(dist))
    if TAG == "1.0.0":
        assets.update(stage_drive_mirrors(dist))
    sums = "".join(f"{assets[name]['sha256']}  {name}\n" for name in sorted(assets)).encode("ascii")
    (dist / "SHA256SUMS.txt").write_bytes(sums)
    assets["SHA256SUMS.txt"] = metadata(sums)

    tag_ref = f"git/ref/tags/{TAG}"
    tag = api("GET", tag_ref, optional=True)
    require(tag is None or tag["object"]["sha"] == commit, "The tag already names another release")
    notes = (DATA.parent / "README.md").read_text(encoding="utf-8")
    notes = notes.split("## 构建与验证 / Build and verify", 1)[0].rstrip()
    notes += f"\n\n[发布源码 / Release source](https://github.com/{REPO}/tree/{commit})\n"
    release = find_release()
    if release is None:
        release = api("POST", "releases", body={"tag_name": TAG, "target_commitish": commit, "draft": True,
                                               "name": f"Phigros Script {TAG}", "body": notes})
    require(release["tag_name"] == TAG and release["target_commitish"] == commit
            and not release["prerelease"], "Unexpected release identity")
    endpoint = f"releases/{release['id']}"
    if release["draft"]:
        for item in release["assets"]:
            if item["name"] in assets and item["state"] == "starter" and item["size"] == 0:
                api("DELETE", f"releases/assets/{item['id']}")
        present = check_assets(api("GET", endpoint), assets, complete=False)
        for name in sorted(assets.keys() - present):
            api("POST", endpoint + "/assets?name=" + quote(name), upload=(dist / name).read_bytes())
        check_assets(api("GET", endpoint), assets, complete=True)
        api("PATCH", endpoint, body={"draft": False, "make_latest": "true", "body": notes})
    latest = api("GET", "releases/latest")
    previous = {"data1.1.3-r2": "data1.1.3", "data1.1.3-r3": "data1.1.3-r2", "1.0.0": "data1.1.3-r3"}.get(TAG)
    if latest["id"] != release["id"] and latest["tag_name"] == previous:
        # Resume if the release became public before GitHub marked it latest.
        public = api("GET", endpoint)
        require(not public["draft"] and not public["prerelease"], "Release is not public and stable")
        check_assets(public, assets, complete=True)
        require(api("GET", tag_ref)["object"]["sha"] == commit, "Published tag mismatch")
        api("PATCH", endpoint, body={"make_latest": "true"})
        latest = api("GET", "releases/latest")
    require(latest["id"] == release["id"] and not latest["draft"] and not latest["prerelease"],
            "Stable release is not latest")
    check_assets(latest, assets, complete=True)
    require(api("GET", tag_ref)["object"]["sha"] == commit, "Published tag mismatch")
    print(latest["html_url"])


if __name__ == "__main__":
    publish(Path(sys.argv[1]), Path(sys.argv[2]))
