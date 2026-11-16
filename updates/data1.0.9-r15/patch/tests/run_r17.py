"""R17 regression suite against native and helper classes from the signed APK."""
import argparse
from pathlib import Path
from run_r16 import regressions, compile_to, cp, run, ROOT


def extra(work, api, host, models, tests):
    compile_to(tests,host,"patch/tests/r16/GameForegroundRegression.java",
               *(p for p in (ROOT/"patch/tests/r17").glob("*.java")))
    run("java","-cp",host,"io.github.phiscript.input.GameForegroundRegression")
    run("java","-cp",host,"io.github.phiscript.assets.OnlinePhiraRegression")
    # Additional noise/import tests are kept alongside the source package.
    for source in sorted((ROOT/"patch/tests/r17").glob("*Noise*Regression.java")):
        text=source.read_text(encoding="utf-8")
        package=text.split("package ",1)[1].split(";",1)[0].strip()
        run("java","-Xmx1600m","-cp",host,package+"."+source.stem)


if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk",type=Path)
    regressions(parser.parse_args().apk.resolve(),extra)
