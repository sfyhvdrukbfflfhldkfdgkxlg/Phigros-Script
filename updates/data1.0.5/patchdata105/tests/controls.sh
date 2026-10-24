#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata105/controltestclasses
phi_controlcp='patchdata105/classes:analysis/data104-api.jar:tools/kotlin-stdlib.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_controlcp" -d patchdata105/controltestclasses $(rg --files patchdata105/tests/controlmock -g '*.java') patchdata105/tests/SharedControlsRegression.java patchdata105/tests/PhigrosControlsRegression.java patchdata105/tests/PhigrosPhotoRegression.java
java -Xverify:none -cp "patchdata105/controltestclasses:$phi_controlcp" io.github.phiscript.capture.SharedControlsRegression > patchdata105/tests/SharedControlsRegression.txt
cat patchdata105/tests/SharedControlsRegression.txt

java -Xverify:none -cp "patchdata105/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosControlsRegression > patchdata105/tests/PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp "patchdata105/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata105/tests/PhigrosPhotoRegression.txt
else
 printf 'SKIP original user photo fixtures are not present; provide the two JPEG paths to PhigrosPhotoRegression.\n' > patchdata105/tests/PhigrosPhotoRegression.txt
fi
