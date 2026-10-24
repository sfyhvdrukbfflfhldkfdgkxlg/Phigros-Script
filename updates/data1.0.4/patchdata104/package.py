from pathlib import Path
import zipfile,json
ROOT=Path(__file__).resolve().parents[1]
def signature(name):
    name=name.upper();return name.startswith('META-INF/') and (name.endswith(('.RSA','.SF','.DSA','.EC')) or name=='META-INF/MANIFEST.MF')
with zipfile.ZipFile(ROOT/'current103/PhigrosScript_data1.0.3.apk') as src,zipfile.ZipFile(ROOT/'analysis/data104-rebuilt.apk') as build,zipfile.ZipFile(ROOT/'analysis/data104-unsigned.apk','w') as dst:
    for info in src.infolist():
        if signature(info.filename):continue
        name=info.filename
        if name=='classes8.dex':data=(ROOT/'patchdata104/dex/classes.dex').read_bytes()
        elif name=='AndroidManifest.xml' or name in json.loads((ROOT/'patchdata104/hook-report.json').read_text())['changed_dex']:data=build.read(name)
        else:data=src.read(name)
        dst.writestr(info,data)
print('data1.0.4: localized display hooks, cancellable native parsers and updated helpers; remaining original entries retained.')
