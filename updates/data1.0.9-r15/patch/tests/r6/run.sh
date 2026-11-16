#!/usr/bin/env bash
set -euo pipefail
mkdir -p r6/models r6/testclasses r6/coremodels
r6_deps="tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "analysis/patched.jar:$r6_deps" -d r6/models $(rg --files patch/tests/mock -g '*.java' | rg -v '/AppSettings.java$')
java -XX:-UsePerfData --add-modules jdk.jartool sun.tools.jar.Main cf r6/models.jar -C r6/models .
r6_cp="r6/testclasses:r6/models.jar:analysis/patched.jar:$r6_deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r6_cp" -d r6/coremodels patch/tests/mock/io/github/phiscript/AppSettings.java
r6_core_cp="r6/testclasses:r6/coremodels:r6/models.jar:analysis/patched.jar:$r6_deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r6_core_cp" -d r6/testclasses patch/tests/src/Regression.java patch/tests/src/CoreTest.java patch/tests/r3/RetryTimingRegression.java
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r6_cp" -d r6/testclasses \
 patch/tests/r6/NativeProjectionRegression.java patch/tests/r6/PreparedPlaybackRegression.java patch/tests/r6/RpeLayerRegression.java patch/tests/r6/ScriptCorpusRegression.java \
 patch/tests/r4/ModeRoutingRegression.java patch/tests/r3/ImportLifecycleRegression.java patch/tests/src/ExternalTest.java patch/tests/r4/NoiseIndexRegression.java
java -XX:-UsePerfData -Xmx1600m -cp "$r6_cp" io.github.phiscript.engine.NativeProjectionRegression > r6/after-projection.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_cp" io.github.phiscript.input.PreparedPlaybackRegression > r6/after-preparation.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_cp" io.github.phiscript.assets.RpeLayerRegression > r6/rpe-layers.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_core_cp" io.github.phiscript.input.CoreTest > r6/core.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_cp" io.github.phiscript.assets.ModeRoutingRegression > r6/routing.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_cp" io.github.phiscript.assets.ImportLifecycleRegression > r6/import-lifecycle.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_core_cp" io.github.phiscript.capture.RetryTimingRegression > r6/retry.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_cp" io.github.phiscript.assets.ExternalTest > r6/import-transport.log
java -XX:-UsePerfData -Xmx1600m -cp "$r6_cp" NoiseIndexRegression > r6/noise-index.log
python3 patch/tests/r6/verify_shared_flow.py > r6/shared-wiring.log
python3 patch/tests/r4/verify_mode_wiring.py > r6/mode-wiring.log
python3 patch/tests/r3/verify_calibration_flow.py > r6/calibration.log
if [ "${1:-}" = "--corpus" ]; then
 java -XX:-UsePerfData -Xmx2300m -cp "$r6_cp" io.github.phiscript.assets.ScriptCorpusRegression > r6/corpus.log
fi
printf 'PASS R6 regressions\n'
