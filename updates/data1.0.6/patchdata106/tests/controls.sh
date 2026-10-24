#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata106/controltestclasses
phi_controlcp='patchdata106/classes:analysis/data105-api.jar:tools/kotlin-stdlib.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_controlcp" -d patchdata106/controltestclasses $(rg --files patchdata106/tests/controlmock -g '*.java') patchdata106/tests/SharedControlsRegression.java patchdata106/tests/PhigrosControlsRegression.java patchdata106/tests/PhigrosPhotoRegression.java
java -Xverify:none -cp "patchdata106/controltestclasses:$phi_controlcp" io.github.phiscript.capture.SharedControlsRegression > patchdata106/tests/SharedControlsRegression.txt
cat patchdata106/tests/SharedControlsRegression.txt

java -Xverify:none -cp "patchdata106/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosControlsRegression > patchdata106/tests/PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp "patchdata106/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata106/tests/PhigrosPhotoRegression.txt
else
 printf 'SKIP original user photo fixtures are not present; provide the two JPEG paths to PhigrosPhotoRegression.\n' > patchdata106/tests/PhigrosPhotoRegression.txt
fi
