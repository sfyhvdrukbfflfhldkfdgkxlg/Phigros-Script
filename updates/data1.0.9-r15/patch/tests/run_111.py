"""Verify production DEX, version and resources from the signed data1.1.1 APK."""
import argparse
import os
import tempfile
from pathlib import Path
from run_r16 import ROOT, compile_to, cp, regressions, run
from run_r20 import extra
from version_manifest_check import check

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--replay", type=Path, nargs="+", default=[],
                        help="Optional original Desultory Signals JSON charts to check")
    args = parser.parse_args()
    apk = args.apk.resolve()
    base = ROOT/"upload/baseline-r15.apk"
    check(base, apk, "data1.1.1", 11001)
    run("python", "patch/tests/icon_resource_check.py", base, apk)
    def checks(work, api, host, models, tests):
        timing = work/"timing"
        routing_cp = cp(timing, work/"routing-models", api)
        compile_to(timing, routing_cp, "patch/tests/r12/TimingRegression.java")
        run("java", "-cp", routing_cp, "io.github.phiscript.TimingRegression")
        extra(work, api, host, models, tests)
        for source in args.replay:
            run("java", "-Dfile.encoding=UTF-8", "-Xmx1800m", "-cp", host,
                "io.github.phiscript.input.DesultoryPlaybackRegression", source.resolve())
    # Keep the disk-capacity fixtures beside the build and release them after the run.
    with tempfile.TemporaryDirectory(prefix="data111-temp-", dir=ROOT) as scratch:
        options = os.environ.get("JAVA_TOOL_OPTIONS", "")
        os.environ["JAVA_TOOL_OPTIONS"] = f'{options} -Duser.language=zh "-Djava.io.tmpdir={scratch}"'
        regressions(apk, checks)
