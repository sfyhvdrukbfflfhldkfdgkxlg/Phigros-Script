"""Resume the data1.1.1 draft, publish complete verified assets, and verify its tag."""
import json
import os
import re
import subprocess
import sys
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import quote
from urllib.request import Request, urlopen

from restore import APK, SOURCE, TAG, manifest_at, metadata, require


REPO = "sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script"


def api(method, path, *, body=None, upload=None, optional=False):
    host = "uploads.github.com" if upload is not None else "api.github.com"
    headers = {"Authorization": "Bearer " + os.environ["GH_TOKEN"],
               "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28",
               "User-Agent": "Phigros-Script-release"}
    data = None
    if upload is not None:
        data = upload
        headers["Content-Type"] = "application/octet-stream"
    elif body is not None:
        data = json.dumps(body).encode()
        headers["Content-Type"] = "application/json"
    request = Request(f"https://{host}/repos/{REPO}/{path}", method=method, headers=headers, data=data)
    try:
        with urlopen(request, timeout=180) as response:
            data = response.read()
            return json.loads(data) if data else None
    except HTTPError as error:
        if optional and error.code == 404:
            return None
        raise RuntimeError(f"GitHub {method} {path} returned {error.code}") from error


def find_release():
    # Authenticated listings include drafts that have no public tag yet.
    result = api("GET", "releases/tags/" + TAG, optional=True)
    if result is not None:
        return result
    page = 1
    while True:
        batch = api("GET", f"releases?per_page=100&page={page}")
        for entry in batch:
            if entry["tag_name"] == TAG:
                return entry
        if len(batch) < 100:
            return None
        page += 1


def check_tag(commit, optional):
    ref = api("GET", "git/ref/tags/" + TAG, optional=optional)
    if ref is None:
        return
    obj = ref["object"]
    while obj["type"] == "tag":
        obj = api("GET", "git/tags/" + obj["sha"])["object"]
    require(obj["type"] == "commit" and obj["sha"] == commit, "Existing tag targets a different commit")


def check_assets(release, expected, *, complete):
    seen = set()
    for asset in release["assets"]:
        name = asset["name"]
        require(name in expected and name not in seen, "Unexpected asset already on this release")
        info = expected[name]
        require(asset["state"] == "uploaded" and asset["size"] == info["size"] and
                asset["digest"] == "sha256:" + info["sha256"], f"Existing remote asset differs: {name}")
        seen.add(name)
    if complete:
        require(seen == set(expected), "Release assets incomplete")
    return seen


def publish(directory, manifest_file):
    require(os.environ.get("GITHUB_REPOSITORY") == REPO and os.environ.get("GITHUB_REF") == "refs/heads/main",
            "Unexpected repository or branch")
    commit = os.environ["GITHUB_SHA"]
    require(re.fullmatch(r"[0-9a-f]{40}", commit), "Unexpected workflow commit")
    manifest = manifest_at(manifest_file)
    patch_tree = subprocess.check_output(["git", "rev-parse", commit + ":updates/data1.0.9-r15/patch"],
                                         text=True).strip()
    require(patch_tree == manifest["patch_tree"], "Source differs from the verified build")
    expected = manifest["assets"]
    for name, info in expected.items():
        require(metadata((directory / name).read_bytes()) == info, f"Invalid local asset: {name}")
    check_tag(commit, optional=True)
    release = find_release()
    if release is None:
        notes = ("Phira / Phira Pro 默认重试延迟为 715 毫秒，默认不叠加谱面自带偏移。"
                 "Phigros 默认为 1700 毫秒。升级后请在首页曲库打开「预计算触点」。\n\n"
                 f"[使用与构建说明](https://github.com/{REPO}/blob/{TAG}/updates/data1.1.1/README.md)"
                 "\n\nSHA-256\n\n" +
                 "\n".join(f"- `{name}` · `{expected[name]['sha256']}`" for name in (APK, SOURCE)))
        release = api("POST", "releases", body={"tag_name": TAG, "target_commitish": commit,
                                                "name": f"Phigros Script {TAG}", "body": notes, "draft": True})
    require(release["tag_name"] == TAG and release["target_commitish"] == commit and not release["prerelease"],
            "Release identity differs from this build")
    release_path = f"releases/{release['id']}"
    present = check_assets(release, expected, complete=not release["draft"])
    if release["draft"]:
        for name in (APK, SOURCE):
            if name not in present:
                api("POST", release_path + "/assets?name=" + quote(name), upload=(directory / name).read_bytes())
        check_assets(api("GET", release_path), expected, complete=True)
        check_tag(commit, optional=True)
        api("PATCH", release_path, body={"draft": False, "make_latest": "true"})
    public = api("GET", "releases/tags/" + TAG)
    require(public["id"] == release["id"] and not public["draft"] and not public["prerelease"],
            "Release publication could not be verified")
    check_tag(commit, optional=False)
    check_assets(public, expected, complete=True)
    latest = api("GET", "releases/latest")
    require(latest["id"] == public["id"], "New stable release is not the latest")
    previous = api("GET", "releases/tags/data1.1.0")
    require(previous["id"] == 447240494 and previous["tag_name"] == "data1.1.0" and not previous["draft"],
            "Unexpected data1.1.0 release")
    if not previous["prerelease"]:
        api("PATCH", f"releases/{previous['id']}", body={"prerelease": True})
    previous = api("GET", "releases/tags/data1.1.0")
    require(previous["id"] == 447240494 and previous["prerelease"] and not previous["draft"],
            "Previous version was not marked as a public prerelease")
    require(api("GET", "releases/latest")["id"] == public["id"], "Latest release changed")
    print(public["html_url"])


if __name__ == "__main__":
    publish(Path(sys.argv[1]), Path(sys.argv[2]))
