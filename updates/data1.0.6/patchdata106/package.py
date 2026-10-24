from pathlib import Path
import zipfile,json
ROOT=Path(__file__).resolve().parents[1]
def signature(name):
    name=name.upper();return name.startswith('META-INF/') and (name.endswith(('.RSA','.SF','.DSA','.EC')) or name=='META-INF/MANIFEST.MF')
with zipfile.ZipFile(ROOT/'current105/PhigrosScript_data1.0.5.apk') as src,zipfile.ZipFile(ROOT/'analysis/data106-rebuilt.apk') as build,zipfile.ZipFile(ROOT/'analysis/data106-unsigned.apk','w') as dst:
    for info in src.infolist():
        if signature(info.filename):continue
        name=info.filename
        if name=='classes8.dex':data=(ROOT/'patchdata106/dex/classes.dex').read_bytes()
        elif name=='AndroidManifest.xml':data=build.read(name)
        else:data=src.read(name)
        dst.writestr(info,data)
print('data1.0.6: updated manifest and helper dex; every other original entry retained.')
