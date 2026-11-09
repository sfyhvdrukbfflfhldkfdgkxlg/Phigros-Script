#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/overlaytestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata109/overlaytestclasses $(rg --files patchdata109/tests/overlaymock -g '*.java') patchdata109/src/io/github/phiscript/capture/AutoOverlayBounds.java patchdata109/src/io/github/phiscript/capture/AutoOverlayFeedback.java patchdata109/tests/OverlayLifecycleRegression.java
java -cp patchdata109/overlaytestclasses io.github.phiscript.capture.OverlayLifecycleRegression > patchdata109/tests/OverlayLifecycleRegression.txt
cat patchdata109/tests/OverlayLifecycleRegression.txt
