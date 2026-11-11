#!/usr/bin/env bash
set -euo pipefail
mkdir -p r15/test
deps="r6/testclasses:r6/coremodels:r6/models.jar:analysis/patched.jar:tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$deps" -d r15/test patch/tests/r14/ContactRegression.java patch/tests/r15/PointerReplayRegression.java
java -Xmx1200m -cp "r15/test:$deps" io.github.phiscript.input.PointerReplayRegression "$@"
