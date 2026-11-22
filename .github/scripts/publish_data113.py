"""Restore and publish the verified, signed data1.1.3 build."""
import os
from pathlib import Path
import subprocess
import sys
from urllib.parse import quote

sys.path.insert(0, str(Path(__file__).parent / "data1_1_1"))
from publish import REPO, api, check_assets
from restore import manifest_at, metadata, require, restore

TAG = "data1.1.3"
CODE = 11005
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


def publish(dist, baseline):
    require(os.environ["GITHUB_REPOSITORY"] == REPO and os.environ["GITHUB_REF"] == "refs/heads/main",
            "Run on the main branch of the release repository")
    commit = os.environ["GITHUB_SHA"]
    patch = subprocess.check_output(["git", "rev-parse", f"{commit}:{PATCH}"], text=True).strip()
    manifest = manifest_at(DATA / "manifest.json", TAG, CODE)
    require(patch == manifest["patch_tree"], "Signed patch source mismatch")
    restore(baseline, DATA, dist, TAG, CODE)
    assets = manifest["assets"].copy()
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
    require(latest["id"] == release["id"] and not latest["draft"] and not latest["prerelease"],
            "Stable release is not latest")
    check_assets(latest, assets, complete=True)
    require(api("GET", tag_ref)["object"]["sha"] == commit, "Published tag mismatch")
    print(latest["html_url"])


if __name__ == "__main__":
    publish(Path(sys.argv[1]), Path(sys.argv[2]))
