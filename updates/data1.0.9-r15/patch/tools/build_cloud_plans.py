"""Compile published Phigros chart JSON into independently range-downloadable touch plans.

Requires Java and the pinned Android SDK 35, dex2jar, ECJ, Kotlin and JSON jars in
updates/data1.0.9-r15/tools (the same dependencies used by tests/run_r16.py).
Example: python patch/tools/build_cloud_plans.py charts.zip --apk signed.apk
         --work /tmp/phigros-compiler --out cloud --jobs 2
"""
import argparse
import concurrent.futures
import gzip
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parents[2]
TOOLS = Path(os.environ.get("PHIGROS_PLAN_TOOLS", ROOT / "tools")).resolve()
SOURCE = ROOT / "patch/tools/CompileCloudPlans.java"
TARGET = "io.github.phiscript.input.CompileCloudPlans"
DEFAULT_BASE = "https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/data1.1.2/"
SHARD_SIZE = 15 * 1024 * 1024
SETTINGS = b"gesture-noise-v2|true|true|0x1.18p5|0x1.8p2|0x1.f4p10|4|12|true|true"


def inspect_member(path):
    """Verify the inner PTPA and PTG checksums, source fingerprint and planner revision."""
    with gzip.open(path, "rb") as stream:
        header = stream.read(114)
        if len(header) != 114 or header[:10] != bytes.fromhex("50545041000000010040"):
            raise ValueError(f"Invalid playback header: {path}")
        fingerprint = header[10:74]
        if len(bytes.fromhex(fingerprint.decode("ascii"))) != 32:
            raise ValueError(f"Invalid source fingerprint: {path}")
        timeline_size = struct.unpack_from(">I", header, 74)[0]
        track_size = struct.unpack_from(">I", header, 110)[0]
        if not (0 < timeline_size <= 24 * 1024**2 and 128 <= track_size <= 40 * 1024**2):
            raise ValueError(f"Invalid playback length: {path}")
        timeline = stream.read(timeline_size)
        if len(timeline) != timeline_size or hashlib.sha256(timeline).digest() != header[78:110]:
            raise ValueError(f"Damaged timeline: {path}")
        track = stream.read(track_size)
        prefix = bytes.fromhex("50544731000000030040") + fingerprint + struct.pack(">H", len(SETTINGS)) + SETTINGS
        if len(track) != track_size or not track.startswith(prefix):
            raise ValueError(f"Incompatible gesture policy or source fingerprint: {path}")
        if hashlib.sha256(memoryview(track)[:-32]).digest() != track[-32:] or stream.read(1):
            raise ValueError(f"Damaged playback tracks: {path}")
        return fingerprint.decode("ascii"), len(header) + timeline_size + track_size


def host_classpath(apk, work):
    sdk = TOOLS / "platform/android-35/android.jar"
    library = work / "final.jar"
    subprocess.run(["java", "-Xmx1400m", "-cp", str(TOOLS / "dex2jar/dex-tools-v2.4/lib/*"),
                    "com.googlecode.dex2jar.tools.Dex2jarCmd", "-f", "-o", library, apk], check=True)
    api = os.pathsep.join(map(str, [library, TOOLS / "kotlin.jar", TOOLS / "json.jar", sdk]))
    classes = work / "classes"
    classes.mkdir(exist_ok=True)
    src = sorted((ROOT / "patch/tests/mock").rglob("*.java")) + [SOURCE]
    # Relative paths inside the checkout also work with spaces in the checkout location.
    sources = work / "host-sources.txt"
    sources.write_text("".join(f'"{p.relative_to(ROOT).as_posix()}"\n' for p in src), encoding="utf-8")
    subprocess.run(["java", "-jar", str(TOOLS / "ecj.jar"), "-8", "-nowarn", "-encoding", "UTF-8",
                    "-cp", api, "-d", classes, "@" + str(sources)], cwd=ROOT, check=True)
    return os.pathsep.join([str(classes), api])


def compile_members(args, entries):
    cp = host_classpath(args.apk, args.work)
    jobs = args.jobs
    indices = sorted(range(len(entries)), key=lambda i: -entries[i]["bytes"])
    seen = set()
    unique = []
    for i in indices:
        key = entries[i]["sha1"]
        if key not in seen:
            seen.add(key)
            unique.append(i)
    lanes = [unique[i::jobs] for i in range(jobs)]

    def run_worker(i):
        lane = args.work / f"lane-{i}.txt"
        lane.write_text("".join(f"{row}\n" for row in lanes[i]), encoding="ascii")
        subprocess.run(["java", "-Dfile.encoding=UTF-8", "-XX:+UseSerialGC", "-Xmx" + args.heap,
                        "-cp", cp, TARGET, args.zip, args.work / "members", lane], check=True)

    with concurrent.futures.ThreadPoolExecutor(max_workers=jobs) as pool:
        list(pool.map(run_worker, range(jobs)))


