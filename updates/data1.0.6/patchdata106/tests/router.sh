#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata106/routertestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata106/routertestclasses $(rg --files patchdata106/tests/routermock -g '*.java') patchdata106/src/io/github/phiscript/capture/AutoSessionRouter.java patchdata106/tests/RouterFeedbackRegression.java
java -cp patchdata106/routertestclasses io.github.phiscript.capture.RouterFeedbackRegression > patchdata106/tests/RouterFeedbackRegression.txt
cat patchdata106/tests/RouterFeedbackRegression.txt
