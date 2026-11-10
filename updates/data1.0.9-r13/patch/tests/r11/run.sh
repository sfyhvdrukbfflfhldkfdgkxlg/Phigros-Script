#!/usr/bin/env bash
set -euo pipefail
mkdir -p r11/testclasses r11/testtools
r11_deps="tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
r11_cp="r11/testclasses:r6/models.jar:analysis/patched.jar:$r11_deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r11_cp" -d r11/testclasses patch/tests/r11/RankMatchingRegression.java patch/tests/r11/MatcherRulesRegression.java
java -XX:-UsePerfData -Xmx1600m -cp "$r11_cp" io.github.phiscript.assets.RankMatchingRegression > r11/rank-matching.log
java -XX:-UsePerfData -Xmx1600m -cp "$r11_cp" io.github.phiscript.vision.MatcherRulesRegression > r11/matcher-rules.log
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -cp tools/apktool.jar -d r11/testtools patch/tests/r11/DumpScopeClasses.java
java -XX:-UsePerfData -Xmx1400m -cp r11/testtools:tools/apktool.jar DumpScopeClasses r11/current/PhigrosScript_data1.0.9-r10.apk r11/scope-baseline
java -XX:-UsePerfData -Xmx1400m -cp r11/testtools:tools/apktool.jar DumpScopeClasses deliverables/PhigrosScript_data1.0.9-r11.apk r11/scope-final
python3 patch/tests/r11/verify_scope.py > r11/scope-audit.log
printf 'PASS R11 formal chart names, strict ranks and unchanged input/session instructions\n'
