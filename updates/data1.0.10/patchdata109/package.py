from pathlib import Path
import zipfile,json
ROOT=Path(__file__).resolve().parents[1]
def signature(name):
    name=name.upper();return name.startswith('META-INF/') and (name.endswith(('.RSA','.SF','.DSA','.EC')) or name=='META-INF/MANIFEST.MF')
report=json.loads((ROOT/'patchdata109/hook-report.json').read_text())
changed=set(report['changed_dex'])
assert changed and 'classes8.dex' not in changed
with zipfile.ZipFile(ROOT/'current108/PhigrosScript_data1.0.8.apk') as src,zipfile.ZipFile(ROOT/'analysis/data109-rebuilt.apk') as build,zipfile.ZipFile(ROOT/'analysis/data109-unsigned.apk','w') as dst:
    for info in src.infolist():
        if signature(info.filename):continue
        name=info.filename
        if name=='classes8.dex':data=(ROOT/'patchdata109/dex/classes.dex').read_bytes()
        elif name=='AndroidManifest.xml':data=build.read(name)
        elif name in changed:data=build.read(name)
        else:data=src.read(name)
        dst.writestr(info,data)

print('data1.0.10: updated manifest, helper DEX and native hooks:',sorted(changed))
