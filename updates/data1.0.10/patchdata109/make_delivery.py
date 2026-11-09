"""Package reproducible sources and reports; never include signing material."""
from pathlib import Path
import hashlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
apk=ROOT/'deliverables/PhigrosScript_data1.0.10.apk'
recorder=ROOT/'deliverables/GestureRecorder_1.0.0.apk'
dest=ROOT/'deliverables/PhigrosScript_data1.0.10_Source.zip'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for directory in ('src','tests','testsupport','translations','assets','reference'):
        for p in sorted((ROOT/'patchdata109'/directory).rglob('*')):
            if p.is_file() and '__pycache__' not in p.parts:z.write(p,p.relative_to(ROOT))
    for p in sorted((ROOT/'patchdata109').iterdir()):
        if p.is_file() and p.suffix in ('.py','.sh','.md','.json'):z.write(p,p.relative_to(ROOT))
    for p in sorted((ROOT/'gesture-recorder').rglob('*')):
        if p.is_file() and not any(part in ('build','.gradle','__pycache__') for part in p.relative_to(ROOT/'gesture-recorder').parts):z.write(p,p.relative_to(ROOT))
    for p in sorted((ROOT/'patch/tests').rglob('*')):
        if p.is_file() and p.suffix in ('.java','.json','.pec','.py') and '__pycache__' not in p.parts:z.write(p,p.relative_to(ROOT))
    for n in range(1,4):
        p=ROOT/'analysis/realcharts'/('chart_'+str(n)+'.json');z.write(p,p.relative_to(ROOT))
    for name in ('README.md','TESTING.md','.gitignore'):z.write(ROOT/name,name)
    z.writestr('SHA256SUMS.txt',''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in (apk,recorder)))
artifacts=(apk,recorder,dest)
(ROOT/'deliverables/SHA256SUMS.txt').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n' for p in artifacts))
for p in artifacts:print(p.name,p.stat().st_size,hashlib.sha256(p.read_bytes()).hexdigest())
