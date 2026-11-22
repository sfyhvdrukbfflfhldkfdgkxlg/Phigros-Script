"""Verify production DEX, version and resources from the signed data1.1.1 APK."""
import argparse
import os
import tempfile
from pathlib import Path
from zipfile import ZipFile
from run_r16 import ROOT, compile_to, cp, regressions, run
from run_r20 import extra
from version_manifest_check import check

def check_download_menu(jar):
    with ZipFile(jar) as classes:
        home = classes.read("io/github/phiscript/root/HomeScreen.class")
        adapter = classes.read("io/github/phiscript/assets/ExternalCharts.class")
    for label in ("Google 云盘 · 识别后自动下载", "Google Drive · Download on song recognition"):
        assert label.encode("utf-8") in home, f"Missing Drive menu item: {label}"
    assert b"useDrive" in home, "Drive menu action missing"
    for action in (b"useNewShare", b"useCloud"):
        assert action not in home and action in adapter, f"Expected a hidden, retained loader: {action}"

def main(version, code):
    parser = argparse.ArgumentParser(description=f"Verify the signed {version} APK and its playback paths.")
    parser.add_argument("apk", type=Path)
    parser.add_argument("--replay", type=Path, nargs="+", default=[],
                        help="Optional original Desultory Signals JSON charts to check")
    parser.add_argument("--cloud-smoke", action="store_true",
                        help="Download a published noise chart and reopen it with networking disabled")
    args = parser.parse_args()
    apk = args.apk.resolve()
    base = ROOT/"upload/baseline-r15.apk"
    check(base, apk, version, code)
    run("python", "patch/tests/icon_resource_check.py", base, apk)
    def checks(work, api, host, models, tests):
        if code >= 11002:
            check_download_menu(work/"final.jar")
            if args.cloud_smoke:
                compile_to(tests, host, "patch/tests/cloud/PublishedCloudSmoke.java")
                run("java", "-Dfile.encoding=UTF-8", "-Xmx900m", "-cp", host,
                    "io.github.phiscript.assets.PublishedCloudSmoke", apk)
        timing = work/"timing"
        routing_cp = cp(timing, work/"routing-models", api)
        compile_to(timing, routing_cp, "patch/tests/r12/TimingRegression.java")
        run("java", "-cp", routing_cp, "io.github.phiscript.TimingRegression")
        extra(work, api, host, models, tests)
        replay = ("java", "-Dfile.encoding=UTF-8", "-Xmx1800m", "-cp", host,
                  "io.github.phiscript.input.DesultoryPlaybackRegression")
        paths = [source.resolve() for source in args.replay]
        for source in paths:
            run(*replay, *(["--standalone"] if code >= 11002 else []),
                *(["--clearance"] if code >= 11004 else []), source)
            if code >= 11004:
                run(*replay, "--standalone", "--random", "--clearance", source)
    # Keep the disk-capacity fixtures beside the build and release them after the run.
    with tempfile.TemporaryDirectory(prefix="release-temp-", dir=ROOT) as scratch:
        options = os.environ.get("JAVA_TOOL_OPTIONS", "")
        os.environ["JAVA_TOOL_OPTIONS"] = f'{options} -Duser.language=zh "-Djava.io.tmpdir={scratch}"'
        regressions(apk, checks)

if __name__ == "__main__":
    main("data1.1.1", 11001)
