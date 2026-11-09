#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p tools/downloads tools/android tools/dex2jar current108
curl -fL --retry 2 -o tools/ecj.jar https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar
curl -fL --retry 2 -o tools/kotlin-stdlib.jar https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar
curl -fL --retry 2 -o tools/json.jar https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar
curl -fL --retry 2 -o tools/apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.11.1/apktool_2.11.1.jar
curl -fL --retry 2 -o tools/downloads/dex-tools.zip https://github.com/pxb1988/dex2jar/releases/download/v2.4/dex-tools-v2.4.zip
curl -fL --retry 2 -o tools/downloads/platform.zip https://dl.google.com/android/repository/platform-35_r02.zip
curl -fL --retry 2 -o tools/downloads/build-tools.zip https://dl.google.com/android/repository/build-tools_r35_linux.zip
curl -fL --retry 2 -o current108/PhigrosScript_data1.0.8.apk https://github.com/sfyhvdrukbfflfhldkfdgkxlg/Phigros-Script/releases/download/data1.0.8/PhigrosScript_data1.0.8.apk
sha256sum -c <<'SUMS'
01f5a92ac19bb2b3bf85e295a68f2c73c264369109158b566ce9b490af982948  tools/ecj.jar
f31cc53f105a7e48c093683bbd5437561d1233920513774b470805641bedbc09  tools/kotlin-stdlib.jar
3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed  tools/json.jar
56d59c524fc764263ba8d345754d8daf55b1887818b15cd3b594f555d249e2db  tools/apktool.jar
ee7c45eb3c1d2474a6145d8d447e651a736a22d9664b6d3d3be5a5a817dda23a  tools/downloads/dex-tools.zip
0988cacad01b38a18a47bac14a0695f246bc76c1b06c0eeb8eb0dc825ab0c8e0  tools/downloads/platform.zip
bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88  tools/downloads/build-tools.zip
284bd64193d0c029937378cf68ecc68f6ab5c81a138d50ed5458d57caa82e346  current108/PhigrosScript_data1.0.8.apk
SUMS
unzip -oq tools/downloads/dex-tools.zip -d tools/dex2jar
unzip -oq tools/downloads/platform.zip -d tools/downloads/platform
unzip -oq tools/downloads/build-tools.zip -d tools/downloads/build-tools
cp tools/downloads/platform/android-35/android.jar tools/android/android.jar
cp tools/downloads/build-tools/android-15/lib/d8.jar tools/android/d8.jar
cp tools/downloads/build-tools/android-15/lib/apksigner.jar tools/android/apksigner.jar
cp tools/downloads/build-tools/android-15/aapt2 tools/downloads/build-tools/android-15/zipalign tools/android/
cp tools/downloads/build-tools/android-15/lib64/libc++.so tools/android/
chmod +x tools/android/aapt2 tools/android/zipalign tools/dex2jar/dex-tools-v2.4/*.sh
