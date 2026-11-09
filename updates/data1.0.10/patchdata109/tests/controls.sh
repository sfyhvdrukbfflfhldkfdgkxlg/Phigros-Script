#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/controltestclasses
phi_controlcp='patchdata109/classes:analysis/data108-api.jar:tools/kotlin-stdlib.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_controlcp" -d patchdata109/controltestclasses $(rg --files patchdata109/tests/controlmock -g '*.java') patchdata109/tests/SharedControlsRegression.java patchdata109/tests/PhigrosControlsRegression.java patchdata109/tests/PhigrosPhotoRegression.java
java -Xverify:none -cp "patchdata109/controltestclasses:$phi_controlcp" io.github.phiscript.capture.SharedControlsRegression > patchdata109/tests/SharedControlsRegression.txt
cat patchdata109/tests/SharedControlsRegression.txt

java -Xverify:none -cp "patchdata109/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosControlsRegression > patchdata109/tests/PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp "patchdata109/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata109/tests/PhigrosPhotoRegression.txt
else
 printf 'SKIP original user photo fixtures are not present; provide the two JPEG paths to PhigrosPhotoRegression.\n' > patchdata109/tests/PhigrosPhotoRegression.txt
fi
