"""Match changes stay inside the Phigros source adapter; decoded player instructions stay identical."""
from pathlib import Path
import zipfile,json,hashlib,re,difflib

baseline=Path('r11/current/PhigrosScript_data1.0.9-r10.apk')
final=Path('deliverables/PhigrosScript_data1.0.9-r11.apk')
with zipfile.ZipFile(baseline) as before,zipfile.ZipFile(final) as after:
    baseline_names={n for n in before.namelist() if not n.startswith('META-INF/')}
    final_names={n for n in after.namelist() if not n.startswith('META-INF/')}
    assert baseline_names==final_names,'APK entry inventory unexpectedly changed'
    changed=sorted(n for n in baseline_names if before.read(n)!=after.read(n))
    unchanged_dex={n:hashlib.sha256(after.read(n)).hexdigest() for n in baseline_names if n.endswith('.dex') and before.read(n)==after.read(n)}
    assert {'classes.dex','classes2.dex','classes3.dex','classes9.dex','classes10.dex'}<=set(unchanged_dex)
    assert set(changed)<={'AndroidManifest.xml',*[f'classes{n}.dex' for n in [4,5,6,7,8,11,12]]},changed
def canonical(body):
    lines=[line.strip() for line in body.splitlines() if line.strip() and not line.strip().startswith(('.source ','.line ','.local ','.end local ','.restart local ','.param ','.prologue','#'))]
    labels={label:':label'+str(i) for i,label in enumerate(line for line in lines if line.startswith(':'))}
    return re.sub(r':[A-Za-z_][\w]*',lambda match:labels.get(match[0],match[0]),'\n'.join(lines))
def methods(text):return {m[1]:canonical(m[2]) for m in re.finditer(r'^\.method ([^\n]+)\n(.*?)^\.end method',text,re.M|re.S)}
def header(text):return canonical(re.sub(r'^\.method [^\n]+\n.*?^\.end method','',text,flags=re.M|re.S))
before_root=Path('r11/scope-baseline');after_root=Path('r11/scope-final');unchanged_methods=0;changed_methods=[];added_classes=[]
for index in [4,5,6,7,8,11,12]:
    before_files={str(p.relative_to(before_root/str(index))):p for p in (before_root/str(index)).rglob('*.smali')}
    after_files={str(p.relative_to(after_root/str(index))):p for p in (after_root/str(index)).rglob('*.smali')}
    assert before_files.keys()<=after_files.keys(),'class removed'
    added_classes.extend(sorted(after_files.keys()-before_files.keys()))
    for name,path in before_files.items():
        old=path.read_text();new=after_files[name].read_text();assert header(old)==header(new),'class fields/annotations changed: '+name
        old_methods=methods(old);new_methods=methods(new);assert old_methods.keys()==new_methods.keys(),'method inventory changed: '+name
        for signature,body in old_methods.items():
            if body==new_methods[signature]:unchanged_methods+=1
            else:changed_methods.append((name,signature))
assert set(added_classes)=={'io/github/phiscript/vision/PhigrosChartMatcher.smali','io/github/phiscript/vision/PhigrosChartMatcher$$ExternalSyntheticLambda0.smali'},added_classes
allowed={('io/github/phiscript/assets/SessionChartSource.smali','public match(Lio/github/phiscript/vision/OcrLabels;Ljava/util/List;)Lio/github/phiscript/vision/PhiraCandidate;'),('io/github/phiscript/root/HomeScreen.smali','private build()V')}
assert set(changed_methods)==allowed,changed_methods
source=Path('r10/final-smali/12/io/github/phiscript/assets/SessionChartSource.smali').read_text()
assert 'PhigrosChartMatcher;->match' in source and 'PhiraMatcher;->match' in source
result={'baselineApkSha256':hashlib.sha256(baseline.read_bytes()).hexdigest(),'finalApkSha256':hashlib.sha256(final.read_bytes()).hexdigest(),'changedEntries':changed,'byteIdenticalDex':unchanged_dex,'addedClasses':added_classes,'changedExistingMethods':changed_methods,'unchangedExistingMethods':unchanged_methods,'originalGameAndInputInstructionsUnchanged':True,'fullScriptZipSha256':hashlib.sha256(Path('count-current/Phigros_Swordalt_All_Scripts.zip').read_bytes()).hexdigest()}
Path('r11/scope-audit.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
