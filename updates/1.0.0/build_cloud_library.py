"""Rebuild the 1.0.0 portable Phigros tracks from a signed APK and the 4.1.0 source ZIP."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
from zipfile import ZipFile

HERE = Path(__file__).resolve().parent
PATCH = HERE.parent / 'data1.0.9-r15/patch'
sys.path.insert(0, str(PATCH / 'tools'))
from build_cloud_plans import TOOLS, inspect_member, write_atomic

BASE = 'https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/1.0.0/'


def compile_members(args, rows):
    host = args.work / 'classes'
    host.mkdir(exist_ok=True)
    archive = args.work / 'release.jar'
    subprocess.run(['java', '-Xmx1400m', '-cp', str(TOOLS / 'dex2jar/dex-tools-v2.4/lib/*'),
                    'com.googlecode.dex2jar.tools.Dex2jarCmd', '-f', '-o', archive, args.apk], check=True)
    jars = [archive, TOOLS / 'kotlin.jar', TOOLS / 'json.jar', TOOLS / 'platform/android-35/android.jar']
    classpath = os.pathsep.join(map(str, [host, *jars]))
    sources = [*sorted((PATCH / 'tests/mock').rglob('*.java')), HERE / 'CompilePortableCloudPlans.java']
    source_list = args.work / 'sources.txt'
    source_list.write_text(''.join(f'"{path.as_posix()}"\n' for path in sources), encoding='utf-8')
    subprocess.run(['java', '-jar', str(TOOLS / 'ecj.jar'), '-8', '-encoding', 'UTF-8', '-nowarn',
                    '-cp', classpath, '-d', host, '@' + str(source_list)], check=True)
    indices = sorted(range(len(rows)), key=lambda i: -rows[i]['bytes'])
    # Start independent long-running noise charts early. Let each worker take
    # one next chart at a time without waiting behind another worker's lane.
    indices.sort(key=lambda i: -rows[i]['noiseCount'])
    import queue
    queued = queue.Queue()
    for i in indices:
        queued.put(i)

    def worker(n):
        lane = args.work / f'worker-{n}.txt'
        while True:
            chunk = []
            try:
                first = queued.get_nowait()
                chunk.append(first)
                if not rows[first]['noiseCount']:
                    for _ in range(19):
                        chunk.append(queued.get_nowait())
            except queue.Empty:
                pass
            if not chunk:
                return
            lane.write_text(''.join(f'{i}\n' for i in chunk), encoding='ascii')
            subprocess.run(['java', '-Dfile.encoding=UTF-8', '-XX:+UseSerialGC', '-Xmx' + args.heap,
                            '-cp', classpath, 'io.github.phiscript.input.CompilePortableCloudPlans',
                            args.zip, args.work / 'members', lane], check=True)

    with ThreadPoolExecutor(max_workers=args.jobs) as executor:
        list(executor.map(worker, range(args.jobs)))


def pack(args, catalog, previous):
    indexed = {e['sha1']:e for e in catalog['entries']}
    if indexed.keys() != previous['entries'].keys():
        raise ValueError('The source catalog and previous shard layout must describe the same source release')
    shards,entries = [],{}
    drive_ids = json.loads(args.drive_ids.read_bytes()) if args.drive_ids else {}
    with ZipFile(args.zip) as source:
        for i,old in enumerate(previous['shards']):
            keys = sorted((k for k,p in previous['entries'].items() if p['shard'] == i),
                          key=lambda k:previous['entries'][k]['offset'])
            shard = bytearray()
            for key in keys:
                e = indexed[key]
                raw = source.read('charts/' + e['path'])
                git_hash = hashlib.sha1(b'blob ' + str(len(raw)).encode('ascii') + b'\0' + raw).hexdigest()
                if git_hash != key or len(raw) != e['bytes']:
                    raise ValueError('Chart changed: ' + e['path'])
                path = args.work / 'members' / (key + '.ptg.gz')
                fp, uncompressed = inspect_member(path)
                if hashlib.sha256(raw).hexdigest() != fp:
                    raise ValueError('Prepared chart fingerprint mismatch: ' + e['path'])
                data = path.read_bytes()
                entries[key] = dict(shard=i, offset=len(shard), length=len(data), bytes=uncompressed,
                                    sha256=hashlib.sha256(data).hexdigest(), fingerprint=fp)
                shard.extend(data)
            if not 0 < len(shard) <= 32 * 1024 * 1024:
                raise ValueError(f'Shard {i} is outside the client download limit')
            name = f'phigros-plans-v1-{i:03d}.bin'
            sha = hashlib.sha256(shard).hexdigest()
            target = args.out / name
            if args.verify_only:
                if hashlib.sha256(target.read_bytes()).hexdigest() != sha:
                    raise ValueError('Published shard differs from compiled members: ' + name)
            else:
                write_atomic(target, shard)
            row = old if old['sha256'] == sha else dict(github=BASE + name, drive=drive_ids.get(name, ''),
                                                        bytes=len(shard), sha256=sha)
            shards.append(row)
    manifest = dict(version=1, chartVersion=catalog['version'], chartCommit=catalog['chartCommit'],
                    shards=shards, entries=entries)
    path = args.out / 'phigros-cloud-plans-v1.json'
    content = (json.dumps(manifest,separators=(',',':'))+'\n').encode()
    if args.verify_only:
        if path.read_bytes() != content:
            raise ValueError('Published range index differs from the verified source data and cloud IDs')
    else:
        write_atomic(path, content)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('zip', type=Path)
    parser.add_argument('--previous-index', required=True, type=Path, help='data1.1.3-r3 range manifest')
    parser.add_argument('--apk', type=Path)
    parser.add_argument('--work', type=Path, required=True, help='separate directory for this planner revision')
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--drive-ids', type=Path, help='JSON object mapping each new shard filename to its Drive ID')
    parser.add_argument('--jobs', type=int, default=2, choices=range(1,33))
    parser.add_argument('--heap', default='700m')
    parser.add_argument('--pack-only', action='store_true')
    parser.add_argument('--verify-only', action='store_true', help='check the existing package against compiled members without writing it')
    args = parser.parse_args()
    if not (args.pack_only or args.verify_only) and not args.apk:
        parser.error('Supply a signed --apk or use --pack-only with already compiled members')
    args.zip, args.out, args.work = (p.resolve() for p in (args.zip, args.out, args.work))
    args.work.mkdir(parents=True,exist_ok=True)
    args.out.mkdir(parents=True,exist_ok=True)
    with ZipFile(args.zip) as z:
        catalog = json.loads(z.read('swordalt-catalog.json'))
    if not (args.pack_only or args.verify_only):
        compile_members(args,catalog['entries'])
    pack(args,catalog,json.loads(args.previous_index.read_bytes()))
