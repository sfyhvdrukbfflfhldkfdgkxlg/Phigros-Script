"""Exercise the signed APK's actual helper and native DEX with Android host models."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]


def run(*args):
    subprocess.run(list(map(str, args)), cwd=ROOT, check=True, timeout=180)


def cp(*paths):
    return os.pathsep.join(map(str, paths))


def sources(path):
    return sorted((ROOT/path).rglob("*.java"))


def compile_to(output, deps, *files):
    output.mkdir(exist_ok=True)
    args = output/"sources.txt"
    args.write_text("\n".join(Path(p).relative_to(ROOT).as_posix() if Path(p).is_absolute() else str(p) for p in files) + "\n", encoding="utf-8")
    run("java", "-jar", "tools/ecj.jar", "-8", "-nowarn", "-encoding", "UTF-8", "-cp", deps, "-d", output, "@"+str(args))


def regressions(apk, extra=None):
    with tempfile.TemporaryDirectory(prefix="r16-tests-", dir=ROOT) as tmp:
        work = Path(tmp)
        patched = work/"final.jar"
        run("java", "-Xmx1600m", "-cp", "tools/dex2jar/dex-tools-v2.4/lib/*",
            "com.googlecode.dex2jar.tools.Dex2jarCmd", "-f", "-o", patched, apk)
        sdk = "tools/platform/android-35/android.jar"
        api = cp(patched, "tools/kotlin.jar", "tools/json.jar", sdk)
        routing_models,routing = work/"routing-models",work/"routing"
        compile_to(routing_models,api,*(p for p in sources("patch/tests/mock") if p.name!="AppSettings.java"))
        routing_cp=cp(routing,routing_models,api)
        compile_to(routing,routing_cp,"patch/tests/r16/CourseRoutingRegression.java")
        run("java","-cp",routing_cp,"io.github.phiscript.root.CourseRoutingRegression")
        models, tests = work/"models", work/"tests"
        compile_to(models, api, *sources("patch/tests/mock"))
        host = cp(tests, models, api)
        compile_to(tests, host, "patch/tests/src/Regression.java", "patch/tests/src/CoreTest.java",
                   "patch/tests/r14/ContactRegression.java", "patch/tests/r15/PointerReplayRegression.java",
                   "patch/tests/r12/NoisePlaybackRegression.java", "patch/tests/r16/NoiseSweepRegression.java",
                   "patch/tests/r4/NoiseIndexRegression.java", "patch/tests/src/CourseGateRegression.java",
                   "patch/tests/src/CourseClockRegression.java")
        for check in ("input.CoreTest", "input.ContactRegression", "input.PointerReplayRegression",
                      "input.NoisePlaybackRegression", "input.NoiseSweepRegression",
                      "capture.CourseGateRegression", "capture.CourseClockRegression"):
            run("java", "-Xmx1600m", "-cp", host, "io.github.phiscript."+check)
        run("java", "-cp", host, "NoiseIndexRegression")
        course = work/"course"
        compile_to(course, api, *sources("patch/tests/coursemock"), "patch/tests/src/CourseEngineRegression.java")
        run("java", "-cp", cp(course, api), "io.github.phiscript.capture.CourseEngineRegression")
        media, media_tests = work/"media", work/"media-tests"
        compile_to(media, api, *sources("patch/tests/r13/mock"))
        media_cp = cp(media_tests, media, api)
        compile_to(media_tests, media_cp, "patch/tests/r13/VideoPolicyTest.java",
                   "patch/tests/r13/CaptureRegression.java", "patch/tests/r15/RecordingProfilesRegression.java")
        for check in ("VideoPolicyTest", "CaptureRegression", "RecordingProfilesRegression"):
            run("java", "-cp", media_cp, "io.github.phiscript.record."+check)
        if extra is not None:
            extra(work, api, host, models, tests)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    regressions(parser.parse_args().apk.resolve())
