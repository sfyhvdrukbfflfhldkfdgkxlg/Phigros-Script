#!/usr/bin/env bash
set -euo pipefail
bash patch/tests/r5/compile_models.sh
mkdir -p patch/tests/r5/coremodels
r5_dependencies="tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
r5_main_cp="patch/tests/r5/classes:patch/tests/r5/models.jar:analysis/patched.jar:$r5_dependencies"
r5_core_cp="patch/tests/r5/classes:patch/tests/r5/coremodels:patch/tests/r5/models.jar:analysis/patched.jar:$r5_dependencies"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r5_main_cp" -d patch/tests/r5/coremodels patch/tests/mock/io/github/phiscript/AppSettings.java
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r5_core_cp" -d patch/tests/r5/classes patch/tests/src/CoreTest.java patch/tests/src/Regression.java patch/tests/r3/RetryTimingRegression.java
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r5_main_cp" -d patch/tests/r5/classes \
 patch/tests/src/ExternalTest.java patch/tests/r3/ImportLifecycleRegression.java \
 patch/tests/r4/ModeRoutingRegression.java patch/tests/r4/NoiseIndexRegression.java patch/tests/r4/SwordaltSourceRegression.java \
 patch/tests/r5/ScriptSourceRegression.java patch/tests/r5/ScriptPackRegression.java \
 patch/data-tools/ExportSwordaltScripts.java patch/tests/r5/ScriptGeometryRegression.java
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" io.github.phiscript.assets.LargeImportRegression > analysis/r5-import-after.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" io.github.phiscript.assets.ModeRoutingRegression > analysis/r5-mode-routing.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_core_cp" io.github.phiscript.input.CoreTest > analysis/r5-core-test.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" io.github.phiscript.assets.ExternalTest > analysis/r5-external-test.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" io.github.phiscript.assets.ImportLifecycleRegression > analysis/r5-import-lifecycle.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_core_cp" io.github.phiscript.capture.RetryTimingRegression > analysis/r5-retry-test.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" NoiseIndexRegression > analysis/r5-noise-index.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" io.github.phiscript.assets.SwordaltSourceRegression > analysis/r5-swordalt-source.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" io.github.phiscript.assets.ScriptGeometryRegression > analysis/r5-script-geometry.log
java -XX:-UsePerfData -Xmx1500m -cp "$r5_main_cp" io.github.phiscript.assets.ScriptSourceRegression > analysis/r5-script-source.log
python3 patch/tests/r4/verify_mode_wiring.py > analysis/r5-mode-wiring.log
python3 patch/tests/r4/verify_source_flow.py > analysis/r5-source-flow.log
python3 patch/tests/r3/verify_calibration_flow.py > analysis/r5-calibration-flow.log
if [ "${1:-}" = "--full" ]; then
 java -XX:-UsePerfData -Xmx2300m -cp "$r5_main_cp" io.github.phiscript.assets.ScriptPackRegression > analysis/r5-script-offline.log
fi
printf 'PASS R5 selected regressions\n'
