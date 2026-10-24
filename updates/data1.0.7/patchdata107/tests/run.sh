#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata107/testclasses patchdata107/rootsimclasses patchdata107/autotestclasses patchdata107/hosttestclasses
find patchdata107/tests -maxdepth 1 -type f -name '*.txt' -not -name 'signature.txt' -delete
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data106-api.jar:tools/kotlin-stdlib.jar' -d patchdata107/testclasses $(rg --files patch/tests/mocksrc patch/tests/aospsrc -g '*.java' | rg -v '(input/TouchService|android/content/Context|android/util/DisplayMetrics|android/view/WindowMetrics)\.java$') $(rg --files patchdata107/testsupport -g '*.java')
phi_tests='patchdata107/testclasses:patchdata107/classes:analysis/data106-api.jar:tools/kotlin-stdlib.jar:tools/json.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata107/testclasses patch/tests/Regression.java patch/tests/ProtocolRegression.java patch/tests/SystemInputRegression.java patch/tests/RealChartsRegression.java patch/tests/ImportPackagesRegression.java patch/tests/LargeChartRegression.java patch/tests/RootStateRegression.java patch/tests/RpeCompatibilityRegression.java patchdata107/tests/AutoStateRegression.java patchdata107/tests/GesturePlanRegression.java patchdata107/tests/PhiraRootPipeRegression.java patchdata107/tests/StopPolicyRegression.java patchdata107/tests/NativeParserCancelRegression.java
for check in io.github.phiscript.input.Regression io.github.phiscript.input.ProtocolRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.input.RealChartsRegression io.github.phiscript.input.ImportPackagesRegression io.github.phiscript.assets.LargeChartRegression io.github.phiscript.root.RootStateRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.capture.StopPolicyRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_tests" "$check" > "patchdata107/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata107/rootsimclasses $(rg --files patchdata107/tests/rootsimsrc -g '*.java') patch/tests/RootPlayerRegression.java patch/tests/RootDaemonRegression.java patchdata107/tests/RootWarmupRegression.java patchdata107/tests/ConnectedPlaybackRegression.java patchdata107/tests/TouchSafetyRegression.java patchdata107/tests/Data104Regression.java patchdata107/tests/Data105Regression.java patchdata107/tests/Data106Regression.java patchdata107/tests/Data107Regression.java patchdata107/tests/PhigrosClockRegression.java
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.input.Data104Regression io.github.phiscript.input.Data105Regression io.github.phiscript.input.Data106Regression io.github.phiscript.input.Data107Regression io.github.phiscript.capture.PhigrosClockRegression; do
 java -Xverify:none -cp "patchdata107/rootsimclasses:$phi_tests" "$check" > "patchdata107/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data106-api.jar:tools/kotlin-stdlib.jar' -d patchdata107/autotestclasses $(rg --files patchdata107/tests/automock -g '*.java') patchdata107/src/io/github/phiscript/capture/AutoEvidence.java patchdata107/src/io/github/phiscript/capture/PhigrosStartGate.java patchdata107/src/io/github/phiscript/capture/PhigrosAutoEngine.java patchdata107/src/io/github/phiscript/input/ChartPlaybackClock.java patchdata107/src/io/github/phiscript/root/InputBackend.java patchdata107/tests/AutoEngineRegression.java
timeout 70s java -Xverify:none -cp 'patchdata107/autotestclasses:analysis/data106-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata107/tests/AutoEngineRegression.txt
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp tools/android/android.jar -d patchdata107/hosttestclasses $(rg --files patchdata107/tests/hostmock -g '*.java') patchdata107/src/io/github/phiscript/input/ForegroundEvents.java patchdata107/src/io/github/phiscript/capture/OverlayHost.java patchdata107/tests/ForegroundHostRegression.java
java -cp patchdata107/hosttestclasses io.github.phiscript.capture.ForegroundHostRegression > patchdata107/tests/ForegroundHostRegression.txt
bash patchdata107/tests/controls.sh
bash patchdata107/tests/overlay.sh
bash patchdata107/tests/router.sh
bash patchdata107/tests/incremental.sh
printf 'Host chart/input, connected gestures, edge safety, Root import, auto flow and overlay regressions passed.\n'
