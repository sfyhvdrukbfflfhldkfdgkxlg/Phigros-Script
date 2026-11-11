#!/usr/bin/env bash
set -euo pipefail
mkdir -p r12/tests r12/media-test
bash patch/tests/r6/run.sh
deps="r6/models.jar:analysis/patched.jar:tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$deps" -d r12/tests patch/tests/r12/TimingRegression.java patch/tests/r12/SharedImportCorpus.java
java -cp "r12/tests:$deps" io.github.phiscript.TimingRegression
core="r12/tests:r6/coremodels:r6/testclasses:$deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$core" -d r12/tests patch/tests/r12/NoisePlaybackRegression.java
java -cp "$core" io.github.phiscript.input.NoisePlaybackRegression
bash patch/tests/r13/recording.sh
bash patch/tests/r8/run.sh
bash patch/tests/r9/run.sh
bash patch/tests/r10/run.sh "${R12_SAMPLE_PEZ:-}"
if [ "${1:-}" = "--corpus" ]; then
 java -Xmx2100m -cp "r12/tests:$deps" io.github.phiscript.assets.SharedImportCorpus
fi
