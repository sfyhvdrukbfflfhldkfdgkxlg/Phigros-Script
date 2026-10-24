#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
java -Xverify:none -Xmx1536m -cp 'patchdata108/timingtestclasses:analysis/data108-final.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.assets.TimingCorpusRegression "${1:-analysis/timing108}" > patchdata108/tests/packaged-TimingCorpusRegression.txt
