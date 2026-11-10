from pathlib import Path
import hashlib
import json
root=Path('patch/src/io/github/phiscript')
external=(root/'assets/ExternalCharts.java').read_text()
home=(root/'root/HomeScreen.java').read_text()
menu=home[home.index('private void importCharts()'):home.index('private void permissions()')]
for index,action in [(0,'SwordaltLibraryUi.open'),(1,'ExternalCharts.useNewShare'),(2,'ExternalCharts.useCloud'),(3,'ExternalCharts.choose'),(4,'ExternalCharts.network'),(5,'ExternalCharts.useLocal')]:
    line=next(line for line in menu.splitlines() if f'if(i=={index})' in line)
    assert action+'(activity)' in line,(index,action)
assert 'else if(i==7){invoke(activity,"chooseApks");}' in menu
assert 'else if(i==8)invoke(activity,"choosePhira");' in menu
assert 'else if(i==6){importInstalledCharts();}' in menu
installed=menu[menu.index('private void importInstalledCharts()'):]
assert 'RootPanel.scan(activity)' in installed and 'invoke(activity,"scan",' in installed
assert external.count('cloud=restoreCloud(a)')==2
assert 'getAssets().open(FeijipanSource.NEW_ASSET)' in external
assert 'DEFAULT_SHARE=FeijipanSource.NEW_SHARE' in external
assert 'setPhiraLibrary(' not in external
new=external[external.index('public static void useNewShare('):external.index('public static void useLocal(')]
assert new.index('fromCatalog(a,catalog)')<new.index('atomic(saved,')<new.index('commitPhigros(')
load=external[external.index('public static Chart load(ChartLibrary lib,String song,String level,AtomicBoolean'):external.index('public static Chart loadSelected')]
assert load.index('FeijipanSource.verify(')<load.index('Chart.Companion.parse(')<load.index('atomic(destination,bytes)')
assert load.index('谱面噪域数量与清单不一致')<load.index('atomic(destination,bytes)')
# Low-level playback sources retain R6 behavior; R8 replaces the two session entry adapters and retry key.
hashes=json.loads(Path('patch/tests/r7/r6-playback-sha256.json').read_bytes())
checked=0
for name,expected in hashes.items():
    if name.endswith(('/PhigrosAutoEngine.java','/PhigrosRetryClock.java','/AutoSessionRouter.java')):continue
    assert hashlib.sha256(Path(name).read_bytes()).hexdigest()==expected,name
    checked+=1
assert checked==9
catalog=json.loads(Path('patch/assets/phigros-cloud-feyajI8g-v1.json').read_bytes())
assert catalog['share']=='feyajI8g' and catalog['expectedCharts']==1057 and len(catalog['missing'])==5
assert len(catalog['entries'])==1052
print(f'PASS R7 menu/default/commit/integrity wiring; {checked} R6 low-level playback sources unchanged; 1052 exact file locations, 5 missing paths')
