"""Build R20 from the verified R15 base and current patch sources."""
import argparse
from pathlib import Path
from build_r17 import build,ROOT
if __name__=="__main__":
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument("base",type=Path);p.add_argument("--key",type=Path,required=True);p.add_argument("--cert",type=Path,required=True)
    p.add_argument("--output",type=Path,default=ROOT/"deliverables/PhigrosScript_data1.0.9-r20.apk")
    a=p.parse_args();build(*(v.resolve() for v in (a.base,a.key,a.cert,a.output)),release="data1.0.9-r20",code=44)
