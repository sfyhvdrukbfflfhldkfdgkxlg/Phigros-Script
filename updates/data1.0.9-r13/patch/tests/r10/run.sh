#!/usr/bin/env bash
set -euo pipefail
mkdir -p r10/testclasses r10/testtools
r10_deps="tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
r10_cp="r10/testclasses:r6/models.jar:analysis/patched.jar:$r10_deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r10_cp" -d r10/testclasses patch/tests/r10/CrossingAssignmentRegression.java patch/tests/r10/UserChartProbe.java
java -XX:-UsePerfData -Xmx1600m -cp "$r10_cp" io.github.phiscript.input.CrossingAssignmentRegression > r10/final-crossing.log
if [ -n "${1:-}" ]; then
 java -XX:-UsePerfData -Xmx1600m -cp "$r10_cp" io.github.phiscript.assets.UserChartProbe "$1" > r10/final-user-chart.log
fi
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -cp tools/apktool.jar -d r10/testtools patch/tests/r10/DumpAuditClasses.java
java -XX:-UsePerfData -Xmx1200m -cp r10/testtools:tools/apktool.jar DumpAuditClasses deliverables/PhigrosScript_data1.0.9-r13.apk r10/final-smali
python3 patch/tests/r10/verify_final_flow.py > r10/final-flow-audit.log
printf 'PASS R10 crossing and final-APK calibration audit\n'
if [ "${2:-}" = "--corpus" ]; then
 java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r10_cp" -d r10/testclasses patch/tests/r10/ChartRankRoutingRegression.java
 java -XX:-UsePerfData -Xmx2300m -cp "$r10_cp" io.github.phiscript.assets.ChartRankRoutingRegression > r10/rank-routing.log
fi
