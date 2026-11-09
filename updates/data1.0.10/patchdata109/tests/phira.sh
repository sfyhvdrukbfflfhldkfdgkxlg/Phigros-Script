#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/phiratestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata109/phiratestclasses $(rg --files patchdata109/tests/phiramock -g '*.java') patchdata109/src/io/github/phiscript/capture/PhiraPackages.java patchdata109/src/io/github/phiscript/capture/PhiraForeground.java patchdata109/src/io/github/phiscript/root/PhiraExport.java patchdata109/tests/PhiraProRegression.java
java -cp patchdata109/phiratestclasses io.github.phiscript.capture.PhiraProRegression > patchdata109/tests/PhiraProRegression.txt
cat patchdata109/tests/PhiraProRegression.txt
python3 patchdata109/tests/phira_hooks.py
