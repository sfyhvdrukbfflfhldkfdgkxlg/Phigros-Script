#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p patchdata104/classes patchdata104/dex deliverables
phi_classpath='analysis/data103-api.jar:tools/android/android.jar:tools/kotlin-stdlib.jar'
java -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp "$phi_classpath" -d patchdata104/classes $(rg --files patchdata104/src -g '*.java')
java --add-modules jdk.jartool sun.tools.jar.Main cf patchdata104/classes.jar -C patchdata104/classes .
java -cp tools/android/d8.jar com.android.tools.r8.D8 --min-api 26 --lib tools/android/android.jar --classpath analysis/data103-api.jar --classpath tools/kotlin-stdlib.jar --output patchdata104/dex patchdata104/classes.jar
phi_build_dir=$(mktemp -d /tmp/phiscript-data104-XXXXXX)
trap 'rm -rf "$phi_build_dir"' EXIT
python3 - "$phi_build_dir" <<'PY'
import shutil,sys
shutil.copytree('analysis/data103',sys.argv[1]+'/app',ignore=shutil.ignore_patterns('.rsync-tmp','build'))
PY
java -jar tools/apktool.jar b -o analysis/data104-rebuilt.apk "$phi_build_dir/app"
python3 patchdata104/package.py
LD_LIBRARY_PATH=tools/android tools/android/zipalign -P 16 -f 4 analysis/data104-unsigned.apk analysis/data104-aligned.apk
java -cp tools/android/apksigner.jar com.android.apksigner.ApkSignerTool sign --ks "${PHI_KEYSTORE:-patch/signing/multihold.p12}" --ks-key-alias "${PHI_KEY_ALIAS:-multihold}" --ks-pass "file:${PHI_PASSWORD_FILE:-patch/signing/password.txt}" --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out deliverables/PhigrosScript_data1.0.4.apk analysis/data104-aligned.apk
mkdir -p patchdata104/tests
java -cp tools/android/apksigner.jar com.android.apksigner.ApkSignerTool verify --verbose --print-certs deliverables/PhigrosScript_data1.0.4.apk > patchdata104/tests/signature.txt
LD_LIBRARY_PATH=tools/android tools/android/zipalign -c -P 16 4 deliverables/PhigrosScript_data1.0.4.apk
