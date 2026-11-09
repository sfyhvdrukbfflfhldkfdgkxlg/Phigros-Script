#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
phi_tools="${PHI_TOOLS_DIR:-../tools}"
mkdir -p build/test-classes
java -jar "$phi_tools/ecj.jar" -8 -nowarn -encoding UTF-8 -d build/test-classes src/io/github/phiscript/gesturerecorder/GestureRecording.java tests/GestureRecordingTest.java
java -cp build/test-classes io.github.phiscript.gesturerecorder.GestureRecordingTest > build/test-recording.json
python3 - <<'PY'
import json
from pathlib import Path
p = json.loads(Path('build/test-recording.json').read_text())
assert p['schema'] == 'io.github.phiscript.gesture-recording' and p['version'] == 1
assert p['name'] == '拖动"\\\n\t\x01😀'
assert len(p['frames']) == 8
assert p['frames'][0]['tMs'] == 10
assert p['frames'][-1]['action'] == 'CANCEL'
assert p['frames'][2]['historical'] is True
assert p['frames'][-1]['pointers'][0]['nx'] == -.1
assert p['frames'][-1]['pointers'][0]['ny'] == 1.1
p['name'] = '双指拖动（含停止取消）'
Path('examples/multi-touch.json').write_text(json.dumps(p, ensure_ascii=False, indent=2) + '\n')
print('PASS: exported JSON parses and preserves exact metadata/coordinates/name')
PY
