"""Build the local R16 update against the verified public R15 APK."""
import argparse
import hashlib
import os
from pathlib import Path
import struct
import subprocess
import sys
import tempfile
from zipfile import ZipFile

from lib.dex_info import DexInfo

ROOT = Path(__file__).resolve().parents[1]
BASE_SHA256 = "d7d38b37f3ba821261c5c9917366c5600d7432c5836a1b8fb4e2b27bba58c5b8"
R15_CERT = "a40da80a59d170caa950cf15c18c454d47a39b26989d8b640ecd745ba71bf5dc"
APK_NAME = "PhigrosScript_data1.0.9-r16.apk"


def run(*args, **kwargs):
    return subprocess.run(list(map(str, args)), cwd=ROOT, check=True, **kwargs)


def manifest(data):
    old = "data1.0.9-r15".encode("utf-16le")
    assert data.count(old) == 1
    data = bytearray(data.replace(old, "data1.0.9-r16".encode("utf-16le")))
    pos = 8
    resources = []
    updated = 0
    while pos < len(data):
        kind, head, size = struct.unpack_from("<HHI", data, pos)
        assert head >= 8 and size >= head and pos + size <= len(data)
        if kind == 0x180:
            resources = struct.unpack_from("<" + "I" * ((size-head)//4), data, pos+head)
        elif kind == 0x102:
            start, width, count = struct.unpack_from("<HHH", data, pos+24)
            assert width == 20
            for i in range(count):
                attr = pos+16+start+i*width
                name = struct.unpack_from("<I", data, attr+4)[0]
                if name < len(resources) and resources[name] == 0x0101021b:
                    assert struct.unpack_from("<I", data, attr+16)[0] == 39
                    struct.pack_into("<I", data, attr+16, 40)
                    updated += 1
        pos += size
    assert updated == 1
    return bytes(data)


def package(base, dex, output):
    updated = DexInfo(dex)
    with ZipFile(base) as original, ZipFile(output, "w") as apk:
        unchanged_classes = set()
        for name in original.namelist():
            if name.endswith(".dex") and name != "classes12.dex":
                unchanged_classes.update(DexInfo(original.read(name)).classes)
        assert not updated.classes & unchanged_classes, "Replacement DEX duplicates native classes"
        old_classes = DexInfo(original.read("classes12.dex")).classes
        assert {c for c in old_classes - updated.classes if "$" not in c} == set(), "A top-level R15 helper was lost"
        for info in original.infolist():
            name = info.filename
            if name.upper().startswith("META-INF/") and name.upper().endswith((".RSA", ".DSA", ".EC", ".SF", "/MANIFEST.MF")):
                continue
            contents = original.read(name)
            if name == "AndroidManifest.xml":
                contents = manifest(contents)
            elif name == "classes12.dex":
                contents = dex
            apk.writestr(info, contents)


def build(base, key, cert, output):
    assert hashlib.sha256(base.read_bytes()).hexdigest() == BASE_SHA256, "Expected the official signed R15 APK"
    sdk = ROOT / "tools/platform/android-35/android.jar"
    bt = ROOT / "tools/build-tools/android-15"
    zipalign = bt / ("zipalign.exe" if os.name == "nt" else "zipalign")
    kotlin = ROOT / "tools/kotlin.jar"
    d2j = str(ROOT / "tools/dex2jar/dex-tools-v2.4/lib/*")
    ecj = ROOT / "tools/ecj.jar"
    signer = bt / "lib/apksigner.jar"
    output.parent.mkdir(parents=True, exist_ok=True)
    # Use a short relative path so the ECJ argument file works on Windows too.
    with tempfile.TemporaryDirectory(prefix="r16-", dir=ROOT, ignore_cleanup_errors=True) as work:
        work = Path(work)
        api, classes, jar, dex = (work / n for n in ("r15.jar", "classes", "r16.jar", "dex"))
        classes.mkdir()
        dex.mkdir()
        run("java", "-Xmx1600m", "-cp", d2j, "com.googlecode.dex2jar.tools.Dex2jarCmd", "-f", "-o", api, base)
        sources = work / "sources.txt"
        sources.write_text("\n".join(p.relative_to(ROOT).as_posix() for p in sorted((ROOT/"patch/src").rglob("*.java"))) + "\n", encoding="utf-8")
        run("java", "-jar", ecj, "-8", "-nowarn", "-encoding", "UTF-8", "-cp", os.pathsep.join(map(str, (api, sdk, kotlin))), "-d", classes, "@"+str(sources))
        run("java", "--add-modules", "jdk.jartool", "sun.tools.jar.Main", "cf", jar, "-C", classes, ".")
        run("java", "-cp", bt/"lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "26", "--lib", sdk, "--classpath", api, "--classpath", kotlin, "--output", dex, jar)
        assert {p.name for p in dex.glob("*.dex")} == {"classes.dex"}
        unsigned, aligned, signed = (work / n for n in ("unsigned.apk", "aligned.apk", "signed.apk"))
        package(base, (dex/"classes.dex").read_bytes(), unsigned)
        run(zipalign, "-P", "16", "-f", "4", unsigned, aligned)
        run("java", "-jar", signer, "sign", "--key", key, "--cert", cert, "--out", signed, aligned)
        report = run("java", "-jar", signer, "verify", "--verbose", "--print-certs", signed, stdout=subprocess.PIPE, text=True).stdout
        print(report)
        assert R15_CERT in report, "Signing certificate must match R15"
        run(zipalign, "-c", "-P", "16", "4", signed)
        run(sys.executable, "patch/tests/verify_dependencies.py", base, signed)
        output.write_bytes(signed.read_bytes())
    print(hashlib.sha256(output.read_bytes()).hexdigest(), output.name)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", type=Path)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--cert", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT / "deliverables" / APK_NAME)
    args = parser.parse_args()
    build(*(p.resolve() for p in (args.base, args.key, args.cert, args.output)))
