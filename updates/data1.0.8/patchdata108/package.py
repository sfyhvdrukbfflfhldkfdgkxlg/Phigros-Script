from pathlib import Path
import zipfile,json
ROOT=Path(__file__).resolve().parents[1]
def signature(name):
    name=name.upper();return name.startswith('META-INF/') and (name.endswith(('.RSA','.SF','.DSA','.EC')) or name=='META-INF/MANIFEST.MF')
with zipfile.ZipFile(ROOT/'current107/PhigrosScript_data1.0.7.apk') as src,zipfile.ZipFile(ROOT/'analysis/data108-rebuilt.apk') as build,zipfile.ZipFile(ROOT/'analysis/data108-unsigned.apk','w') as dst:
    for info in src.infolist():
        if signature(info.filename):continue
        name=info.filename
        if name=='classes8.dex':data=(ROOT/'patchdata108/dex/classes.dex').read_bytes()
        elif name in ('AndroidManifest.xml','classes6.dex','classes7.dex'):data=build.read(name)
        else:data=src.read(name)
        dst.writestr(info,data)
    dst.writestr('assets/phigros-first-notes-v1.tsv',(ROOT/'patchdata108/assets/phigros-first-notes-v1.tsv').read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
print('data1.0.8: updated manifest, shared import hook, helper dex and bundled first-note facts; other original entries retained.')