def write_atomic(target, data):
    fd, name = tempfile.mkstemp(dir=target.parent, prefix=target.name + ".", suffix=".partial")
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
        os.replace(name, target)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def pack(args, catalog, source):
    # Independent members can be requested with an HTTP Range or extracted from a bounded shard.
    out = args.out
    out.mkdir(parents=True, exist_ok=True)
    entries, shards = {}, []
    drive_ids = json.loads(args.drive_ids.read_text(encoding="utf-8")) if args.drive_ids else {}
    manifest = {"version": 1, "chartVersion": catalog["version"], "chartCommit": catalog["chartCommit"],
                "shards": shards, "entries": entries}
    shard = bytearray()

    def finish():
        name = f"phigros-plans-v1-{len(shards):03}.bin"
        dest = out / name
        if not dest.exists() or dest.read_bytes() != shard:
            write_atomic(dest, shard)
        shards.append({"github": args.github_base.rstrip("/") + "/" + name,
                       "drive": drive_ids.get(name, ""), "bytes": len(shard),
                       "sha256": hashlib.sha256(shard).hexdigest()})
        shard.clear()

    for entry in sorted(catalog["entries"], key=lambda e: (bool(e["noiseCount"]), e["sha1"])):
        key = entry["sha1"]
        if key in entries:
            continue
        path = args.work / "members" / (key + ".ptg.gz")
        if args.partial and not path.exists():
            print(f"{len(shards)} completed shards, prefix contains {len(entries)} unique charts")
            return
        member = path.read_bytes()
        fingerprint, size = inspect_member(path)
        if not args.partial:
            git_hash = hashlib.sha1(f"blob {entry['bytes']}\0".encode("ascii"))
            raw_hash = hashlib.sha256()
            with source.open("charts/" + entry["path"]) as raw:
                for chunk in iter(lambda: raw.read(256 * 1024), b""):
                    raw_hash.update(chunk)
                    git_hash.update(chunk)
            if raw_hash.hexdigest() != fingerprint or git_hash.hexdigest() != key:
                raise ValueError(f"Prepared plan and original chart differ: {entry['path']}")
        if len(member) > SHARD_SIZE:
            raise ValueError(f"Member exceeds shard size: {path}")
        if len(shard) + len(member) > SHARD_SIZE:
            finish()
        entries[key] = {"shard": len(shards), "offset": len(shard), "length": len(member),
                        "bytes": size, "sha256": hashlib.sha256(member).hexdigest(), "fingerprint": fingerprint}
        shard.extend(member)
    if shard:
        finish()
    manifest_path = out / "phigros-cloud-plans-v1.json"
    write_atomic(manifest_path, (json.dumps(manifest, separators=(",", ":")) + "\n").encode("ascii"))
    print(f"{len(entries)} plans in {len(shards)} shards, {manifest_path}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("zip", type=Path)
    parser.add_argument("--apk", type=Path, help="signed APK whose production planner and codecs to use")
    parser.add_argument("--work", type=Path, required=True, help="persistent directory for verified members")
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--jobs", type=int, default=2)
    parser.add_argument("--heap", default="650m", help="Java heap size for each compiler process")
    parser.add_argument("--github-base", default=DEFAULT_BASE)
    parser.add_argument("--drive-ids", type=Path, help="JSON object mapping published shard names to Drive file IDs")
    parser.add_argument("--pack-only", action="store_true")
    parser.add_argument("--partial", action="store_true", help="emit only completed shards while a compile is still running")
    args = parser.parse_args()
    if not 1 <= args.jobs <= 32 or (not args.pack_only and not args.apk):
        parser.error("supply --apk or --pack-only and choose 1–32 jobs")
    args.zip, args.work, args.out = (p.resolve() for p in (args.zip, args.work, args.out))
    args.work.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.zip) as archive:
        catalog = json.loads(archive.read("swordalt-catalog.json"))
    if not args.pack_only:
        compile_members(args, catalog["entries"])
    with zipfile.ZipFile(args.zip) as source:
        pack(args, catalog, source)


if __name__ == "__main__":
    main()
