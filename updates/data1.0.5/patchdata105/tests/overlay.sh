#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata105/overlaytestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata105/overlaytestclasses $(rg --files patchdata105/tests/overlaymock -g '*.java') patchdata105/src/io/github/phiscript/capture/AutoOverlayBounds.java patchdata105/src/io/github/phiscript/capture/AutoOverlayFeedback.java patchdata105/tests/OverlayLifecycleRegression.java
java -cp patchdata105/overlaytestclasses io.github.phiscript.capture.OverlayLifecycleRegression > patchdata105/tests/OverlayLifecycleRegression.txt
cat patchdata105/tests/OverlayLifecycleRegression.txt
