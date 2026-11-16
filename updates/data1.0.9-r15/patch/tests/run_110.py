"""data1.1.0 signed-APK validation, including version and launcher resources."""
import argparse
from pathlib import Path
from run_r16 import regressions,run
from run_r20 import extra
if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("apk",type=Path)
    apk=parser.parse_args().apk.resolve()
    run("python","patch/tests/version_manifest_check.py","upload/baseline-r15.apk",apk)
    run("python","patch/tests/icon_resource_check.py","upload/baseline-r15.apk",apk)
    regressions(apk,extra)
