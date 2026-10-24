#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata104/routertestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -d patchdata104/routertestclasses $(rg --files patchdata104/tests/routermock -g '*.java') patchdata104/src/io/github/phiscript/capture/AutoSessionRouter.java patchdata104/tests/RouterFeedbackRegression.java
java -cp patchdata104/routertestclasses io.github.phiscript.capture.RouterFeedbackRegression > patchdata104/tests/RouterFeedbackRegression.txt
cat patchdata104/tests/RouterFeedbackRegression.txt
