"""R19 signed-APK regression entry point."""
import argparse
from pathlib import Path
from run_r16 import regressions,compile_to,run,ROOT
from run_r18 import extra as r18_extra
def extra(work,api,host,models,tests):
    r18_extra(work,api,host,models,tests)
    files=sorted((ROOT/"patch/tests/r19").glob("*.java"))
    if files:compile_to(tests,host,*files)
    for source in files:
        if not source.name.endswith(("Regression.java","Counterexamples.java")):continue
        package=source.read_text(encoding="utf-8").split("package ",1)[1].split(";",1)[0].strip()
        run("java","-Xmx1600m","-cp",host,package+"."+source.stem)
if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("apk",type=Path)
    regressions(p.parse_args().apk.resolve(),extra)
