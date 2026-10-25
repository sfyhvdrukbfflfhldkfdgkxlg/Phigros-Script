#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
java -Xverify:none -Xmx1536m -cp 'patchdata109/timingtestclasses:analysis/data109-final.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.assets.TimingCorpusRegression "${1:-analysis/timing108}" > patchdata109/tests/packaged-TimingCorpusRegression.txt
