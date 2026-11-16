"""R18 final-APK regressions, including deletion and replay-motivated input timing."""
import argparse
from pathlib import Path
from run_r16 import regressions,compile_to,run,ROOT
from run_r17 import extra as r17_extra

def extra(work,api,host,models,tests):
    r17_extra(work,api,host,models,tests)
    files=sorted((ROOT/"patch/tests/r18").glob("*.java"))
    compile_to(tests,host,*files)
    for source in files:
        if not source.name.endswith("Regression.java"):continue
        text=source.read_text(encoding="utf-8")
        package=text.split("package ",1)[1].split(";",1)[0].strip()
        run("java","-Xmx1600m","-cp",host,package+"."+source.stem)

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk",type=Path)
    regressions(parser.parse_args().apk.resolve(),extra)
