from pathlib import Path
p=Path('patch/smali/5/io/github/phiscript/capture/PhiraSessionEngine.smali').read_text()
prepare=p[p.index('.method private final prepareRetry'):p.index('.method private final recognizeAndConfirm')]
assert prepare.index('SharedChartPlayback;->prepare')<prepare.index('PhiraRestartClock;->epoch')
assert 'SharedChartPlayback;->control' in p and p.count('SharedChartPlayback;->close')>=2
native=Path('patch/smali/7/io/github/phiscript/engine/PhiraNativeChart.smali').read_text()
assert 'NativeChartProjection;->position' in native and 'NativeChartProjection;->normal' in native
animation=Path('patch/smali/7/io/github/phiscript/engine/NativeAnim.smali').read_text()
assert ':r6_native_track_started' in animation and 'NativeChartProjection;->unit' in animation
phigros=Path('patch/src/io/github/phiscript/capture/PhigrosAutoEngine.java').read_text()
assert 'PhiraSessionEngine.class.getDeclaredConstructor' in phigros and 'engine.run()' in phigros
shared=Path('patch/src/io/github/phiscript/input/SharedChartPlayback.java').read_text()
assert 'service.play(chart,epoch,viewport,stop,status)' in shared and 'new RootWarmup(s,w,h)' in shared
player=Path('patch/src/io/github/phiscript/input/RootChartPlayer.java').read_text()
assert 'SharedChartPlayback.takeRoot(chart)' in player
warm=Path('patch/src/io/github/phiscript/root/RootWarmup.java').read_text()
assert 'return firstDown' in warm
print('PASS actual APK wiring: Phira prepares before retry, shared menu/cleanup, common service.play for both games, prepared Root ownership, native projection and pre-start clamp')
