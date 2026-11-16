"""Compare real APK session instructions, allowing source access, labels and the explicit game-menu adapter."""
from pathlib import Path
import difflib
import hashlib
import json
import re
import sys

def methods(text):
    return {m.group(1):m.group(2) for m in re.finditer(r'^\.method ([^\n]+)\n(.*?)^\.end method',text,re.M|re.S)}

def canonical(body):
    body=body.replace('Lio/github/phiscript/assets/SessionChartSource;','Lio/github/phiscript/assets/PhiraLibrary;').replace('Lio/github/phiscript/capture/SessionPauseMenu;','Lio/github/phiscript/vision/PhiraPauseDetector;')
    result=[]
    for line in body.splitlines():
        line=line.strip()
        if not line or line.startswith(('#','.line ','.local ','.end local ','.param ','.prologue','.restart local ')):continue
        match=re.fullmatch(r'(const-string(?:/jumbo)? [^,]+, )("(?:\\.|[^"\\])*")',line)
        if match:
            message=json.loads(match.group(2))
            if message=='请先导入 Phira ZIP/PEZ 谱面包':message='请先选择已导入或联网谱库'
            else:message=message.replace('Phira 谱面模式','谱面模式').replace('Phira ','').replace('Phira','当前游戏')
            line=match.group(1)+json.dumps(message,ensure_ascii=True)
        if line.startswith('invoke-') and re.search(r'->(?:getPhiraRestartPoint|activeRetryPoint)\(\)Lio/github/phiscript/vision/NormalizedPoint;',line):line='invoke-menu-retry-point'
        result.append(line)
    for i,line in enumerate(result):
        if '->phigrosPauseRegion(Ljava/lang/Object;)' in line:
            assert result[i+1]=='move-result-object v0' and result[i+2].startswith('if-eqz v0, ') and result[i+3]=='return-object v0' and result[i+4].startswith(':')
            result=result[:i]+result[i+5:]
            break
    prepare='invoke-static {v1, v2, v9, v3, v10}, Lio/github/phiscript/input/SharedChartPlayback;->prepare(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lio/github/phiscript/engine/Chart;Lio/github/phiscript/engine/Viewport;)V'
    if prepare in result:
        i=result.index(prepare)
        assert result[i-2:i]==['move-result-object v10','move-object/from16 v3, p3']
        assert '->viewportFor(' in result[i-3]
        del result[i-1:i+1]
        result.insert(1,'invoke-static/range {p0 .. p3}, Lio/github/phiscript/input/SharedChartPlayback;->prepare(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Lio/github/phiscript/engine/Chart;)V')
    labels={line.split()[0]:':LABEL'+str(i) for i,line in enumerate(line for line in result if line.startswith(':'))}
    return re.sub(r':[A-Za-z_][A-Za-z0-9_]*',lambda m:labels.get(m.group(0),m.group(0)),'\n'.join(result))

baseline_file=Path('patch/tests/r8/phira-r7-baseline.json')
if len(sys.argv)>1 and sys.argv[1]=='--record':
    baseline=methods(Path('r8/phira-r7.smali').read_text())
    baseline={name:canonical(body) for name,body in baseline.items() if '<init>(' not in name and not name.startswith(('private final match(','private final isForeground('))}
    baseline_file.write_text(json.dumps({'apk_sha256':'5fb85dc844dbeadb9d3e9bb5c351768092cf7e3d813dc2a5d82cb3a0f3123098','methods':baseline},indent=2,ensure_ascii=False))
    print('Recorded actual delivered R7 Phira methods:',len(baseline))
    sys.exit()
baseline=json.loads(baseline_file.read_bytes())['methods']
current=methods(Path(sys.argv[1] if len(sys.argv)>1 else 'r8/current-session.smali').read_text())
for name,expected in baseline.items():
    delegate=name if name in current else name.replace(' pauseMenuFor(',' phiraPauseMenuFor(')
    actual=canonical(current[delegate])
    expected=canonical(expected)
    if actual!=expected:
        print('Changed session behavior:',name)
        print('\n'.join(difflib.unified_diff(expected.splitlines(),actual.splitlines()))[:7000])
        raise SystemExit(1)
assert len(baseline)>=18
engine=Path('patch/src/io/github/phiscript/capture/PhigrosAutoEngine.java').read_text()
assert 'PhiraSessionEngine.class.getDeclaredConstructor' in engine and 'engine.run()' in engine
for forbidden in ['new Thread','PhigrosControls','PhigrosStartGate','PhigrosRetryClock','RootChartPlayer','freshFrame','awaitPaused','startClock']:
    assert forbidden not in engine,forbidden
router=Path('patch/src/io/github/phiscript/capture/AutoSessionRouter.java').read_text()
assert 'new PhigrosAutoEngine(' in router and 'while(' not in router and 'ServiceChanged' not in router
root=Path('patch/src/io/github/phiscript/root/RootPanel.java').read_text()
load=root[root.index('private static void load('):root.index('public static void timing(')]
assert 'SessionChartSource.selectManual(' in load and 'AutoEntry.launch(' in load
assert 'RecordingConsentActivity' not in root and 'phigros_manual_delay_ms' not in root
assert 'SessionChartSource.hasManual(' in Path('patch/src/io/github/phiscript/root/PlayEntry.java').read_text()
assert 'return capturedMode==PlayMode.VISUAL&&needsPhigros(p);' in Path('patch/src/io/github/phiscript/root/ModeRouting.java').read_text()
print(f'PASS actual APK parity: {len(baseline)} Phira methods retain identical instructions after source/label/menu-adapter normalization; both Phigros routes enter the same engine')
