"""Apply the real hooks to a scratch decode and check focus guards remain exact."""
from pathlib import Path
import importlib.util
import re
import shutil
import tempfile

root = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('patch_phira', root/'patchdata109/patch_phira.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
baseline = root/'analysis/data108'
with tempfile.TemporaryDirectory(prefix='phi-phira-hooks-') as temp:
    app = Path(temp)
    paths = [next(baseline.glob('smali*/io/github/phiscript/input/TouchService.smali')),
             next(baseline.glob('smali*/io/github/phiscript/capture/PhiraSessionEngine.smali')),
             baseline/'AndroidManifest.xml']
    for source in paths:
        target = app/source.relative_to(baseline)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)
    service = app/paths[0].relative_to(baseline)
    before = service.read_text()
    module.patch(app)
    first = [p.read_bytes() for p in app.rglob('*') if p.is_file()]
    module.patch(app)
    assert first == [p.read_bytes() for p in app.rglob('*') if p.is_file()], 'hooks must be idempotent'
    after = service.read_text()
    for signature in ['getGameForeground()Z', 'onAccessibilityEvent(Landroid/view/accessibility/AccessibilityEvent;)V']:
        def method(source):
            return re.search(r'\.method[^\n]* '+re.escape(signature)+r'\n.*?\.end method', source, re.S).group()
        assert method(before) == method(after), 'exact foreground/cancel guard changed: '+signature
    assert 'const/4 v0, 0x3' in after and after.count('const-string v2, "org.flos.phirapro"') == 1
    engine = (app/paths[1].relative_to(baseline)).read_text()
    assert 'Lio/github/phiscript/capture/PhiraForeground;->isForeground()Z' in engine
    assert engine.count('Lio/github/phiscript/capture/PhiraForeground;->beginSession()V') == 1
    assert (app/'AndroidManifest.xml').read_text().count('android:name="org.flos.phirapro"') == 1
print('PASS Phira native hooks: idempotent package admission/foreground route/manifest; exact active input and cancellation methods unchanged.')
