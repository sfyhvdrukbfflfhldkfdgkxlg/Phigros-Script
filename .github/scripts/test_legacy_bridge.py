"""Validate that retries only change the independent migration release."""
from copy import deepcopy
import os
import unittest
from unittest.mock import patch

import publish_legacy_bridge as bridge


class GitHub:
    def __init__(self, data):
        self.data = data
        self.asset = {"name": bridge.APK, "size": len(data), "digest": "sha256:" + bridge.metadata(data)["sha256"],
                      "state": "uploaded", "id": 9}
        self.refs = {bridge.FORMAL: bridge.COMMIT}
        self.formal = {"id": 2, "tag_name": bridge.FORMAL, "draft": False, "prerelease": False,
                       "assets": [self.asset]}
        self.entry = None
        self.writes = []
        self.fail_upload = False

    def request(self, method, path, *, body=None, upload=None, optional=False):
        if method == "GET":
            if path.startswith("git/ref/tags/"):
                commit = self.refs.get(path.split("/")[-1])
                return {"object": {"type": "commit", "sha": commit}} if commit else None
            if path == "releases/tags/" + bridge.FORMAL or path == "releases/latest":
                return deepcopy(self.formal)
            if path == "releases/tags/" + bridge.BRIDGE:
                return deepcopy(self.entry) if self.entry and not self.entry["draft"] else None
            if path == "releases?per_page=100&page=1":
                return deepcopy([self.formal] + ([self.entry] if self.entry else []))
            if path == "releases/7":
                return deepcopy(self.entry)
        self.writes.append((method, path))
        if method == "POST" and path == "git/refs":
            assert body == {"ref": "refs/tags/" + bridge.BRIDGE, "sha": bridge.COMMIT}
            self.refs[bridge.BRIDGE] = bridge.COMMIT
        elif method == "POST" and path == "releases":
            assert body["target_commitish"] == bridge.COMMIT and body["tag_name"] == bridge.BRIDGE
            assert body["draft"] and not body["prerelease"] and body["make_latest"] == "false"
            self.entry = dict(body, id=7, assets=[], html_url="https://github.com/" + bridge.REPO + "/releases/tag/" + bridge.BRIDGE)
        elif method == "POST" and path == "releases/7/assets?name=" + bridge.APK:
            assert upload == self.data
            if self.fail_upload:
                self.entry["assets"].append(dict(self.asset, state="starter", size=0))
                raise RuntimeError("Interrupted asset upload")
            self.entry["assets"].append(self.asset)
        elif method == "DELETE" and path == "releases/assets/9":
            assert self.entry["draft"] and self.entry["assets"][0]["state"] == "starter"
            self.entry["assets"].clear()
        elif method == "PATCH" and path == "releases/7":
            assert self.entry["draft"] and self.entry["assets"] == [self.asset]
            assert body == {"draft": False, "prerelease": False, "make_latest": "false"}
            self.entry.update(body)
        else:
            raise AssertionError(f"Unexpected write {method} {path}")
        return deepcopy(self.entry)


class PublicationTests(unittest.TestCase):
    def setUp(self):
        self.data = b"fixture APK"
        self.remote = GitHub(self.data)
        for context in (patch.object(bridge, "api", self.remote.request),
                        patch.object(bridge, "EXPECTED", bridge.metadata(self.data)),
                        patch.dict(os.environ, {"GITHUB_REPOSITORY": bridge.REPO, "GITHUB_REF": "refs/heads/main"})):
            context.start()
            self.addCleanup(context.stop)

    def test_publish_then_no_writes(self):
        bridge.publish(self.data)
        self.assertFalse(self.remote.entry["draft"])
        self.remote.writes.clear()
        bridge.publish(self.data)
        self.assertEqual(self.remote.writes, [])

    def test_resume_interrupted_upload(self):
        self.remote.fail_upload = True
        with self.assertRaises(RuntimeError):
            bridge.publish(self.data)
        self.remote.fail_upload = False
        bridge.publish(self.data)
        self.assertEqual(self.remote.entry["assets"], [self.remote.asset])
        self.assertFalse(self.remote.entry["draft"])

    def test_wrong_inputs_leave_remote_untouched(self):
        with self.assertRaises(ValueError):
            bridge.publish(self.data + b"tampered")
        self.remote.refs[bridge.BRIDGE] = "different-commit"
        with self.assertRaises(ValueError):
            bridge.publish(self.data)
        self.assertEqual(self.remote.writes, [])

    def test_mismatched_public_asset_is_not_replaced(self):
        bridge.publish(self.data)
        self.remote.entry["assets"] = [dict(self.remote.asset, size=1)]
        self.remote.writes.clear()
        with self.assertRaises(ValueError):
            bridge.publish(self.data)
        self.assertEqual(self.remote.writes, [])


if __name__ == "__main__":
    unittest.main()
