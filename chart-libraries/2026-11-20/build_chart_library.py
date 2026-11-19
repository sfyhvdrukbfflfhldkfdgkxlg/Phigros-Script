"""Build a Phigros Script offline ZIP from a pinned chart tree and its catalog."""
import argparse
import collections
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import tarfile
import tempfile
import time
import unicodedata
from urllib.request import Request, urlopen
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

REPO = "7aGiven/Phigros_Resource"
CHART = "5d40bfdf05bee53f5107a6ea5620ca0e953de815"
INFO = "da34d1de07cd59b096ca0520b2815a2ea11cc13e"
MAX_CHART = 32 * 1024 * 1024
DATE = (2026, 11, 20, 0, 0, 0)


def download(url, dest):
    if dest.is_file():
        return
    pending = dest.with_suffix(dest.suffix + ".downloading")
    for attempt in range(4):
        try:
            req = Request(url, headers={"User-Agent": "Phigros-Script-library/1"})
            with urlopen(req, timeout=120) as src, pending.open("wb") as out:
                shutil.copyfileobj(src, out, 1024 * 1024)
            pending.replace(dest)
            return
        except OSError:
            if attempt == 3:
                raise
            time.sleep(2 ** attempt)


def git_hash(data):
    return hashlib.sha1(b"blob " + str(len(data)).encode() + b"\0" + data).hexdigest()


def key(title):
    return "".join(c for c in unicodedata.normalize("NFKC", title).casefold() if c.isalnum())


def add_identifiers(entries):
    owners = collections.defaultdict(set)
    candidates = {}
    for e in entries:
        title = e["title"]
        for s in [title, *e["aliases"]]:
            owners[key(s)].add(title)
        if e["siteVisible"] and e["id"].endswith(".0"):
            name = e["id"].split(".")[0]
            if len(key(name)) >= 4:
                candidates[title] = name
                owners[key(name)].add(title)
    added = set()
    for e in entries:
        name = candidates.get(e["title"])
        if (name and owners[key(name)] == {e["title"]} and
                key(name) not in {key(t) for t in [e["title"], *e["aliases"]]}):
            e["aliases"].append(name)
            added.add(e["title"])
    return len(added)


def put(z, name, data):
    info = ZipInfo(name, DATE)
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    z.writestr(info, data, compress_type=ZIP_DEFLATED, compresslevel=6)


def from_tar(arc):
    with tarfile.open(arc, "r|gz") as tar:
        for member in tar:
            if member.isfile():
                if member.size > MAX_CHART:
                    raise ValueError("Individual upstream chart exceeds app limit")
                yield member.name.partition("/")[2], tar.extractfile(member).read(member.size)


