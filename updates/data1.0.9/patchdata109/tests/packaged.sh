#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
bash tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh -f -o analysis/data109-packaged.jar deliverables/PhigrosScript_data1.0.9.apk
phi_packed='patchdata109/rootsimclasses:patchdata109/testclasses:analysis/data109-packaged.jar:tools/kotlin-stdlib.jar:tools/json.jar'
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.input.Data104Regression io.github.phiscript.input.Data105Regression io.github.phiscript.input.Data106Regression io.github.phiscript.input.Data107Regression io.github.phiscript.input.Data109Regression io.github.phiscript.capture.PhigrosClockRegression io.github.phiscript.root.NativeParserCancelRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_packed" "$check" > "patchdata109/tests/packaged-${check##*.}.txt"
done
python3 - <<'INNER'
import zipfile
with zipfile.ZipFile('analysis/data109-packaged.jar') as src:
 for target,prefixes in [('engine',('io/github/phiscript/capture/PhigrosAutoEngine','io/github/phiscript/capture/AutoEvidence','io/github/phiscript/capture/PhigrosStartGate','io/github/phiscript/input/ChartPlaybackClock')),
                         ('overlay',('io/github/phiscript/capture/AutoOverlayFeedback','io/github/phiscript/capture/AutoOverlayBounds')),
                         ('router',('io/github/phiscript/capture/AutoSessionRouter',)),
                         ('host',('io/github/phiscript/capture/OverlayHost','io/github/phiscript/input/ForegroundEvents'))]:
  with zipfile.ZipFile('analysis/data109-'+target+'-test.jar','w') as out:
   for name in src.namelist():
    if name.startswith(prefixes) and name.endswith('.class'):out.writestr(name,src.read(name))
INNER
timeout 70s java -Xverify:none -cp 'analysis/data109-engine-test.jar:patchdata109/autotestclasses:analysis/data108-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata109/tests/packaged-AutoEngineRegression.txt
java -Xverify:none -cp 'patchdata109/controltestclasses:analysis/data109-packaged.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.SharedControlsRegression > patchdata109/tests/packaged-SharedControlsRegression.txt
java -Xverify:none -cp 'analysis/data109-overlay-test.jar:patchdata109/overlaytestclasses' io.github.phiscript.capture.OverlayLifecycleRegression > patchdata109/tests/packaged-OverlayLifecycleRegression.txt
java -Xverify:none -cp 'analysis/data109-router-test.jar:patchdata109/routertestclasses' io.github.phiscript.capture.RouterFeedbackRegression > patchdata109/tests/packaged-RouterFeedbackRegression.txt
java -Xverify:none -cp 'analysis/data109-host-test.jar:patchdata109/hosttestclasses' io.github.phiscript.capture.ForegroundHostRegression > patchdata109/tests/packaged-ForegroundHostRegression.txt
java -Xverify:none -cp 'patchdata109/testclasses:analysis/data109-packaged.jar' io.github.phiscript.capture.StopPolicyRegression > patchdata109/tests/packaged-StopPolicyRegression.txt
printf 'Packaged DEX automatic engine, reconnect, overlay, edge safety, connected gestures, Root import and chart/input simulations passed.\n'

java -Xverify:none -cp 'patchdata109/controltestclasses:analysis/data109-packaged.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.PhigrosControlsRegression > patchdata109/tests/packaged-PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp 'patchdata109/controltestclasses:analysis/data109-packaged.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata109/tests/packaged-PhigrosPhotoRegression.txt
fi

java -Xverify:none -cp 'patchdata109/importtestclasses:analysis/data109-packaged.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.root.IncrementalImportRegression > patchdata109/tests/packaged-IncrementalImportRegression.txt

java -Xverify:none -cp 'patchdata109/timingtestclasses:analysis/data109-packaged.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.assets.TimingIndexRegression > patchdata109/tests/packaged-TimingIndexRegression.txt
