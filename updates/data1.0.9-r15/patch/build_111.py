"""Build data1.1.1 from the verified public R15 baseline."""
import argparse
from pathlib import Path
from build_r17 import build, ROOT

VERSION = "data1.1.1"
VERSION_CODE = 11001

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", type=Path)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--cert", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT/"deliverables/PhigrosScript_data1.1.1.apk")
    args = parser.parse_args()
    build(*(path.resolve() for path in (args.base, args.key, args.cert, args.output)),
          release=VERSION, code=VERSION_CODE)
