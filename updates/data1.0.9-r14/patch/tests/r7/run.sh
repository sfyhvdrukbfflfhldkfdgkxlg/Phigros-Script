#!/usr/bin/env bash
set -euo pipefail
bash patch/tests/r6/run.sh
mkdir -p r7/testclasses
r7_cp="r7/testclasses:r6/models.jar:analysis/patched.jar:tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r7_cp" -d r7/testclasses patch/tests/r7/FeijipanPathRegression.java patch/tests/r7/FeijipanLiveDownload.java
java -XX:-UsePerfData -Xmx1600m -cp "$r7_cp" io.github.phiscript.assets.FeijipanPathRegression > r7/paths.log
python3 patch/tests/r7/verify_source_flow.py > r7/source-flow.log
if [ "${1:-}" = "--live" ]; then
 java -XX:-UsePerfData -Xmx1600m -cp "$r7_cp" io.github.phiscript.assets.FeijipanLiveDownload > r7/live-download.log
fi
printf 'PASS R7 Feijipan checks\n'
