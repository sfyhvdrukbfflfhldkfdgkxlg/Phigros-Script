#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata108/overlaytestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata108/overlaytestclasses $(rg --files patchdata108/tests/overlaymock -g '*.java') patchdata108/src/io/github/phiscript/capture/AutoOverlayBounds.java patchdata108/src/io/github/phiscript/capture/AutoOverlayFeedback.java patchdata108/tests/OverlayLifecycleRegression.java
java -cp patchdata108/overlaytestclasses io.github.phiscript.capture.OverlayLifecycleRegression > patchdata108/tests/OverlayLifecycleRegression.txt
cat patchdata108/tests/OverlayLifecycleRegression.txt
