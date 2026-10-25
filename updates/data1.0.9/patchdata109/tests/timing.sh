#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/timingtestclasses
if [[ ! -f analysis/data109-final.jar || deliverables/PhigrosScript_data1.0.9.apk -nt analysis/data109-final.jar ]]; then
 bash tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh -f -o analysis/data109-final.jar deliverables/PhigrosScript_data1.0.9.apk > analysis/data109-timing-d2j.log 2>&1
fi
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'patchdata109/classes:analysis/data108-api.jar:tools/android/android.jar:tools/kotlin-stdlib.jar:tools/json.jar' -d patchdata109/timingtestclasses $(rg --files patchdata109/tests/timingmock -g '*.java') patchdata109/tests/importmock/android/util/AtomicFile.java patchdata109/tests/importmock/io/github/phiscript/AppSettings.java patchdata109/tests/importmock/io/github/phiscript/RuntimeState.java patchdata109/tests/TimingIndexRegression.java patchdata109/tests/TimingCorpusRegression.java
java -Xverify:none -cp 'patchdata109/timingtestclasses:patchdata109/classes:analysis/data109-final.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.assets.TimingIndexRegression > patchdata109/tests/TimingIndexRegression.txt
