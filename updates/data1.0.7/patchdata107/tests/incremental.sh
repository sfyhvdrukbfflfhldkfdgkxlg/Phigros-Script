#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata107/importtestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'patchdata107/classes:analysis/data106-api.jar:tools/android/android.jar:tools/kotlin-stdlib.jar' -d patchdata107/importtestclasses $(rg --files patchdata107/tests/importmock -g '*.java') patchdata107/tests/IncrementalImportRegression.java
java -Xverify:none -cp 'patchdata107/importtestclasses:patchdata107/classes:analysis/data106-api.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.root.IncrementalImportRegression > patchdata107/tests/IncrementalImportRegression.txt
