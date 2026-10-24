#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata107/overlaytestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata107/overlaytestclasses $(rg --files patchdata107/tests/overlaymock -g '*.java') patchdata107/src/io/github/phiscript/capture/AutoOverlayBounds.java patchdata107/src/io/github/phiscript/capture/AutoOverlayFeedback.java patchdata107/tests/OverlayLifecycleRegression.java
java -cp patchdata107/overlaytestclasses io.github.phiscript.capture.OverlayLifecycleRegression > patchdata107/tests/OverlayLifecycleRegression.txt
cat patchdata107/tests/OverlayLifecycleRegression.txt
