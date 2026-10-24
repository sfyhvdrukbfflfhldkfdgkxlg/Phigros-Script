#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata108/controltestclasses
phi_controlcp='patchdata108/classes:analysis/data107-api.jar:tools/kotlin-stdlib.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_controlcp" -d patchdata108/controltestclasses $(rg --files patchdata108/tests/controlmock -g '*.java') patchdata108/tests/SharedControlsRegression.java patchdata108/tests/PhigrosControlsRegression.java patchdata108/tests/PhigrosPhotoRegression.java
java -Xverify:none -cp "patchdata108/controltestclasses:$phi_controlcp" io.github.phiscript.capture.SharedControlsRegression > patchdata108/tests/SharedControlsRegression.txt
cat patchdata108/tests/SharedControlsRegression.txt

java -Xverify:none -cp "patchdata108/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosControlsRegression > patchdata108/tests/PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp "patchdata108/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata108/tests/PhigrosPhotoRegression.txt
else
 printf 'SKIP original user photo fixtures are not present; provide the two JPEG paths to PhigrosPhotoRegression.\n' > patchdata108/tests/PhigrosPhotoRegression.txt
fi
