"""Publish verified data1.1.0 bytes, then remove only the pinned obsolete release."""

import hashlib
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path
from urllib.parse import quote

from restore import ASSET_NAMES, PATCH_TREE, load_manifest, require


REPO = "sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script"
TAG = "data1.1.0"
OLD_TAG = "data1.0.10"
OLD_ID = 439100299
OLD_COMMIT = "bdb87c01031a8a0d0259426f99e68d62e432cd44"
OLD_ASSETS = {
    "PhigrosScript_data1.0.10.apk": (48189006, "fca66e2426790b72fa8f9291f20470d167bcad05153677f6780771de8fffe20e"),
    "GestureRecorder_1.0.0.apk": (29075, "60685996c5db3041dd839d6c87345c19a2420642d387af28d1ba4ea16ae5430d"),
    "PhigrosScript_data1.0.10_Source.zip": (933678, "b79da0af280cad5e06069769000445e9e54f0b92f46738b1b7b0acab30293d4f"),
    "SHA256SUMS.txt": (289, "642dd8d8287cd27924dcd2b9e421b8f172a023ced2faf46789f5477191b6db48"),
}


def api(method, path, payload=None, allow_missing=False, input_file=None):
    endpoint = path if path.startswith("https://uploads.github.com/") else "repos/" + REPO + "/" + path
    command = ["gh", "api", "--include", "--method", method, endpoint,
               "-H", "Accept: application/vnd.github+json",
               "-H", "X-GitHub-Api-Version: 2022-11-28"]
    data = None
    if input_file is not None:
        command += ["-H", "Content-Type: application/octet-stream", "--input", str(input_file)]
    elif payload is not None:
        command += ["--input", "-"]
        data = json.dumps(payload)
    result = subprocess.run(command, input=data, text=True, capture_output=True, timeout=180)
    sections = re.split(r"\r?\n\r?\n", result.stdout, maxsplit=1)
    status_match = re.match(r"HTTP/\S+\s+(\d{3})", sections[0])
    if not status_match:
        raise RuntimeError("GitHub API returned no HTTP status: " + method + " " + path)
    status = int(status_match.group(1))
    if status == 404 and allow_missing:
        return None
    expected_status = 204 if method == "DELETE" else (201 if method == "POST" else 200)
    if status != expected_status or result.returncode:
        raise RuntimeError("GitHub API failed: " + method + " " + path + " HTTP " + str(status))
    if status == 204:
        return None
    require(len(sections) == 2, "GitHub API returned no response body")
    return json.loads(sections[1])


def check_main(commit):
    ref = api("GET", "git/ref/heads/main")
    require(ref["object"]["type"] == "commit" and ref["object"]["sha"] == commit,
            "Main advanced; refusing release mutation")


def check_source_tree(commit):
    obj = api("GET", "git/commits/" + commit)
    require(obj.get("sha") == commit, "Unexpected workflow source commit")
    tree = obj["tree"]["sha"]
    for part in ("updates", "data1.0.9-r15", "patch"):
        entries = api("GET", "git/trees/" + tree)
        require(entries.get("truncated") is False, "Source tree response was truncated")
        matches = [entry for entry in entries["tree"] if entry["path"] == part]
        require(len(matches) == 1 and matches[0]["type"] == "tree", "Verified patch tree is missing")
        tree = matches[0]["sha"]
    require(tree == PATCH_TREE, "Product source differs from the verified patch tree")


def tag_commit(allow_missing=False):
    ref = api("GET", "git/ref/tags/" + TAG, allow_missing=allow_missing)
    if ref is None:
        return None
    obj = ref["object"]
    for _ in range(8):
        if obj["type"] == "commit":
            return obj["sha"]
        require(obj["type"] == "tag", "Unexpected release tag object")
        obj = api("GET", "git/tags/" + obj["sha"])["object"]
    raise ValueError("Release tag nesting exceeds limit")


def find_release():
    matches = []
    for page in range(1, 1001):
        releases = api("GET", "releases?per_page=100&page=" + str(page))
        require(isinstance(releases, list), "Invalid release collection")
        matches.extend(release for release in releases if release["tag_name"] == TAG)
        if len(releases) < 100:
            require(len(matches) <= 1, "Multiple releases share the tag; refusing to guess")
            return matches[0] if matches else None
    raise ValueError("Release listing exceeds pagination limit")


def check_identity(release, commit, notes):
    require(type(release.get("id")) is int and release["id"] != OLD_ID and
            release.get("tag_name") == TAG and release.get("target_commitish") == commit,
            "Existing release points to different source")
    require(release.get("name") == "Phigros Script " + TAG and release.get("body") == notes and
            release.get("prerelease") is False, "Unexpected release metadata; refusing overwrite")


def check_assets(release, expected, complete):
    assets = release.get("assets")
    require(isinstance(assets, list), "Invalid release assets")
    present = {asset["name"]: asset for asset in assets}
    require(len(present) == len(assets) and not (set(present) - set(expected)),
            "Unexpected or duplicate remote assets; refusing overwrite")
    if complete:
        require(set(present) == set(expected), "Release assets are incomplete")
    for name, asset in present.items():
        item = expected[name]
        require(asset.get("state") == "uploaded" and asset.get("size") == item["size"] and
                asset.get("digest") == "sha256:" + item["sha256"],
                "Remote asset size or SHA256 mismatch: " + name)
    return present


