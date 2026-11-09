#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
phi_tools="${PHI_TOOLS_DIR:-../tools}"
phi_tools=$(cd "$phi_tools" && pwd)
mkdir -p build/classes build/dex build/signing ../deliverables
rm -rf build/classes build/dex build/resources.zip
mkdir -p build/classes build/dex
java -jar "$phi_tools/ecj.jar" -8 -nowarn -encoding UTF-8 -cp "$phi_tools/android/android.jar" -d build/classes $(find src -name '*.java' -print)
java --add-modules jdk.jartool sun.tools.jar.Main cf build/classes.jar -C build/classes .
java -cp "$phi_tools/android/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$phi_tools/android/android.jar" --output build/dex build/classes.jar
"$phi_tools/android/aapt2" compile --dir res -o build/resources.zip
"$phi_tools/android/aapt2" link -o build/unsigned.apk -I "$phi_tools/android/android.jar" --manifest AndroidManifest.xml --min-sdk-version 26 --target-sdk-version 35 build/resources.zip
python3 - <<'PY'
import zipfile
with zipfile.ZipFile('build/unsigned.apk', 'a', compression=zipfile.ZIP_DEFLATED) as apk:
    apk.write('build/dex/classes.dex', 'classes.dex')
PY
LD_LIBRARY_PATH="$phi_tools/android${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" "$phi_tools/android/zipalign" -P 16 -f 4 build/unsigned.apk build/aligned.apk
# This separate app has its own local development certificate. Never use the main app's private key.
if [[ ! -f build/signing/recorder.p12 ]]; then
    python3 - <<'PY'
import secrets
from pathlib import Path
Path('build/signing/password.txt').write_text(secrets.token_urlsafe(32), encoding='utf-8')
Path('build/signing/password.txt').chmod(0o600)
PY
    keytool -genkeypair -noprompt -storetype PKCS12 -keystore build/signing/recorder.p12 -storepass:file build/signing/password.txt -keypass:file build/signing/password.txt -alias recorder -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Gesture Recorder Local Build'
    chmod 600 build/signing/recorder.p12
fi
java -cp "$phi_tools/android/apksigner.jar" com.android.apksigner.ApkSignerTool sign --ks build/signing/recorder.p12 --ks-key-alias recorder --ks-pass file:build/signing/password.txt --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out ../deliverables/GestureRecorder_1.0.0.apk build/aligned.apk
java -cp "$phi_tools/android/apksigner.jar" com.android.apksigner.ApkSignerTool verify --verbose --print-certs ../deliverables/GestureRecorder_1.0.0.apk > build/signature.txt
LD_LIBRARY_PATH="$phi_tools/android${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}" "$phi_tools/android/zipalign" -c -P 16 4 ../deliverables/GestureRecorder_1.0.0.apk
printf '%s\n' 'Built deliverables/GestureRecorder_1.0.0.apk'
