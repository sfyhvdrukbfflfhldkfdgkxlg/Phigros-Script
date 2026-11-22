"""Build data1.1.1 from the verified public R15 baseline."""
import argparse
from pathlib import Path
from build_r17 import build, ROOT

VERSION = "data1.1.1"
VERSION_CODE = 11001

def main(version, code):
    parser = argparse.ArgumentParser(description=f"Build {version} from the verified R15 baseline.")
    parser.add_argument("base", type=Path)
    parser.add_argument("--key", type=Path, required=True)
    parser.add_argument("--cert", type=Path, required=True)
    parser.add_argument("--output", type=Path, default=ROOT/f"deliverables/PhigrosScript_{version}.apk")
    args = parser.parse_args()
    build(*(path.resolve() for path in (args.base, args.key, args.cert, args.output)),
          release=version, code=code)

if __name__ == "__main__":
    main(VERSION, VERSION_CODE)
