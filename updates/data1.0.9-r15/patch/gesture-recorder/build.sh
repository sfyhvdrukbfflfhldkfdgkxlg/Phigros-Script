#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
src=patch/gesture-recorder
out=r13/gesture-build
bt=tools/build-tools/android-15
sdk=tools/platform/android-35/android.jar
mkdir -p "$out/classes" "$out/dex" deliverables
chmod +x "$bt/aapt2" "$bt/zipalign"
/lib64/ld-linux-x86-64.so.2 --library-path "$bt/lib64" "$bt/aapt2" link -I "$sdk" --manifest "$src/AndroidManifest.xml" -o "$out/base.apk"
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$sdk" -d "$out/classes" $(rg --files "$src/src" -g '*.java')
java --add-modules jdk.jartool sun.tools.jar.Main cf "$out/classes.jar" -C "$out/classes" .
java -XX:-UsePerfData -cp "$bt/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$sdk" --output "$out/dex" "$out/classes.jar"
python3 - "$out" <<'PY'
import zipfile,pathlib,sys
out=pathlib.Path(sys.argv[1])
with zipfile.ZipFile(out/'base.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex',zipfile.ZIP_DEFLATED)
PY
/lib64/ld-linux-x86-64.so.2 --library-path "$bt/lib64" "$bt/zipalign" -f 4 "$out/base.apk" "$out/aligned.apk"
java -jar "$bt/lib/apksigner.jar" sign --key patch/signing/testkey.pk8 --cert patch/signing/testkey.x509.pem --out deliverables/GestureCapture_1.0.apk "$out/aligned.apk"
java -jar "$bt/lib/apksigner.jar" verify --verbose --print-certs deliverables/GestureCapture_1.0.apk
/lib64/ld-linux-x86-64.so.2 --library-path "$bt/lib64" "$bt/zipalign" -c 4 deliverables/GestureCapture_1.0.apk
