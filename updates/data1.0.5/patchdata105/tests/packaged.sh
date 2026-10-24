#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
bash tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh -f -o analysis/data105-final.jar deliverables/PhigrosScript_data1.0.5.apk
phi_packed='patchdata105/rootsimclasses:patchdata105/testclasses:analysis/data105-final.jar:tools/kotlin-stdlib.jar:tools/json.jar'
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.input.Data104Regression io.github.phiscript.input.Data105Regression io.github.phiscript.root.NativeParserCancelRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_packed" "$check" > "patchdata105/tests/packaged-${check##*.}.txt"
done
python3 - <<'INNER'
import zipfile
with zipfile.ZipFile('analysis/data105-final.jar') as src:
 for target,prefixes in [('engine',('io/github/phiscript/capture/PhigrosAutoEngine','io/github/phiscript/capture/AutoEvidence','io/github/phiscript/capture/PhigrosStartGate')),
                         ('overlay',('io/github/phiscript/capture/AutoOverlayFeedback','io/github/phiscript/capture/AutoOverlayBounds')),
                         ('router',('io/github/phiscript/capture/AutoSessionRouter',)),
                         ('host',('io/github/phiscript/capture/OverlayHost','io/github/phiscript/input/ForegroundEvents'))]:
  with zipfile.ZipFile('analysis/data105-'+target+'-test.jar','w') as out:
   for name in src.namelist():
    if name.startswith(prefixes) and name.endswith('.class'):out.writestr(name,src.read(name))
INNER
timeout 70s java -Xverify:none -cp 'analysis/data105-engine-test.jar:patchdata105/autotestclasses:analysis/data104-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata105/tests/packaged-AutoEngineRegression.txt
java -Xverify:none -cp 'patchdata105/controltestclasses:analysis/data105-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.SharedControlsRegression > patchdata105/tests/packaged-SharedControlsRegression.txt
java -Xverify:none -cp 'analysis/data105-overlay-test.jar:patchdata105/overlaytestclasses' io.github.phiscript.capture.OverlayLifecycleRegression > patchdata105/tests/packaged-OverlayLifecycleRegression.txt
java -Xverify:none -cp 'analysis/data105-router-test.jar:patchdata105/routertestclasses' io.github.phiscript.capture.RouterFeedbackRegression > patchdata105/tests/packaged-RouterFeedbackRegression.txt
java -Xverify:none -cp 'analysis/data105-host-test.jar:patchdata105/hosttestclasses' io.github.phiscript.capture.ForegroundHostRegression > patchdata105/tests/packaged-ForegroundHostRegression.txt
java -Xverify:none -cp 'patchdata105/testclasses:analysis/data105-final.jar' io.github.phiscript.capture.StopPolicyRegression > patchdata105/tests/packaged-StopPolicyRegression.txt
printf 'Packaged DEX automatic engine, reconnect, overlay, edge safety, connected gestures, Root import and chart/input simulations passed.\n'

java -Xverify:none -cp 'patchdata105/controltestclasses:analysis/data105-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.PhigrosControlsRegression > patchdata105/tests/packaged-PhigrosControlsRegression.txt

if [[ -f upload/01-1000731778.jpg && -f upload/02-1000731452.jpg ]]; then
 java -Xverify:none -cp 'patchdata105/controltestclasses:analysis/data105-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.PhigrosPhotoRegression upload/01-1000731778.jpg upload/02-1000731452.jpg > patchdata105/tests/packaged-PhigrosPhotoRegression.txt
fi
