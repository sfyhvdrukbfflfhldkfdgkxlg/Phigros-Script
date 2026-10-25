#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/routertestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata109/routertestclasses $(rg --files patchdata109/tests/routermock -g '*.java') patchdata109/src/io/github/phiscript/capture/AutoSessionRouter.java patchdata109/tests/RouterFeedbackRegression.java
java -cp patchdata109/routertestclasses io.github.phiscript.capture.RouterFeedbackRegression > patchdata109/tests/RouterFeedbackRegression.txt
cat patchdata109/tests/RouterFeedbackRegression.txt
