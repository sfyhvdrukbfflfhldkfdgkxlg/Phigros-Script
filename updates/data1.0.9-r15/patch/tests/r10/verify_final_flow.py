from pathlib import Path
import re,sys,json,difflib
sys.path.insert(0,'patch/lib')
from dex_info import apk_dexes,aggregate

apk=Path(sys.argv[1] if len(sys.argv)>1 else 'deliverables/PhigrosScript_data1.0.9-r15.apk')
root=Path('r10/final-smali');base='io/github/phiscript/'
def method(text,prefix):
    start=text.index(prefix);return text[start:text.index('.end method',start)]
def canonical(text):
    lines=[line.strip() for line in text.splitlines() if line.strip() and not line.strip().startswith(('.line ','.local ','.end local ','.restart local ','.param ','.prologue','#'))]
    labels={label:':label'+str(i) for i,label in enumerate(line for line in lines if line.startswith(':'))}
    return re.sub(r':[A-Za-z_][\w]*',lambda match:labels.get(match[0],match[0]),'\n'.join(lines))
original=Path('analysis/decoded/smali_classes8/'+base+'input/UnifiedGesturePlanner.smali').read_text()
final=(root/'8'/(base+'input/UnifiedGesturePlanner.smali')).read_text()
old='    aput v0, v5, v4\n\n    goto :goto_6'
new=old.replace('goto :goto_6','goto :goto_7')
assert original.count(old)==1
def method_bodies(text):return {m[1]:canonical(m[2]) for m in re.finditer(r'^\.method ([^\n]+)\n(.*?)^\.end method',text,re.M|re.S)}
expected=method_bodies(original.replace(old,new));actual=method_bodies(final)
assert expected==actual,'gesture planner changed beyond the crossing-loop exit'
program=aggregate(apk_dexes(apk).values())
removed=['assets/ChartTimingIndex','root/FirstNoteBrowser','capture/PhigrosStartGate','capture/PhigrosControls','capture/PhigrosHudProbe','capture/SharedPhiraControls']
for cls in removed:
    stem='L'+base+cls
    assert not any(name==stem+';' or name.startswith(stem+'$') for name in program['classes']),cls
    assert not any(name==stem+';' or name.startswith(stem+'$') for name in program['types']),cls+' still referenced'
activity=(root/'4'/(base+'MainActivity.smali')).read_text()
settings=method(activity,'.method private final calibration()V')
assert 'RecognitionSettings;->open' in settings and 'EditText' not in settings
scan=(root/'6'/(base+'assets/ChartLibrary$Companion.smali')).read_text()
assert 'ChartTimingIndex' not in scan
source=(root/'12'/(base+'assets/SessionChartSource.smali')).read_text()
assert 'ChartTimingIndex' not in source and 'ExternalCharts;->loadSelected' in source
retry=(root/'12'/(base+'root/RetryCalibration.smali')).read_text()
recognition=(root/'12'/(base+'root/RecognitionSettings.smali')).read_text()
assert retry.count('new-instance')>0 and 'phiraRestartDelay' in retry
assert 'phiraRestartDelay' not in recognition
home=(root/'12'/(base+'root/HomeScreen.smali')).read_text()
assert 'FirstNoteBrowser' not in home and 'ChartTimingIndex' not in home
result={'crossingFix':'only one branch changed; all other planner instructions preserved','removedLegacyClasses':removed,'legacyClassReferences':0,'timeTableImportAndPlaybackCalls':0,'activeCalibration':'RecognitionSettings + one RetryCalibration','sharedSessionClock':'PhiraRestartClock (R8 parity audit)','apk':apk.name}
Path('r10/final-flow-audit.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False))
