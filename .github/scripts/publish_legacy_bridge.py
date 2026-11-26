"""Publish the original 1.0.0 APK under a tag recognized by the historical Android updater."""
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).parent / "data1_1_1"))
from publish import REPO, api, check_assets
from restore import metadata, require


FORMAL = "1.0.0"
BRIDGE = "data1.1.4-to-1.0.0"
COMMIT = "079eeb82bd7f8351a7689ef3ac202c1b02be27d5"
APK = "PhigrosScript_1.0.0.apk"
EXPECTED = {"size": 48734683, "sha256": "876b9c0de158afb645ce6b73f247d3e30a0dd0109f891b4e39c0798448c69811"}
TITLE = "旧版客户端升级至 1.0.0 / Legacy client update to 1.0.0"
NOTES = f"""旧版客户端按旧的版本号规则检查更新，因此会显示标签 `{BRIDGE}`。本页提供正式 1.0.0 的同一份已签名 APK，覆盖安装后显示的版本为 **1.0.0**。后续更新通过应用内检查更新获取。[正式 1.0.0 与发行说明](https://github.com/{REPO}/releases/tag/{FORMAL})

Older clients use the previous version numbering scheme and show the tag `{BRIDGE}` when checking for updates. This download is the exact signed **1.0.0** APK. Install it over your existing copy, then use the in-app updater for subsequent releases. [Official 1.0.0 release and notes](https://github.com/{REPO}/releases/tag/{FORMAL})

APK SHA-256 `{EXPECTED["sha256"]}`
"""


def check_ref(tag, optional=False):
    ref = api("GET", "git/ref/tags/" + tag, optional=optional)
    if ref is not None:
        obj = ref["object"]
        require(obj["type"] == "commit" and obj["sha"] == COMMIT, f"Unexpected commit for {tag}")
    return ref


def find_bridge():
    release = api("GET", "releases/tags/" + BRIDGE, optional=True)
    if release:
        return release
    page = 1
    while True:
        batch = api("GET", f"releases?per_page=100&page={page}")
        for row in batch:
            if row["tag_name"] == BRIDGE:
                return row
        if len(batch) < 100:
            return None
        page += 1


def publish(data):
    require(os.getenv("GITHUB_REPOSITORY") == REPO and os.getenv("GITHUB_REF") == "refs/heads/main",
            "Expected the official repository main branch")
    require(metadata(data) == EXPECTED, "Downloaded APK differs from the pinned official 1.0.0")
    check_ref(FORMAL)
    formal = api("GET", "releases/tags/" + FORMAL)
    require(formal["tag_name"] == FORMAL and not formal["draft"] and not formal["prerelease"],
            "Missing stable canonical release")
    official_assets = [a for a in formal["assets"] if a["name"] == APK]
    check_assets({"assets": official_assets}, {APK: EXPECTED}, complete=True)

    ref = check_ref(BRIDGE, optional=True)
    release = find_bridge()
    if release:
        require(release["tag_name"] == BRIDGE and release["target_commitish"] == COMMIT
                and release["name"] == TITLE and not release["prerelease"], "Existing bridge identity differs")
        if not release["draft"]:
            require(ref is not None, "Public bridge has no tag")
            check_assets(release, {APK: EXPECTED}, complete=True)
    if ref is None:
        api("POST", "git/refs", body={"ref": "refs/tags/" + BRIDGE, "sha": COMMIT})
        check_ref(BRIDGE)
    if release is None:
        release = api("POST", "releases", body={"tag_name": BRIDGE, "target_commitish": COMMIT, "name": TITLE,
                      "body": NOTES, "draft": True, "prerelease": False, "make_latest": "false"})
    if release["draft"]:
        endpoint = f"releases/{release['id']}"
        for asset in release["assets"]:
            if asset["name"] == APK and asset["state"] == "starter" and asset["size"] == 0:
                api("DELETE", f"releases/assets/{asset['id']}")
        present = check_assets(api("GET", endpoint), {APK: EXPECTED}, complete=False)
        if APK not in present:
            api("POST", endpoint + "/assets?name=" + APK, upload=data)
        check_assets(api("GET", endpoint), {APK: EXPECTED}, complete=True)
        check_ref(BRIDGE)
        api("PATCH", endpoint, body={"draft": False, "prerelease": False, "make_latest": "false"})

    public = api("GET", "releases/tags/" + BRIDGE)
    require(public["id"] == release["id"] and not public["draft"] and not public["prerelease"],
            "Bridge publication incomplete")
    check_assets(public, {APK: EXPECTED}, complete=True)
    check_ref(BRIDGE)
    require(api("GET", "releases/latest")["id"] != public["id"], "Bridge unexpectedly became Latest")
    print(public["html_url"])


if __name__ == "__main__":
    publish(Path(sys.argv[1]).read_bytes())
