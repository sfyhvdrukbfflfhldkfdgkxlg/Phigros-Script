#!/usr/bin/env bash
set -euo pipefail
r4_compile_cp=analysis/patched.jar:tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar
r4_runtime_cp=patch/tests/classes:$r4_compile_cp
mkdir -p patch/tests/classes patch/tests/routing/testclasses
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r4_compile_cp" -d patch/tests/classes $(rg --files patch/tests/mock patch/tests/src patch/tests/r3 patch/tests/r4 -g '*.java' | rg -v 'ModeRoutingRegression.java$')
python3 - <<'PY'
from pathlib import Path
import zipfile
with zipfile.ZipFile('patch/tests/routing/models.jar','w',zipfile.ZIP_DEFLATED) as archive:
    for path in Path('patch/tests/classes').rglob('*.class'):
        name=path.relative_to('patch/tests/classes').as_posix()
        if name!='io/github/phiscript/AppSettings.class':archive.write(path,name)
PY
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "patch/tests/routing/models.jar:$r4_compile_cp" -d patch/tests/routing/testclasses patch/tests/r4/ModeRoutingRegression.java
java -XX:-UsePerfData -Xmx1g -cp "patch/tests/routing/testclasses:patch/tests/routing/models.jar:$r4_compile_cp" io.github.phiscript.assets.ModeRoutingRegression
for r4_test in io.github.phiscript.assets.SwordaltSourceRegression NoiseIndexRegression io.github.phiscript.input.CoreTest io.github.phiscript.assets.ExternalTest io.github.phiscript.assets.ImportLifecycleRegression io.github.phiscript.capture.RetryTimingRegression; do
    java -XX:-UsePerfData -Xmx1g -cp "$r4_runtime_cp" "$r4_test"
done
python3 patch/tests/r4/verify_mode_wiring.py
python3 patch/tests/r4/verify_source_flow.py
python3 patch/tests/r3/verify_calibration_flow.py
if [[ "${1:-}" == --full-data ]]; then
    java -XX:-UsePerfData -Xmx1g -cp "$r4_runtime_cp" AllRawChartsRegression
    java -XX:-UsePerfData -Xmx512m -cp "$r4_runtime_cp" io.github.phiscript.assets.FullOfflinePackRegression
    python3 patch/data-tools/verify_swordalt_pack.py
fi
