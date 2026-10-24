#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
bash tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh -f -o analysis/data107-final.jar deliverables/PhigrosScript_data1.0.7.apk
phi_packed='patchdata107/rootsimclasses:patchdata107/testclasses:analysis/data107-final.jar:tools/kotlin-stdlib.jar:tools/json.jar'
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.input.Data104Regression io.github.phiscript.input.Data105Regression io.github.phiscript.input.Data106Regression io.github.phiscript.input.Data107Regression io.github.phiscript.capture.PhigrosClockRegression io.github.phiscript.root.NativeParserCancelRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_packed" "$check" > "patchdata107/tests/packaged-${check##*.}.txt"
done
python3 - <<'INNER'
import zipfile
with zipfile.ZipFile('analysis/data107-final.jar') as src:
 for target,prefixes in [('engine',('io/github/phiscript/capture/PhigrosAutoEngine','io/github/phiscript/capture/AutoEvidence','io/github/phiscript/capture/PhigrosStartGate','io/github/phiscript/input/ChartPlaybackClock')),
                         ('overlay',('io/github/phiscript/capture/AutoOverlayFeedback','io/github/phiscript/capture/AutoOverlayBounds')),
                         ('router',('io/github/phiscript/capture/AutoSessionRouter',)),
                         ('host',('io/github/phiscript/capture/OverlayHost','io/github/phiscript/input/ForegroundEvents'))]:
  with zipfile.ZipFile('analysis/data107-'+target+'-test.jar','w') as out:
   for name in src.namelist():
    if name.startswith(prefixes) and name.endswith('.class'):out.writestr(name,src.read(name))
INNER
timeout 70s java -Xverify:none -cp 'analysis/data107-engine-test.jar:patchdata107/autotestclasses:analysis/data106-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata107/tests/packaged-AutoEngineRegression.txt
java -Xverify:none -cp 'patchdata107/controltestclasses:analysis/data107-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.SharedControlsRegression > patchdata107/tests/packaged-SharedControlsRegression.txt
java -Xverify:none -cp 'analysis/data107-overlay-test.jar:patchdata107/overlaytestclasses' io.github.phiscript.capture.OverlayLifecycleRegression > patchdata107/tests/packaged-OverlayLifecycleRegression.txt
java -Xverify:none -cp 'analysis/data107-router-test.jar:patchdata107/routertestclasses' io.github.phiscript.capture.RouterFeedbackRegression > patchdata107/tests/packaged-RouterFeedbackRegression.txt
java -Xverify:none -cp 'analysis/data107-host-test.jar:patchdata107/hosttestclasses' io.github.phiscript.capture.ForegroundHostRegression > patchdata107/tests/packaged-ForegroundHostRegression.txt
java -Xverify:none -cp 'patchdata107/testclasses:analysis/data107-final.jar' io.github.phiscript.capture.StopPolicyRegression > patchdata107/tests/packaged-StopPolicyRegression.txt
printf 'Packaged DEX automatic engine, reconnect, overlay, edge safety, connected gestures, Root import and chart/input simulations passed.\n'

java -Xverify:none -cp 'patchdata107/controltestclasses:analysis/data107-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.PhigrosControlsRegression > patchdata107/tests/packaged-PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp 'patchdata107/controltestclasses:analysis/data107-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata107/tests/packaged-PhigrosPhotoRegression.txt
fi

java -Xverify:none -cp 'patchdata107/importtestclasses:analysis/data107-final.jar:tools/kotlin-stdlib.jar:tools/json.jar:tools/android/android.jar' io.github.phiscript.root.IncrementalImportRegression > patchdata107/tests/packaged-IncrementalImportRegression.txt
