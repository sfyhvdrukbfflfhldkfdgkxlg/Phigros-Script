#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata104/controltestclasses
phi_controlcp='patchdata104/classes:analysis/data103-api.jar:tools/kotlin-stdlib.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_controlcp" -d patchdata104/controltestclasses $(rg --files patchdata104/tests/controlmock -g '*.java') patchdata104/tests/SharedControlsRegression.java patchdata104/tests/PhigrosControlsRegression.java
java -Xverify:none -cp "patchdata104/controltestclasses:$phi_controlcp" io.github.phiscript.capture.SharedControlsRegression > patchdata104/tests/SharedControlsRegression.txt
cat patchdata104/tests/SharedControlsRegression.txt

java -Xverify:none -cp "patchdata104/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosControlsRegression > patchdata104/tests/PhigrosControlsRegression.txt
