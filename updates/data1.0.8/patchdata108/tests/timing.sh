#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata108/timingtestclasses
if [[ ! -f analysis/data108-final.jar || deliverables/PhigrosScript_data1.0.8.apk -nt analysis/data108-final.jar ]]; then
 bash tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh -f -o analysis/data108-final.jar deliverables/PhigrosScript_data1.0.8.apk > analysis/data108-timing-d2j.log 2>&1
fi
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'patchdata108/classes:analysis/data107-api.jar:tools/android/android.jar:tools/kotlin-stdlib.jar:tools/json.jar' -d patchdata108/timingtestclasses $(rg --files patchdata108/tests/timingmock -g '*.java') patchdata108/tests/importmock/android/util/AtomicFile.java patchdata108/tests/importmock/io/github/phiscript/AppSettings.java patchdata108/tests/importmock/io/github/phiscript/RuntimeState.java patchdata108/tests/TimingIndexRegression.java patchdata108/tests/TimingCorpusRegression.java
java -Xverify:none -cp 'patchdata108/timingtestclasses:patchdata108/classes:analysis/data108-final.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.assets.TimingIndexRegression > patchdata108/tests/TimingIndexRegression.txt
