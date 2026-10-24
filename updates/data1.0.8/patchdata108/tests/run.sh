#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata108/testclasses patchdata108/rootsimclasses patchdata108/autotestclasses patchdata108/hosttestclasses
find patchdata108/tests -maxdepth 1 -type f -name '*.txt' -not -name 'signature.txt' -delete
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data107-api.jar:tools/kotlin-stdlib.jar' -d patchdata108/testclasses $(rg --files patch/tests/mocksrc patch/tests/aospsrc -g '*.java' | rg -v '(input/TouchService|android/content/Context|android/util/DisplayMetrics|android/view/WindowMetrics)\.java$') $(rg --files patchdata108/testsupport -g '*.java')
phi_tests='patchdata108/testclasses:patchdata108/classes:analysis/data107-api.jar:tools/kotlin-stdlib.jar:tools/json.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata108/testclasses patch/tests/Regression.java patch/tests/ProtocolRegression.java patch/tests/SystemInputRegression.java patch/tests/RealChartsRegression.java patch/tests/ImportPackagesRegression.java patch/tests/LargeChartRegression.java patch/tests/RootStateRegression.java patch/tests/RpeCompatibilityRegression.java patchdata108/tests/AutoStateRegression.java patchdata108/tests/GesturePlanRegression.java patchdata108/tests/PhiraRootPipeRegression.java patchdata108/tests/StopPolicyRegression.java patchdata108/tests/NativeParserCancelRegression.java
for check in io.github.phiscript.input.Regression io.github.phiscript.input.ProtocolRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.input.RealChartsRegression io.github.phiscript.input.ImportPackagesRegression io.github.phiscript.assets.LargeChartRegression io.github.phiscript.root.RootStateRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.capture.StopPolicyRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_tests" "$check" > "patchdata108/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata108/rootsimclasses $(rg --files patchdata108/tests/rootsimsrc -g '*.java') patch/tests/RootPlayerRegression.java patch/tests/RootDaemonRegression.java patchdata108/tests/RootWarmupRegression.java patchdata108/tests/ConnectedPlaybackRegression.java patchdata108/tests/TouchSafetyRegression.java patchdata108/tests/Data104Regression.java patchdata108/tests/Data105Regression.java patchdata108/tests/Data106Regression.java patchdata108/tests/Data107Regression.java patchdata108/tests/PhigrosClockRegression.java
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.input.Data104Regression io.github.phiscript.input.Data105Regression io.github.phiscript.input.Data106Regression io.github.phiscript.input.Data107Regression io.github.phiscript.capture.PhigrosClockRegression; do
 java -Xverify:none -cp "patchdata108/rootsimclasses:$phi_tests" "$check" > "patchdata108/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data107-api.jar:tools/kotlin-stdlib.jar' -d patchdata108/autotestclasses $(rg --files patchdata108/tests/automock -g '*.java') patchdata108/src/io/github/phiscript/capture/AutoEvidence.java patchdata108/src/io/github/phiscript/capture/PhigrosStartGate.java patchdata108/src/io/github/phiscript/capture/PhigrosAutoEngine.java patchdata108/src/io/github/phiscript/input/ChartPlaybackClock.java patchdata108/src/io/github/phiscript/root/InputBackend.java patchdata108/tests/AutoEngineRegression.java
timeout 70s java -Xverify:none -cp 'patchdata108/autotestclasses:analysis/data107-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata108/tests/AutoEngineRegression.txt
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp tools/android/android.jar -d patchdata108/hosttestclasses $(rg --files patchdata108/tests/hostmock -g '*.java') patchdata108/src/io/github/phiscript/input/ForegroundEvents.java patchdata108/src/io/github/phiscript/capture/OverlayHost.java patchdata108/tests/ForegroundHostRegression.java
java -cp patchdata108/hosttestclasses io.github.phiscript.capture.ForegroundHostRegression > patchdata108/tests/ForegroundHostRegression.txt
bash patchdata108/tests/controls.sh
bash patchdata108/tests/overlay.sh
bash patchdata108/tests/router.sh
bash patchdata108/tests/incremental.sh
bash patchdata108/tests/timing.sh
printf 'Host chart/input, connected gestures, edge safety, Root import, auto flow and overlay regressions passed.\n'
