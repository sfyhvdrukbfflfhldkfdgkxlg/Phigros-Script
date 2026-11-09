#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/importtestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'patchdata109/classes:analysis/data108-api.jar:tools/android/android.jar:tools/kotlin-stdlib.jar' -d patchdata109/importtestclasses $(rg --files patchdata109/tests/importmock -g '*.java') patchdata109/tests/IncrementalImportRegression.java
java -Xverify:none -cp 'patchdata109/importtestclasses:patchdata109/classes:analysis/data108-api.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.root.IncrementalImportRegression > patchdata109/tests/IncrementalImportRegression.txt
