#!/usr/bin/env bash
set -euo pipefail
mkdir -p r14/test
deps="r6/testclasses:r6/coremodels:r6/models.jar:analysis/patched.jar:tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$deps" -d r14/test patch/tests/r14/*.java
test_classpath="r14/test:$deps"
java -Xmx1400m -cp "$test_classpath" io.github.phiscript.input.ContactRegression
for sample in "$@"; do
    java -Xmx1700m -cp "$test_classpath" io.github.phiscript.input.ChartAudit "$sample"
    java -Xmx1700m -cp "$test_classpath" io.github.phiscript.input.ChartAudit "$sample" --random
    if [[ "$sample" == *.pez ]]; then
        java -Xmx1100m -cp "$test_classpath" io.github.phiscript.input.PerformanceReplay "$sample"
    fi
done
