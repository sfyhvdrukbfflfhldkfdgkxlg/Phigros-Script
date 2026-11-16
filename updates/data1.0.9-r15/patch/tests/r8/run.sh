#!/usr/bin/env bash
set -euo pipefail
r8_apk="${1:-deliverables/PhigrosScript_data1.0.9-r15.apk}"
mkdir -p r8/sessionmodels r8/testclasses r8/testtools
r8_deps="tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
r8_build_cp="r6/models.jar:analysis/patched.jar:$r8_deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r8_build_cp" -d r8/sessionmodels $(rg --files patch/tests/r8/mock -g '*.java')
r8_cp="r8/testclasses:r8/sessionmodels:r6/models.jar:analysis/patched.jar:$r8_deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r8_cp" -d r8/testclasses patch/tests/r8/SharedSessionRegression.java
java -XX:-UsePerfData -Xmx1600m -cp "$r8_cp" io.github.phiscript.capture.SharedSessionRegression > r8/shared-session.log
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp tools/apktool.jar -d r8/testtools patch/tests/r8/DumpSession.java
java -XX:-UsePerfData -cp r8/testtools:tools/apktool.jar DumpSession "$r8_apk" r8/current-session.smali
python3 patch/tests/r8/verify_session_parity.py > r8/parity.log
printf 'PASS R8 shared Phira session checks\n'
