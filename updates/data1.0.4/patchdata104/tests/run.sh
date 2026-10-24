#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata104/testclasses patchdata104/rootsimclasses patchdata104/autotestclasses patchdata104/hosttestclasses
find patchdata104/tests -maxdepth 1 -type f -name '*.txt' -not -name 'signature.txt' -delete
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data103-api.jar:tools/kotlin-stdlib.jar' -d patchdata104/testclasses $(rg --files patch/tests/mocksrc patch/tests/aospsrc -g '*.java' | rg -v '(input/TouchService|android/content/Context|android/util/DisplayMetrics|android/view/WindowMetrics)\.java$') $(rg --files patchdata104/testsupport -g '*.java')
phi_tests='patchdata104/testclasses:patchdata104/classes:analysis/data103-api.jar:tools/kotlin-stdlib.jar:tools/json.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata104/testclasses patch/tests/Regression.java patch/tests/ProtocolRegression.java patch/tests/SystemInputRegression.java patch/tests/RealChartsRegression.java patch/tests/ImportPackagesRegression.java patch/tests/LargeChartRegression.java patch/tests/RootStateRegression.java patch/tests/RpeCompatibilityRegression.java patchdata104/tests/AutoStateRegression.java patchdata104/tests/GesturePlanRegression.java patchdata104/tests/PhiraRootPipeRegression.java patchdata104/tests/StopPolicyRegression.java patchdata104/tests/NativeParserCancelRegression.java
for check in io.github.phiscript.input.Regression io.github.phiscript.input.ProtocolRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.input.RealChartsRegression io.github.phiscript.input.ImportPackagesRegression io.github.phiscript.assets.LargeChartRegression io.github.phiscript.root.RootStateRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.capture.StopPolicyRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_tests" "$check" > "patchdata104/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata104/rootsimclasses $(rg --files patchdata104/tests/rootsimsrc -g '*.java') patch/tests/RootPlayerRegression.java patch/tests/RootDaemonRegression.java patchdata104/tests/RootWarmupRegression.java patchdata104/tests/ConnectedPlaybackRegression.java patchdata104/tests/TouchSafetyRegression.java patchdata104/tests/Data104Regression.java
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.input.Data104Regression; do
 java -Xverify:none -cp "patchdata104/rootsimclasses:$phi_tests" "$check" > "patchdata104/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data103-api.jar:tools/kotlin-stdlib.jar' -d patchdata104/autotestclasses $(rg --files patchdata104/tests/automock -g '*.java') patchdata104/src/io/github/phiscript/capture/AutoEvidence.java patchdata104/src/io/github/phiscript/capture/PhigrosStartGate.java patchdata104/src/io/github/phiscript/capture/PhigrosAutoEngine.java patchdata104/src/io/github/phiscript/root/InputBackend.java patchdata104/tests/AutoEngineRegression.java
timeout 70s java -Xverify:none -cp 'patchdata104/autotestclasses:analysis/data103-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata104/tests/AutoEngineRegression.txt
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp tools/android/android.jar -d patchdata104/hosttestclasses $(rg --files patchdata104/tests/hostmock -g '*.java') patchdata104/src/io/github/phiscript/input/ForegroundEvents.java patchdata104/src/io/github/phiscript/capture/OverlayHost.java patchdata104/tests/ForegroundHostRegression.java
java -cp patchdata104/hosttestclasses io.github.phiscript.capture.ForegroundHostRegression > patchdata104/tests/ForegroundHostRegression.txt
bash patchdata104/tests/controls.sh
bash patchdata104/tests/overlay.sh
bash patchdata104/tests/router.sh
printf 'Host chart/input, connected gestures, edge safety, Root import, auto flow and overlay regressions passed.\n'
