#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata108/importtestclasses
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'patchdata108/classes:analysis/data107-api.jar:tools/android/android.jar:tools/kotlin-stdlib.jar' -d patchdata108/importtestclasses $(rg --files patchdata108/tests/importmock -g '*.java') patchdata108/tests/IncrementalImportRegression.java
java -Xverify:none -cp 'patchdata108/importtestclasses:patchdata108/classes:analysis/data107-api.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.root.IncrementalImportRegression > patchdata108/tests/IncrementalImportRegression.txt
