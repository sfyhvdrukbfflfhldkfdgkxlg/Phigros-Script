#!/usr/bin/env bash
set -euo pipefail
python3 - <<'PY_CLEAN'
from pathlib import Path
for directory in ['patch/classes','patch/dex']:
    for item in Path(directory).rglob('*'):
        if item.is_file() and item.suffix in ['.class','.dex']:item.unlink()
PY_CLEAN
mkdir -p patch/classes patch/dex patch/toolclasses deliverables
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -cp tools/apktool.jar -d patch/toolclasses patch/Assemble.java
java -XX:-UsePerfData -jar tools/ecj.jar -8 -nowarn -encoding UTF-8 -cp analysis/original.jar:tools/platform/android-35/android.jar:tools/kotlin.jar -d patch/classes $(rg --files patch/src -g '*.java')
java -XX:-UsePerfData --add-modules jdk.jartool sun.tools.jar.Main cf patch/classes.jar -C patch/classes .
java -XX:-UsePerfData -cp tools/build-tools/android-15/lib/d8.jar com.android.tools.r8.D8 --min-api 26 --lib tools/platform/android-35/android.jar --classpath analysis/original.jar --classpath tools/kotlin.jar --output patch/dex patch/classes.jar
python3 patch/patch_smali.py
python3 patch/restore_shared_dependencies.py
for n in 4 5 6 7 8 11; do
  java -XX:-UsePerfData -Xmx1536m -cp patch/toolclasses:tools/apktool.jar Assemble "patch/smali/$n" "patch/dex/classes$n.dex"
done
python3 patch/package_apk.py
chmod +x tools/build-tools/android-15/zipalign
/lib64/ld-linux-x86-64.so.2 --library-path tools/build-tools/android-15/lib64 tools/build-tools/android-15/zipalign -P 16 -f 4 analysis/patched-unsigned.apk analysis/patched-aligned.apk
java -XX:-UsePerfData -jar tools/build-tools/android-15/lib/apksigner.jar sign --key patch/signing/testkey.pk8 --cert patch/signing/testkey.x509.pem --out deliverables/PhigrosScript_data1.0.9-r14.apk analysis/patched-aligned.apk
java -XX:-UsePerfData -jar tools/build-tools/android-15/lib/apksigner.jar verify --verbose --print-certs deliverables/PhigrosScript_data1.0.9-r14.apk > analysis/signature.txt
/lib64/ld-linux-x86-64.so.2 --library-path tools/build-tools/android-15/lib64 tools/build-tools/android-15/zipalign -c -P 16 4 deliverables/PhigrosScript_data1.0.9-r14.apk
JAVA_OPTS=-XX:-UsePerfData bash tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh -f deliverables/PhigrosScript_data1.0.9-r14.apk -o analysis/patched.jar > analysis/patched-dex2jar.log 2>&1

python3 patch/tests/verify_dependencies.py upload/attachment_1413700149765945124.apk deliverables/PhigrosScript_data1.0.9-r14.apk > analysis/r14-dependencies.json

python3 patch/tests/verify_apk.py > analysis/r14-structural-test-output.json
