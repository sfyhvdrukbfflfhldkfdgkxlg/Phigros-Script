#!/usr/bin/env bash
set -euo pipefail
mkdir -p r13/host
sdk=tools/platform/android-35/android.jar
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "analysis/patched.jar:$sdk" -d r13/host patch/tests/r13/VideoPolicyTest.java
java -cp "r13/host:analysis/patched.jar:$sdk" io.github.phiscript.record.VideoPolicyTest > r13/video-test.log
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp tools/json.jar -d r13/host patch/gesture-recorder/src/io/github/phiscript/gesturecapture/GestureTrace.java patch/gesture-recorder/tests/TraceTest.java
java -cp "r13/host:tools/json.jar" io.github.phiscript.gesturecapture.TraceTest > r13/gesture-test.log
mkdir -p r13/media-model r13/media-check
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -d r13/media-model $(rg --files patch/tests/r13/mock -g '*.java')
model_cp="r13/media-check:r13/media-model:analysis/patched.jar:tools/platform/android-35/android.jar"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -cp "$model_cp" -d r13/media-check patch/tests/r13/CaptureRegression.java
java -cp "$model_cp" io.github.phiscript.record.CaptureRegression > r13/capture-test.log
