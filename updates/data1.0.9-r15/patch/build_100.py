"""Build the numbered 1.0.0 release, keeping Android upgrade compatibility with data1.1.3-r3."""
from build_111 import main

if __name__ == "__main__":
    main("1.0.0", 11008)
