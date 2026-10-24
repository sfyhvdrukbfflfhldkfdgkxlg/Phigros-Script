#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata106/overlaytestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata106/overlaytestclasses $(rg --files patchdata106/tests/overlaymock -g '*.java') patchdata106/src/io/github/phiscript/capture/AutoOverlayBounds.java patchdata106/src/io/github/phiscript/capture/AutoOverlayFeedback.java patchdata106/tests/OverlayLifecycleRegression.java
java -cp patchdata106/overlaytestclasses io.github.phiscript.capture.OverlayLifecycleRegression > patchdata106/tests/OverlayLifecycleRegression.txt
cat patchdata106/tests/OverlayLifecycleRegression.txt