def build(assets, cache, output, blobs=None):
    cache.mkdir(parents=True, exist_ok=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    catalog = json.loads(assets.read_bytes())
    if (catalog["chartCommit"], catalog["infoCommit"]) != (CHART, INFO):
        raise ValueError("The input catalog is for a different resource revision")
    tree_path = cache / "chart-tree.json"
    download(f"https://api.github.com/repos/{REPO}/git/trees/{CHART}?recursive=1", tree_path)
    tree = json.loads(tree_path.read_bytes())
    assert not tree["truncated"]
    upstream = {r["path"]: (r["sha"], r["size"]) for r in tree["tree"] if r["type"] == "blob"}
    rows = catalog["entries"]
    local = {e["path"]: (e["sha1"], e["bytes"]) for e in rows}
    assert local == upstream and len(rows) == len(local)
    assert all(re.fullmatch(r"[^/\\]+/(?:EZ|HD|IN|AT|SP)\.json", p) and n <= MAX_CHART for p, (_, n) in local.items())
    assert sum(n for _, n in local.values()) < 4 * 1024 ** 3
    added = add_identifiers(rows)
    rows.sort(key=lambda e: e["path"])
    index_text = io.StringIO(newline="")
    writer = csv.writer(index_text, delimiter="\t", lineterminator="\n")
    writer.writerow(["曲名", "难度", "定数", "作曲", "噪域数", "匹配方式", "文件", "别名"])
    for e in sorted(rows, key=lambda r: (r["title"].casefold(), ["EZ","HD","IN","AT","SP"].index(r["difficulty"]))):
        writer.writerow([e["title"], e["difficulty"], e["level"], e["composer"], e["noiseCount"],
                         "自动或手选" if e["siteVisible"] else "手选", e["path"], " / ".join(e["aliases"])])
    print(f"catalog {len(rows)} charts, {added} extra identifier aliases", flush=True)
    if blobs is None:
        arc = cache / "charts.tar.gz"
        download(f"https://codeload.github.com/{REPO}/tar.gz/{CHART}", arc)
        members = from_tar(arc)
    else:
        members = ((p, (blobs / local[p][0]).read_bytes()) for p in sorted(local))
    by_path = {e["path"]: e for e in rows}
    seen = set()
    with tempfile.NamedTemporaryFile(dir=output.parent, prefix=output.name+".", suffix=".partial", delete=False) as tmp:
        pending = Path(tmp.name)
    try:
        with ZipFile(pending, "w") as z:
            put(z, "swordalt-catalog.json", json.dumps(catalog, ensure_ascii=False, separators=(",", ":")).encode("utf-8"))
            put(z, "song-index.tsv", index_text.getvalue().encode("utf-8-sig"))
            put(z, "README.txt", (
                f"Phigros Script 离线谱库 · {catalog['version']} · 2026-11-20\n"
                "从 Phigros 谱库中的 Swordalt 入口导入本 ZIP，再到「预计算触点」中选择曲目与难度。"
                "触点缓存与本机屏幕和触控设置绑定。课题模式需提前准备三首。\n"
                "song-index.tsv 列出曲目、难度、噪域数量、别名和教学或变体谱。"
                "噪域保存在 charts/ 下的原始谱面 JSON 内。\n"
                f"来源 https://github.com/{REPO}/tree/{CHART}\n"
                f"曲目资料 https://github.com/{REPO}/tree/{INFO}\n"
                "本包为社区整理的非官方谱面资源，曲目及谱面归各自权利人所有。\n"
            ).encode("utf-8"))
            previous = ""
            for path, data in members:
                if path <= previous or path not in by_path or len(data) != local[path][1]:
                    raise ValueError(f"Unexpected upstream member: {path}")
                previous = path
                if git_hash(data) != local[path][0]:
                    raise ValueError(f"Invalid Git blob: {path}")
                if b'"blockAreaList"' in data or by_path[path]["noiseCount"]:
                    doc = json.loads(data)
                    assert len(doc.get("blockAreaList", [])) == by_path[path]["noiseCount"]
                put(z, "charts/" + path, data)
                seen.add(path)
                if len(seen) % 150 == 0:
                    print("packed", len(seen), flush=True)
        assert seen == set(upstream)
        with ZipFile(pending) as z:
            assert z.namelist()[0] == "swordalt-catalog.json"
            assert len(z.namelist()) == len(seen) + 3
            for path, (digest, _) in local.items():
                assert git_hash(z.read("charts/" + path)) == digest, path
        with pending.open("rb") as completed:
            checksum = hashlib.file_digest(completed, "sha256").hexdigest()
        os.replace(pending, output)
        print(json.dumps(dict(version=catalog["version"], charts=len(rows), chartBytes=sum(n for _,n in local.values()),
                              visible=sum(e["siteVisible"] for e in rows), noiseCharts=sum(e["noiseCount"]>0 for e in rows),
                              noiseAreas=sum(e["noiseCount"] for e in rows), newAliases=added,
                              output=str(output), bytes=output.stat().st_size, sha256=checksum), ensure_ascii=False), flush=True)
    finally:
        pending.unlink(missing_ok=True)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    for arg in ("assets", "cache", "output"):
        p.add_argument("--" + arg, type=Path, required=True)
    p.add_argument("--blobs", type=Path, help="Optional predownloaded files named by their verified Git SHA")
    args = p.parse_args()
    build(args.assets, args.cache, args.output, args.blobs)
