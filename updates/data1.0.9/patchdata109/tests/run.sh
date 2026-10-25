#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata109/testclasses patchdata109/rootsimclasses patchdata109/autotestclasses patchdata109/hosttestclasses
find patchdata109/tests -maxdepth 1 -type f -name '*.txt' -not -name 'signature.txt' -delete
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data108-api.jar:tools/kotlin-stdlib.jar' -d patchdata109/testclasses $(rg --files patch/tests/mocksrc patch/tests/aospsrc -g '*.java' | rg -v '(input/TouchService|android/content/Context|android/util/DisplayMetrics|android/view/WindowMetrics)\.java$') $(rg --files patchdata109/testsupport -g '*.java')
phi_tests='patchdata109/testclasses:patchdata109/classes:analysis/data108-api.jar:tools/kotlin-stdlib.jar:tools/json.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata109/testclasses patch/tests/Regression.java patch/tests/ProtocolRegression.java patch/tests/SystemInputRegression.java patch/tests/RealChartsRegression.java patch/tests/ImportPackagesRegression.java patch/tests/LargeChartRegression.java patch/tests/RootStateRegression.java patch/tests/RpeCompatibilityRegression.java patchdata109/tests/AutoStateRegression.java patchdata109/tests/GesturePlanRegression.java patchdata109/tests/PhiraRootPipeRegression.java patchdata109/tests/StopPolicyRegression.java patchdata109/tests/NativeParserCancelRegression.java
for check in io.github.phiscript.input.Regression io.github.phiscript.input.ProtocolRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.input.RealChartsRegression io.github.phiscript.input.ImportPackagesRegression io.github.phiscript.assets.LargeChartRegression io.github.phiscript.root.RootStateRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.capture.StopPolicyRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_tests" "$check" > "patchdata109/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata109/rootsimclasses $(rg --files patchdata109/tests/rootsimsrc -g '*.java') patch/tests/RootPlayerRegression.java patch/tests/RootDaemonRegression.java patchdata109/tests/RootWarmupRegression.java patchdata109/tests/ConnectedPlaybackRegression.java patchdata109/tests/TouchSafetyRegression.java patchdata109/tests/Data104Regression.java patchdata109/tests/Data105Regression.java patchdata109/tests/Data106Regression.java patchdata109/tests/Data107Regression.java patchdata109/tests/Data109Regression.java patchdata109/tests/PhigrosClockRegression.java
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.input.Data104Regression io.github.phiscript.input.Data105Regression io.github.phiscript.input.Data106Regression io.github.phiscript.input.Data107Regression io.github.phiscript.input.Data109Regression io.github.phiscript.capture.PhigrosClockRegression; do
 java -Xverify:none -cp "patchdata109/rootsimclasses:$phi_tests" "$check" > "patchdata109/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data108-api.jar:tools/kotlin-stdlib.jar' -d patchdata109/autotestclasses $(rg --files patchdata109/tests/automock -g '*.java') patchdata109/src/io/github/phiscript/capture/AutoEvidence.java patchdata109/src/io/github/phiscript/capture/PhigrosStartGate.java patchdata109/src/io/github/phiscript/capture/PhigrosAutoEngine.java patchdata109/src/io/github/phiscript/input/ChartPlaybackClock.java patchdata109/src/io/github/phiscript/root/InputBackend.java patchdata109/tests/AutoEngineRegression.java
timeout 70s java -Xverify:none -cp 'patchdata109/autotestclasses:analysis/data108-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata109/tests/AutoEngineRegression.txt
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp tools/android/android.jar -d patchdata109/hosttestclasses $(rg --files patchdata109/tests/hostmock -g '*.java') patchdata109/src/io/github/phiscript/input/ForegroundEvents.java patchdata109/src/io/github/phiscript/capture/OverlayHost.java patchdata109/tests/ForegroundHostRegression.java
java -cp patchdata109/hosttestclasses io.github.phiscript.capture.ForegroundHostRegression > patchdata109/tests/ForegroundHostRegression.txt
bash patchdata109/tests/controls.sh
bash patchdata109/tests/overlay.sh
bash patchdata109/tests/router.sh
bash patchdata109/tests/incremental.sh
bash patchdata109/tests/timing.sh
printf 'Host chart/input, connected gestures, edge safety, Root import, auto flow and overlay regressions passed.\n'