def verify_published(release_id, commit, notes, expected):
    release = api("GET", "releases/" + str(release_id))
    check_identity(release, commit, notes)
    require(release.get("draft") is False, "Release is still a draft")
    check_assets(release, expected, complete=True)
    by_tag = api("GET", "releases/tags/" + TAG)
    require(by_tag["id"] == release_id and by_tag.get("draft") is False,
            "Published tag resolves to another release")
    check_assets(by_tag, expected, complete=True)
    require(tag_commit() == commit, "Published tag points to different source")
    latest = api("GET", "releases/latest")
    require(latest["id"] == release_id, "Release is not the latest release")
    return release


def remove_old_release(release_id, commit, notes, expected):
    # This gate runs even on retries where the obsolete release is already absent.
    verify_published(release_id, commit, notes, expected)
    check_main(commit)
    old = api("GET", "releases/" + str(OLD_ID), allow_missing=True)
    if old is not None:
        require(old.get("id") == OLD_ID and old.get("tag_name") == OLD_TAG and
                old.get("target_commitish") == OLD_COMMIT and old.get("draft") is False and
                old.get("immutable") is False, "Obsolete release identity changed; refusing deletion")
        old_expected = {name: {"size": item[0], "sha256": item[1]}
                        for name, item in OLD_ASSETS.items()}
        check_assets(old, old_expected, complete=True)
        by_tag = api("GET", "releases/tags/" + OLD_TAG)
        require(by_tag["id"] == OLD_ID, "Obsolete tag resolves to another release")
        verify_published(release_id, commit, notes, expected)
        check_main(commit)
        api("DELETE", "releases/" + str(OLD_ID))
    require(api("GET", "releases/" + str(OLD_ID), allow_missing=True) is None,
            "Obsolete release still exists")
    require(api("GET", "releases/tags/" + OLD_TAG, allow_missing=True) is None,
            "Another release now uses the obsolete tag; refusing further deletion")
    # Deliberately never delete or move refs/tags/data1.0.10.
    verify_published(release_id, commit, notes, expected)


def publish(assets_dir, manifest_path, notes_path):
    require(os.environ.get("GITHUB_REPOSITORY") == REPO and
            os.environ.get("GITHUB_REF") == "refs/heads/main", "Wrong repository or branch")
    commit = os.environ.get("GITHUB_SHA", "")
    require(re.fullmatch(r"[0-9a-f]{40}", commit), "Invalid workflow commit")
    manifest = load_manifest(manifest_path)
    expected = manifest["assets"]
    root = Path(assets_dir)
    require(root.is_dir() and not root.is_symlink() and
            {path.name for path in root.iterdir()} == ASSET_NAMES, "Unexpected local assets")
    for name, item in expected.items():
        path = root / name
        require(path.is_file() and not path.is_symlink() and path.stat().st_size == item["size"],
                "Local asset file mismatch: " + name)
        with path.open("rb") as stream:
            digest = hashlib.sha256()
            for block in iter(lambda: stream.read(1024 * 1024), b""):
                digest.update(block)
        require(digest.hexdigest() == item["sha256"], "Local asset SHA256 mismatch: " + name)
    notes = Path(notes_path).read_text(encoding="utf-8")
    require(notes and len(notes.encode("utf-8")) < 100000, "Invalid release notes")
    check_source_tree(commit)
    check_main(commit)
    existing_tag = tag_commit(allow_missing=True)
    require(existing_tag is None or existing_tag == commit, "Existing tag points to different source")
    release = find_release()
    if release is None:
        release = api("POST", "releases", {
            "tag_name": TAG, "target_commitish": commit, "name": "Phigros Script " + TAG,
            "body": notes, "draft": True, "prerelease": False,
        })
    check_identity(release, commit, notes)
    require(type(release.get("draft")) is bool, "Invalid release draft state")
    present = check_assets(release, expected, complete=not release["draft"])
    for name in sorted(expected):
        if name not in present:
            require(release["draft"], "Published release is incomplete; refusing mutation")
            check_main(commit)
            endpoint = ("https://uploads.github.com/repos/" + REPO + "/releases/" +
                        str(release["id"]) + "/assets?name=" + quote(name, safe=""))
            asset = api("POST", endpoint, input_file=root / name)
            check_assets({"assets": [asset]}, {name: expected[name]}, complete=True)
    release = api("GET", "releases/" + str(release["id"]))
    check_identity(release, commit, notes)
    check_assets(release, expected, complete=True)
    check_main(commit)
    # No asset overwrite or deletion occurs. Only an exact release can become latest.
    api("PATCH", "releases/" + str(release["id"]),
        {"draft": False, "prerelease": False, "make_latest": "true"})
    published = None
    for attempt in range(5):
        try:
            published = verify_published(release["id"], commit, notes, expected)
            break
        except (ValueError, RuntimeError):
            if attempt == 4:
                raise
            time.sleep(2)
    require(published is not None, "Publication verification failed")
    remove_old_release(release["id"], commit, notes, expected)
    print("https://github.com/" + REPO + "/releases/tag/" + TAG)
    print("Verified all 12 remote asset digests and the latest release; obsolete release removed, Git tag retained.")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        raise SystemExit("Usage: publish.py ASSETS_DIR MANIFEST_PATH RELEASE_NOTES")
    publish(*sys.argv[1:])
