"""Restore removed classes still referenced by the retained APK or new patch."""
import sys,re,shutil,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent/'lib'))
from dex_info import apk_dexes,DexInfo,aggregate,base_type
original=apk_dexes('upload/attachment_1413700149765945124.apk');changed={4,5,6,7,8,11}
old=aggregate(original.values());defined=set();referenced=set();paths={}
for name,dex in original.items():
    index=1 if name=='classes.dex' else int(name[7:-4]);folder='smali' if index==1 else 'smali_classes'+str(index)
    for cls in dex.classes:paths[cls]=(index,Path('analysis/decoded')/folder/(cls[1:-1]+'.smali'))
    if index not in changed:defined.update(dex.classes);referenced.update(base_type(t) for t in dex.types)
new=DexInfo(Path('patch/dex/classes.dex').read_bytes());defined.update(new.classes);referenced.update(base_type(t) for t in new.types)
def include(file):
    text=file.read_text();match=re.search(r'^\.class\s+.*?(L[^\s;]+;)',text,re.M)
    if not match:raise RuntimeError('no class '+str(file))
    if match.group(1) in new.classes:raise RuntimeError('duplicate replacement class '+match.group(1))
    defined.add(match.group(1));referenced.update(re.findall(r'L[^\s;\[\](),"<>]+;',text))
for folder in sorted(Path('patch/smali').iterdir()):
    for file in folder.rglob('*.smali'):include(file)
restored=[]
while True:
    missing=sorted(referenced & old['classes']-defined)
    if not missing:break
    for cls in missing:
        index,source=paths[cls]
        if index not in changed:raise RuntimeError('unexpected unmodified dependency '+cls)
        target=Path('patch/smali')/str(index)/(cls[1:-1]+'.smali');target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target);include(target);restored.append(cls)
Path('analysis/r3-restored-dependencies.json').write_text(json.dumps({'restored_shared_classes':restored},indent=2))
print('restored shared dependencies:',len(restored))
for cls in restored:print(cls)
