"""Extract unchanged AOSP event conversion methods for a host input audit.

Input: analysis/aosp-MotionEventInjector.java, downloaded from the official
frameworks/base main branch. Only type names and logging are adapted. This
audits framework output, rather than assuming an app gesture is valid because
its simultaneous finger count is valid. No audit code ships in the APK.
"""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
source = (ROOT / 'analysis/aosp-MotionEventInjector.java').read_text()
names = ['newGestureTriesToContinueOldOne', 'prepareToContinueOldGesture',
         'getMotionEventsFromGestureSteps', 'getLastTouchPoints',
         'appendMoveEventIfNeeded', 'appendUpEvents', 'appendDownEvents',
         'obtainMotionEvent', 'findPointByStrokeId', 'getUnusedPointerId']
parts = []
for name in names:
    match = re.search(r'    private [^{;]+\b' + name + r'\([^;{]*\)\s*\{', source)
    assert match, name
    at, depth = match.end(), 1
    while depth:
        if source[at] == '{': depth += 1
        elif source[at] == '}': depth -= 1
        at += 1
    part = source[match.start():at]
    part = part.replace('GestureDescription.GestureStep', 'GestureStep').replace(
        'GestureDescription.TouchPoint', 'TouchPoint')
    parts.append(part)

support = (ROOT / 'patch/tests/aosp-support.txt').read_text()
out = ROOT / 'patch/tests/aospsrc/aosp/audit/AospInputAudit.java'
out.parent.mkdir(parents=True, exist_ok=True)
out.write_text(support.replace('// INSERT_AOSP_METHODS', '\n\n'.join(parts)))
print('Generated audit using 10 AOSP MotionEventInjector methods:', out)
