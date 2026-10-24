#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata104/overlaytestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata104/overlaytestclasses $(rg --files patchdata104/tests/overlaymock -g '*.java') patchdata104/src/io/github/phiscript/capture/AutoOverlayBounds.java patchdata104/src/io/github/phiscript/capture/AutoOverlayFeedback.java patchdata104/tests/OverlayLifecycleRegression.java
java -cp patchdata104/overlaytestclasses io.github.phiscript.capture.OverlayLifecycleRegression > patchdata104/tests/OverlayLifecycleRegression.txt
cat patchdata104/tests/OverlayLifecycleRegression.txt
