#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
bash tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh -f -o analysis/data104-final.jar deliverables/PhigrosScript_data1.0.4.apk
phi_packed='patchdata104/rootsimclasses:patchdata104/testclasses:analysis/data104-final.jar:tools/kotlin-stdlib.jar:tools/json.jar'
for check in io.github.phiscript.input.RootPlayerRegression io.github.phiscript.root.RootDaemonRegression io.github.phiscript.input.SystemInputRegression io.github.phiscript.assets.RpeCompatibilityRegression io.github.phiscript.root.RootWarmupRegression io.github.phiscript.capture.AutoStateRegression io.github.phiscript.input.GesturePlanRegression io.github.phiscript.input.ConnectedPlaybackRegression io.github.phiscript.input.TouchSafetyRegression io.github.phiscript.root.PhiraRootPipeRegression io.github.phiscript.input.Data104Regression io.github.phiscript.root.NativeParserCancelRegression; do
 java -Xverify:none -Xmx1024m -cp "$phi_packed" "$check" > "patchdata104/tests/packaged-${check##*.}.txt"
done
python3 - <<'INNER'
import zipfile
with zipfile.ZipFile('analysis/data104-final.jar') as src:
 for target,prefixes in [('engine',('io/github/phiscript/capture/PhigrosAutoEngine','io/github/phiscript/capture/AutoEvidence','io/github/phiscript/capture/PhigrosStartGate')),
                         ('overlay',('io/github/phiscript/capture/AutoOverlayFeedback','io/github/phiscript/capture/AutoOverlayBounds')),
                         ('router',('io/github/phiscript/capture/AutoSessionRouter',)),
                         ('host',('io/github/phiscript/capture/OverlayHost','io/github/phiscript/input/ForegroundEvents'))]:
  with zipfile.ZipFile('analysis/data104-'+target+'-test.jar','w') as out:
   for name in src.namelist():
    if name.startswith(prefixes) and name.endswith('.class'):out.writestr(name,src.read(name))
INNER
timeout 70s java -Xverify:none -cp 'analysis/data104-engine-test.jar:patchdata104/autotestclasses:analysis/data103-api.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.AutoEngineRegression > patchdata104/tests/packaged-AutoEngineRegression.txt
java -Xverify:none -cp 'patchdata104/controltestclasses:analysis/data104-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.SharedControlsRegression > patchdata104/tests/packaged-SharedControlsRegression.txt
java -Xverify:none -cp 'analysis/data104-overlay-test.jar:patchdata104/overlaytestclasses' io.github.phiscript.capture.OverlayLifecycleRegression > patchdata104/tests/packaged-OverlayLifecycleRegression.txt
java -Xverify:none -cp 'analysis/data104-router-test.jar:patchdata104/routertestclasses' io.github.phiscript.capture.RouterFeedbackRegression > patchdata104/tests/packaged-RouterFeedbackRegression.txt
java -Xverify:none -cp 'analysis/data104-host-test.jar:patchdata104/hosttestclasses' io.github.phiscript.capture.ForegroundHostRegression > patchdata104/tests/packaged-ForegroundHostRegression.txt
java -Xverify:none -cp 'patchdata104/testclasses:analysis/data104-final.jar' io.github.phiscript.capture.StopPolicyRegression > patchdata104/tests/packaged-StopPolicyRegression.txt
printf 'Packaged DEX automatic engine, reconnect, overlay, edge safety, connected gestures, Root import and chart/input simulations passed.\n'

java -Xverify:none -cp 'patchdata104/controltestclasses:analysis/data104-final.jar:tools/kotlin-stdlib.jar' io.github.phiscript.capture.PhigrosControlsRegression > patchdata104/tests/packaged-PhigrosControlsRegression.txt
