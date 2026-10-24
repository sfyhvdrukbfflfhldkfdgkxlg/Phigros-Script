#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata107/controltestclasses
phi_controlcp='patchdata107/classes:analysis/data106-api.jar:tools/kotlin-stdlib.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_controlcp" -d patchdata107/controltestclasses $(rg --files patchdata107/tests/controlmock -g '*.java') patchdata107/tests/SharedControlsRegression.java patchdata107/tests/PhigrosControlsRegression.java patchdata107/tests/PhigrosPhotoRegression.java
java -Xverify:none -cp "patchdata107/controltestclasses:$phi_controlcp" io.github.phiscript.capture.SharedControlsRegression > patchdata107/tests/SharedControlsRegression.txt
cat patchdata107/tests/SharedControlsRegression.txt

java -Xverify:none -cp "patchdata107/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosControlsRegression > patchdata107/tests/PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp "patchdata107/controltestclasses:$phi_controlcp" io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata107/tests/PhigrosPhotoRegression.txt
else
 printf 'SKIP original user photo fixtures are not present; provide the two JPEG paths to PhigrosPhotoRegression.\n' > patchdata107/tests/PhigrosPhotoRegression.txt
fi
