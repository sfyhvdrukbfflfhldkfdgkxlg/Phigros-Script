"""Reissue the stable APK while retaining the published cloud index and shard links."""
import argparse
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import quote
from zipfile import ZipFile

from publish_data112 import APK, INDEX, PATCH, TAG, cloud_index
from publish import REPO, api
from restore import manifest_at, metadata, require, restore

DATA = Path("updates/data1.1.2/rebuild-11003")
PREVIOUS = Path("updates/data1.1.2/release-data")
SOURCE = f"PhigrosScript_{TAG}_Source.zip"
SUMS = "SHA256SUMS.txt"
NAMES = (APK, SOURCE, SUMS)
TAG_COMMIT = "b7dafe76fa6aa6412215ad355d07b5c4495c92bf"
PENDING = ".pending-11003"
BACKUP = ".previous-11002"


def sums(expected):
    return "".join(f"{expected[n]['sha256']}  {n}\n" for n in sorted(expected)).encode("ascii")


def matches(asset, expected):
    return (asset and asset["state"] == "uploaded" and asset["size"] == expected["size"]
            and asset["digest"] == "sha256:" + expected["sha256"])


def rename(asset, new_name):
    result = api("PATCH", f"releases/assets/{asset['id']}", body={"name": new_name})
    require(result["id"] == asset["id"] and result["name"] == new_name, "Rename failed")


def publish(dist):
    commit = os.environ["GITHUB_SHA"]
    require(re.fullmatch("[0-9a-f]{40}", commit)
            and os.environ.get("GITHUB_REPOSITORY") == REPO
            and os.environ.get("GITHUB_REF") == "refs/heads/release/data112-rebuild-11003", "Unexpected job")
    manifest = manifest_at(DATA / "manifest.json", TAG, 11003)
    tree = subprocess.check_output(["git", "rev-parse", f"{commit}:{PATCH.as_posix()}"], text=True).strip()
    require(tree == manifest["patch_tree"], "APK source does not match the workflow commit")
    require(api("GET", "git/ref/tags/" + TAG)["object"]["sha"] == TAG_COMMIT, "Tag target changed")
    release = api("GET", "releases/tags/" + TAG)
    require(release["tag_name"] == TAG and not release["draft"] and not release["prerelease"]
            and api("GET", "releases/latest")["id"] == release["id"], "Expected current stable release")
    endpoint = f"releases/{release['id']}"
    cloud = {f"phigros-plans-v1-{i:03d}.bin": {"size": row["bytes"], "sha256": row["sha256"]}
             for i, row in enumerate(cloud_index(dist)["shards"])}
    with ZipFile(dist / APK) as apk:
        # The published index has LF line endings; Windows APK builds may include CRLF.
        cloud[INDEX] = metadata(apk.read("assets/" + INDEX).replace(b"\r\n", b"\n"))
    original = dict(manifest_at(PREVIOUS / "manifest.json", TAG, 11002)["assets"])
    updated = dict(manifest["assets"])
    original[SUMS] = metadata(sums(cloud | original))
    (dist / SUMS).write_bytes(sums(cloud | updated))
    updated[SUMS] = metadata((dist / SUMS).read_bytes())

    def remote():
        files = api("GET", endpoint + "/assets?per_page=100")
        assets = {f["name"]: f for f in files}
        require(len(files) == len(assets) < 100, "Unexpected release inventory")
        require(all(matches(assets.get(n), item) for n, item in cloud.items()), "Cloud library mismatch")
        allowed = set(cloud) | {n + suffix for n in NAMES for suffix in ("", PENDING, BACKUP)}
        require(set(assets) <= allowed, "Unexpected release asset")
        return assets

    assets = remote()
    for n in NAMES:
        canonical, staged, previous = (assets.get(n + suffix) for suffix in ("", PENDING, BACKUP))
        require(canonical is None or matches(canonical, original[n]) or matches(canonical, updated[n]),
                "Unknown public asset: " + n)
        require(previous is None or matches(previous, original[n]), "Unknown previous asset: " + n)
        require(canonical or previous, "Missing original: " + n)
        if staged and staged["state"] == "starter" and staged["size"] == 0:
            api("DELETE", f"releases/assets/{staged['id']}")
            staged = None
        require(not staged or matches(staged, updated[n]), "Unknown staging asset: " + n)
        if not matches(canonical, updated[n]) and staged is None:
            payload = (dist / n).read_bytes()
            require(metadata(payload) == updated[n], "Incorrect local asset: " + n)
            staged = api("POST", endpoint + "/assets?name=" + quote(n + PENDING), upload=payload)
            require(matches(staged, updated[n]), "Staged asset differs: " + n)

    try:
        assets = remote()
        for n in NAMES:
            if matches(assets.get(n), updated[n]):
                continue
            require(matches(assets.get(n + PENDING), updated[n]), "Missing verified replacement: " + n)
            if n in assets:
                require(n + BACKUP not in assets and matches(assets[n], original[n]), "Backup conflict")
                rename(assets[n], n + BACKUP)
            rename(assets[n + PENDING], n)
        assets = remote()
        require(all(matches(assets.get(n), updated[n]) for n in NAMES), "Public release verification failed")
        marker = "<!-- data112-reissue-11003 -->"
        body = api("GET", endpoint)["body"] or ""
        if marker not in body:
            notes = Path("updates/data1.1.2/REISSUE.md").read_text(encoding="utf-8")
            summary = notes.split("## Build\n", 1)[0].split("## 中文\n", 1)[1].strip()
            link = f"https://github.com/{REPO}/tree/{commit}"
            body = f"{marker}\n## data1.1.2 · Build 11003\n\n### 中文\n\n{summary}\n\n[构建源码 / Build source]({link})\n\n---\n\n{body}"
            result = api("PATCH", endpoint, body={"body": body})
            require(result["body"] == body, "Release notes were not saved")
        require(commit in body, "Release notes reference a different build")
    except Exception:
        # Restore original canonical URLs, retaining both verified builds for a retry.
        assets = remote()
        for n in NAMES:
            if n + BACKUP in assets and matches(assets[n + BACKUP], original[n]):
                if matches(assets.get(n), updated[n]) and n + PENDING not in assets:
                    rename(assets[n], n + PENDING)
                if n not in assets or matches(assets.get(n), updated[n]):
                    rename(assets[n + BACKUP], n)
        raise

    for n in NAMES:
        for suffix, expected in ((BACKUP, original[n]), (PENDING, updated[n])):
            asset = assets.get(n + suffix)
            if asset:
                require(matches(asset, expected), "Unexpected obsolete release asset")
                api("DELETE", f"releases/assets/{asset['id']}")
    assets = remote()
    require(set(assets) == set(cloud) | set(NAMES)
            and all(matches(assets[n], updated[n]) for n in NAMES), "Final release verification failed")
    print("Verified replacement APK, source, checksums and all cloud chart assets:", release["html_url"])


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["restore", "publish"])
    parser.add_argument("dist", type=Path)
    parser.add_argument("baseline", type=Path, nargs="?")
    args = parser.parse_args()
    if args.action == "restore":
        restore(args.baseline, DATA, args.dist, TAG, 11003)
    else:
        publish(args.dist)
