"""Publish this chart collection without promoting it to the latest app release."""
from hashlib import sha256
from http.client import HTTPSConnection
import json
import os
from pathlib import Path
import re
import sys
from urllib.parse import quote
from zipfile import ZipFile
import hashlib


REPO = "sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script"
TAG = "charts-4.0.1.2-20261120"
FILENAME = "Phigros_Charts_4.0.1.2_20261120.zip"
CATALOG = Path("updates/data1.0.9-r15/patch/assets/phigros-swordalt-catalog-v1.json")


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def checksum(file):
    digest = sha256()
    with file.open("rb") as reader:
        for block in iter(lambda: reader.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def verified_assets(archive):
    require(archive.name == FILENAME, "Unexpected archive name")
    source = json.loads(CATALOG.read_bytes())
    require(source["chartCommit"] == "5d40bfdf05bee53f5107a6ea5620ca0e953de815" and
            source["infoCommit"] == "da34d1de07cd59b096ca0520b2815a2ea11cc13e" and
            source["version"] == "4.0.1.2", "Unexpected source catalog")
    require(len(source["entries"]) == 1057, "Wrong source chart count")
    with ZipFile(archive) as packed:
        paths = {e["path"] for e in source["entries"]}
        names = {"charts/" + path for path in paths} | {"swordalt-catalog.json", "song-index.tsv", "README.txt"}
        require(len(paths) == 1057 and packed.namelist()[0] == "swordalt-catalog.json" and
                len(packed.namelist()) == len(names) and set(packed.namelist()) == names,
                "Archive layout differs from the complete importable library")
        manifest = json.loads(packed.read("swordalt-catalog.json"))
        require(all(manifest[k] == source[k] for k in ("version", "chartCommit", "infoCommit", "repository")),
                "Archive identifies a different source")
        rows = {entry["path"]: entry for entry in manifest["entries"]}
        require(len(manifest["entries"]) == 1057 and set(rows) == paths, "Internal catalog differs")
        for entry in source["entries"]:
            path, size, expected = entry["path"], entry["bytes"], entry["sha1"]
            require(all(rows[path][k] == entry[k] for k in entry if k != "aliases"), "Catalog mismatch: " + path)
            member = packed.getinfo("charts/" + path)
            require(member.file_size == size, "Chart length differs: " + path)
            sha = hashlib.sha1(f"blob {size}\0".encode("ascii"))
            with packed.open(member) as reader:
                for chunk in iter(lambda: reader.read(1024 * 1024), b""):
                    sha.update(chunk)
            require(sha.hexdigest() == expected, "Chart differs from pinned Git blob: " + path)
        require(packed.testzip() is None, "Archive CRC verification failed")
    digest = checksum(archive)
    sums = archive.with_name("SHA256SUMS.txt")
    sums.write_bytes(f"{digest}  {FILENAME}\n".encode("ascii"))
    return {archive.name: (archive, digest), sums.name: (sums, checksum(sums))}


def api(method, path, *, payload=None, upload=None, optional=False):
    headers = {"Authorization": "Bearer " + os.environ["GH_TOKEN"],
               "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28",
               "User-Agent": "Phigros-Script-chart-library"}
    connection = HTTPSConnection("uploads.github.com" if upload else "api.github.com", timeout=300)
    endpoint = f"/repos/{REPO}/{path}"
    try:
        if upload:
            headers["Content-Type"] = "application/zip" if upload.suffix == ".zip" else "text/plain"
            headers["Content-Length"] = str(upload.stat().st_size)
            with upload.open("rb") as stream:
                connection.request(method, endpoint, stream, headers)
        else:
            body = None if payload is None else json.dumps(payload).encode()
            if body is not None:
                headers["Content-Type"] = "application/json"
            connection.request(method, endpoint, body, headers)
        response = connection.getresponse()
        data = response.read()
        if optional and response.status == 404:
            return None
        require(200 <= response.status < 300, f"{method} {path} returned HTTP {response.status}")
        return json.loads(data) if data else None
    finally:
        connection.close()


def release_by_tag():
    result = api("GET", f"releases/tags/{TAG}", optional=True)
    if result:
        return result
    page = 1
    while True:
        releases = api("GET", f"releases?per_page=100&page={page}")
        for release in releases:
            if release["tag_name"] == TAG:
                return release
        if len(releases) < 100:
            return None
        page += 1


def check_assets(release, assets):
    present = set()
    for remote in release["assets"]:
        name = remote["name"]
        require(name in assets and name not in present, "Unexpected release asset: " + name)
        file, digest = assets[name]
        if release["draft"] and remote["state"] == "starter" and remote["size"] == 0:
            # GitHub can leave a zero-byte placeholder after a failed asset upload.
            api("DELETE", "releases/assets/" + str(remote["id"]))
            continue
        require(remote["state"] == "uploaded" and remote["size"] == file.stat().st_size
                and remote["digest"] == "sha256:" + digest, "Remote asset verification failed: " + name)
        present.add(name)
    return present


def publish(archive):
    require(os.environ.get("GITHUB_REPOSITORY") == REPO and
            os.environ.get("GITHUB_REF") == "refs/heads/publish/" + TAG, "Unexpected workflow source")
    commit = os.environ["GITHUB_SHA"]
    require(re.fullmatch(r"[0-9a-f]{40}", commit), "Invalid workflow commit")
    assets = verified_assets(archive)
    tag = api("GET", "git/ref/tags/" + TAG, optional=True)
    release = release_by_tag()
    if not release:
        require(not tag or tag["object"]["sha"] == commit, "Existing tag belongs to another commit")
        notes = (
            "Phigros 原谱离线谱库，资源版本 4.0.1.2，保留谱内噪域。\n\n"
            "在 Phigros Script 的 Phigros 谱库中打开 Swordalt，选择离线 ZIP。"
            "完成导入后，从「预计算触点」选择曲目和难度，生成适配本机屏幕的触控坐标。\n\n"
            "[上游谱面快照](https://github.com/7aGiven/Phigros_Resource/tree/"
            "5d40bfdf05bee53f5107a6ea5620ca0e953de815)\n\n"
            f"ZIP SHA-256 `{assets[FILENAME][1]}`"
        )
        release = api("POST", "releases", payload={"tag_name": TAG, "target_commitish": commit,
                      "name": "Phigros 离线谱库 4.0.1.2 · 2026-11-20", "body": notes,
                      "draft": True, "make_latest": "false"})
    require(release["tag_name"] == TAG and not release["prerelease"], "Unexpected release identity")
    path = "releases/" + str(release["id"])
    if release["draft"]:
        require(release["target_commitish"] == commit and (not tag or tag["object"]["sha"] == commit),
                "Existing draft belongs to another commit")
        existing = check_assets(release, assets)
        for name, (file, _) in assets.items():
            if name not in existing:
                api("POST", path + "/assets?name=" + quote(name), upload=file)
        require(check_assets(api("GET", path), assets) == set(assets), "Draft assets are incomplete")
        api("PATCH", path, payload={"draft": False, "make_latest": "false"})
    public = api("GET", "releases/tags/" + TAG)
    require(not public["draft"] and public["id"] == release["id"] and
            check_assets(public, assets) == set(assets), "Published assets are incomplete")
    require(api("GET", "releases/latest")["id"] != public["id"], "Chart library marked as latest app release")
    print(public["html_url"])


if __name__ == "__main__":
    publish(Path(sys.argv[1]))
