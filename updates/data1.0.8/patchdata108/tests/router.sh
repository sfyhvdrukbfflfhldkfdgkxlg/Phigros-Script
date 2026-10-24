#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata108/routertestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata108/routertestclasses $(rg --files patchdata108/tests/routermock -g '*.java') patchdata108/src/io/github/phiscript/capture/AutoSessionRouter.java patchdata108/tests/RouterFeedbackRegression.java
java -cp patchdata108/routertestclasses io.github.phiscript.capture.RouterFeedbackRegression > patchdata108/tests/RouterFeedbackRegression.txt
cat patchdata108/tests/RouterFeedbackRegression.txt
