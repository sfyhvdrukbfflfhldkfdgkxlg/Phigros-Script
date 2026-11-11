#!/usr/bin/env bash
set -euo pipefail
mkdir -p r13/host
bt=tools/build-tools/android-15
d2j=tools/dex2jar/dex-tools-v2.4
asm="$d2j/lib/asm-9.5.jar"
main=deliverables/PhigrosScript_data1.0.9-r15.apk
gesture=deliverables/GestureCapture_1.0.apk
distro=/lib64/ld-linux-x86-64.so.2
"$distro" --library-path "$bt/lib64" "$bt/aapt" dump xmltree "$main" AndroidManifest.xml > r13/manifest.log
"$distro" --library-path "$bt/lib64" "$bt/aapt" dump badging "$gesture" > r13/gesture-badging.log
"$distro" --library-path "$bt/lib64" "$bt/zipalign" -c -P 16 4 "$main"
"$distro" --library-path "$bt/lib64" "$bt/zipalign" -c 4 "$gesture"
java -jar "$bt/lib/apksigner.jar" verify --verbose --print-certs "$gesture" > r13/recorder-signature.log
java -jar "$bt/lib/apksigner.jar" verify --verbose --print-certs upload/attachment_1413700149765945124.apk > r13/base-signature.log
JAVA_OPTS=-XX:-UsePerfData bash "$d2j/d2j-dex2jar.sh" -f "$gesture" -o r13/gesture-final.jar > r13/gesture-dex2jar.log 2>&1
java -jar tools/ecj.jar -8 -nowarn -cp "$asm:tools/apktool.jar" -d r13/host patch/tests/r13/HostFrames.java patch/tests/r13/DumpCapture.java
# dex2jar omits JVM stack maps. Supply verifier metadata without replacing DEX-derived model instructions.
java -cp "r13/host:$asm:tools/json.jar" HostFrames r13/gesture-final.jar r13/gesture-vm
java -jar tools/ecj.jar -8 -nowarn -cp r13/gesture-vm:tools/json.jar -d r13/host patch/gesture-recorder/tests/TraceTest.java
java -cp r13/gesture-vm:r13/host:tools/json.jar io.github.phiscript.gesturecapture.TraceTest > r13/gesture-apk-test.log
java -cp r13/host:tools/apktool.jar DumpCapture "$main" r13/smali
python3 patch/tests/r13/audit_packages.py > r13/apk-contract.log
