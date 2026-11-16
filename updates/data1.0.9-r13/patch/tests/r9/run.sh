#!/usr/bin/env bash
set -euo pipefail
mkdir -p r9/pixelmodels r9/pixeltests
r9_deps="tools/kotlin.jar:tools/json.jar:tools/platform/android-35/android.jar"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "r6/models.jar:analysis/patched.jar:$r9_deps" -d r9/pixelmodels patch/tests/r9/mock/android/graphics/Bitmap.java
r9_cp="r9/pixeltests:r9/pixelmodels:r6/models.jar:analysis/patched.jar:$r9_deps"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$r9_cp" -d r9/pixeltests patch/tests/r9/MenuPixelRegression.java
java -XX:-UsePerfData -Djava.awt.headless=true -Xmx1600m -cp "$r9_cp" io.github.phiscript.capture.MenuPixelRegression > r9/menu-pixels.log
printf 'PASS R9 real-pixel menu checks\n'
