from pathlib import Path
engine=Path('patch/src/io/github/phiscript/capture/PhigrosAutoEngine.java').read_text()
for removed in ['PhigrosStartGate','PhigrosControls','PhigrosRetryClock','VisualNoteDetector','new Thread','RootChartPlayer','startClock(']:
    assert removed not in engine,removed
assert 'PhiraSessionEngine.class.getDeclaredConstructor' in engine and 'engine.run()' in engine
source=Path('patch/src/io/github/phiscript/assets/SessionChartSource.java').read_text()
assert 'ExternalCharts.loadSelected(' in source and 'return phira.load(id,level)' in source
shared=Path('patch/smali/5/io/github/phiscript/capture/PhiraSessionEngine.smali').read_text()
for field in ['getPhiraRestartDelayMs','getPhiraGlobalOffsetMs','getTouchOffsetMs']:
    assert field in shared
assert shared.index('SessionChartSource;->load(')<shared.rindex('->prepareRetry(')
ui=Path('patch/src/io/github/phiscript/root/RetryCalibration.java').read_text()
assert ui.count('new EditText(')==1 and 'new CheckBox(' not in ui and 'new Switch(' not in ui
assert 'KEY="phigrosRestartDelay"' in Path('patch/src/io/github/phiscript/capture/PhigrosRetryClock.java').read_text()
home=Path('patch/src/io/github/phiscript/root/HomeScreen.java').read_text()
assert '"Phigros / Phira · 重试校准"' in home
assert '"Phigros 自动同步 / 暂停识别"' not in home and '"手动起点与视口"' not in home
print('PASS shared calibration: one retry control, separate game offsets and session engine, source-only chart loading')
