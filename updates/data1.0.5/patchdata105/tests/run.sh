#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p patchdata105/testclasses patchdata105/rootsimclasses patchdata105/autotestclasses patchdata105/hosttestclasses
find patchdata105/tests -maxdepth 1 -type f -name '*.txt' -not -name 'signature.txt' -delete
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data104-api.jar:tools/kotlin-stdlib.jar' -d patchdata105/testclasses $(rg --files patch/tests/mocksrc patch/tests/aospsrc -g '*.java' | rg -v '(input/TouchService|android/content/Context|android/util/DisplayMetrics|android/view/WindowMetrics)\.java$') $(rg --files patchdata105/testsupport -g '*.java')
phi_tests='patchdata105/testclasses:patchdata105/classes:analysis/data104-api.jar:tools/kotlin-stdlib.jar:tools/json.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata105/testclasses patch/tests/Regression.java patch/tests/ProtocolRegression.java patch/tests/SystemInputRegression.java patch/tests/RealChartsRegression.java patch/tests/ImportPackagesRegression.java patch/tests/LargeChartRegression.java patch/tests/RootStateRegression.java patch/tests/RpeCompatibilityRegression.java patchdata105/tests/AutoStateRegression.java patchdata105/tests/GesturePlanRegression.java patchdata105/tests/PhiraRootPipeRegression.java patchdata105/tests/StopPolicyRegression.java patchdata105/tests/NativeParserCancelRegression.java
for check in io.github.phiscript.input.Regression io.github.phiscript.input.ProtocolRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.input.RealChartsRegression io.github.phiscript.input.ImportPackagesRegression io.github.phiscript.assets.LargeChartRegression io.github.phiscript.root.RootStateRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.capture.StopPolicyRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_tests" "$check" > "patchdata105/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_tests" -d patchdata105/rootsimclasses $(rg --files patchdata105/tests/rootsimsrc -g '*.java') patch/tests/RootPlayerRegression.java patch/tests/RootDaemonRegression.java patchdata105/tests/RootWarmupRegression.java patchdata105/tests/ConnectedPlaybackRegression.java patchdata105/tests/TouchSafetyRegression.java patchdata105/tests/Data104Regression.java patchdata105/tests/Data105Regression.java
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.input.Data104Regression io.github.phiscript.input.Data105Regression; do
 java -Xverify:none -cp "patchdata105/rootsimclasses:$phi_tests" "$check" > "patchdata105/tests/${check##*.}.txt"
done
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp 'analysis/data104-api.jar:tools/kotlin-stdlib.jar' -d patchdata105/autotestclasses $(rg --files patchdata105/tests/automock -g '*.java') patchdata105/src/io/github/phiscript/capture/AutoEvidence.java patchdata105/src/io/github/phiscript/capture/PhigrosStartGate.java patchdata105/src/io/github/phiscript/capture/PhigrosAutoEngine.java patchdata105/src/io/github/phiscript/root/InputBackend.java patchdata105/tests/AutoEngineRegression.java
timeout 70s java -Xverify:none -cp 'patchdata105/autotestclasses:analysis/data104-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata105/tests/AutoEngineRegression.txt
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp tools/android/android.jar -d patchdata105/hosttestclasses $(rg --files patchdata105/tests/hostmock -g '*.java') patchdata105/src/io/github/phiscript/input/ForegroundEvents.java patchdata105/src/io/github/phiscript/capture/OverlayHost.java patchdata105/tests/ForegroundHostRegression.java
java -cp patchdata105/hosttestclasses io.github.phiscript.capture.ForegroundHostRegression > patchdata105/tests/ForegroundHostRegression.txt
bash patchdata105/tests/controls.sh
bash patchdata105/tests/overlay.sh
bash patchdata105/tests/router.sh
printf 'Host chart/input, connected gestures, edge safety, Root import, auto flow and overlay regressions passed.\n'
