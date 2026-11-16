"""Build replay-informed R18 from the exact public R15 baseline."""
import argparse
from pathlib import Path
from build_r17 import build,ROOT

if __name__=="__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base",type=Path)
    parser.add_argument("--key",type=Path,required=True)
    parser.add_argument("--cert",type=Path,required=True)
    parser.add_argument("--output",type=Path,default=ROOT/"deliverables/PhigrosScript_data1.0.9-r18.apk")
    args=parser.parse_args()
    build(*(p.resolve() for p in (args.base,args.key,args.cert,args.output)),release="data1.0.9-r18",code=42)
