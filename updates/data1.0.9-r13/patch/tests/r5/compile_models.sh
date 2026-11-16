#!/usr/bin/env bash
set -euo pipefail
mkdir -p patch/tests/r5/models patch/tests/r5/classes
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 \
 -cp analysis/patched.jar:tools/json.jar:tools/kotlin.jar:tools/platform/android-35/android.jar \
 -d patch/tests/r5/models $(rg --files patch/tests/mock -g '*.java' | rg -v '/AppSettings.java$')
java -XX:-UsePerfData --add-modules jdk.jartool sun.tools.jar.Main cf patch/tests/r5/models.jar -C patch/tests/r5/models .
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 \
 -cp patch/tests/r5/models.jar:analysis/patched.jar:tools/json.jar:tools/kotlin.jar:tools/platform/android-35/android.jar \
 -d patch/tests/r5/classes patch/tests/r5/LargeImportRegression.java
