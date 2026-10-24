#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata105/routertestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata105/routertestclasses $(rg --files patchdata105/tests/routermock -g '*.java') patchdata105/src/io/github/phiscript/capture/AutoSessionRouter.java patchdata105/tests/RouterFeedbackRegression.java
java -cp patchdata105/routertestclasses io.github.phiscript.capture.RouterFeedbackRegression > patchdata105/tests/RouterFeedbackRegression.txt
cat patchdata105/tests/RouterFeedbackRegression.txt
