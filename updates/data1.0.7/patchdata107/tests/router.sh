#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata107/routertestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata107/routertestclasses $(rg --files patchdata107/tests/routermock -g '*.java') patchdata107/src/io/github/phiscript/capture/AutoSessionRouter.java patchdata107/tests/RouterFeedbackRegression.java
java -cp patchdata107/routertestclasses io.github.phiscript.capture.RouterFeedbackRegression > patchdata107/tests/RouterFeedbackRegression.txt
cat patchdata107/tests/RouterFeedbackRegression.txt
