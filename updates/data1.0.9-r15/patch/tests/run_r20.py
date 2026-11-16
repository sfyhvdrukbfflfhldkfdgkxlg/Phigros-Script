"""R20 final-APK regressions: prepared paths, cache persistence and native UI motion."""
import argparse
from pathlib import Path
from run_r16 import regressions,compile_to,run,ROOT
from run_r19 import extra as previous
from ui_motion_check import check as check_ui
def extra(work,api,host,models,tests):
    previous(work,api,host,models,tests)
    check_ui(work,api)
    files=sorted((ROOT/"patch/tests/r20").glob("*.java"))
    if files:compile_to(tests,host,*files)
    for source in files:
        if not source.name.endswith("Regression.java"):continue
        package=source.read_text(encoding="utf-8").split("package ",1)[1].split(";",1)[0].strip()
        run("java","-Dfile.encoding=UTF-8","-Xmx1800m","-cp",host,package+"."+source.stem)
if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__);p.add_argument("apk",type=Path)
    apk=p.parse_args().apk.resolve()
    run("python","patch/tests/icon_resource_check.py","upload/baseline-r15.apk",apk)
    regressions(apk,extra)
