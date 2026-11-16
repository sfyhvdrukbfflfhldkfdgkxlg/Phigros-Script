import zipfile,struct
from pathlib import Path
src='upload/attachment_1413700149765945124.apk'
from manifest_patch import patch_manifest as manifest
with zipfile.ZipFile(src) as z,zipfile.ZipFile('analysis/patched-unsigned.apk','w') as out:
 for info in z.infolist():
  if info.filename.startswith('META-INF/') and info.filename.rsplit('.',1)[-1].upper() in ['RSA','DSA','EC','SF','MF']:continue
  data=z.read(info.filename)
  if info.filename=='AndroidManifest.xml':data=manifest(data)
  p=Path('patch/dex')/info.filename
  if info.filename in ['classes4.dex','classes5.dex','classes6.dex','classes7.dex','classes8.dex','classes11.dex']:data=p.read_bytes()
  out.writestr(info,data)
 out.write('patch/dex/classes.dex','classes12.dex',compress_type=zipfile.ZIP_DEFLATED)
 for p in Path('patch/assets').iterdir():out.write(p,'assets/'+p.name,compress_type=zipfile.ZIP_DEFLATED)
print('packaged',Path('analysis/patched-unsigned.apk').stat().st_size)
