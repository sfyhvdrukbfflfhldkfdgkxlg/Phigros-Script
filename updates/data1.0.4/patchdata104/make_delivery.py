from pathlib import Path
import hashlib,zipfile
ROOT=Path(__file__).resolve().parents[1]
apk=ROOT/'deliverables/PhigrosScript_data1.0.4.apk'
dest=ROOT/'deliverables/PhigrosScript_data1.0.4_Source.zip'
with zipfile.ZipFile(dest,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for directory in ('src','tests','testsupport','translations'):
        for p in sorted((ROOT/'patchdata104'/directory).rglob('*')):
            if p.is_file() and '__pycache__' not in p.parts:z.write(p,p.relative_to(ROOT))
    for name in ('README.md','prepare.py','apply.py','package.py','build.sh','make_delivery.py','hook-report.json','localize_source.py','build_catalog.py'):
        p=ROOT/'patchdata104'/name;z.write(p,p.relative_to(ROOT))
    for p in sorted((ROOT/'patch/tests').rglob('*')):
        if p.is_file() and p.suffix in ('.java','.json','.pec','.py') and '__pycache__' not in p.parts:z.write(p,p.relative_to(ROOT))
    for n in range(1,4):
        p=ROOT/'analysis/realcharts'/('chart_'+str(n)+'.json');z.write(p,p.relative_to(ROOT))
    z.writestr('SHA256SUMS.txt',hashlib.sha256(apk.read_bytes()).hexdigest()+'  '+apk.name+'\n')
for p in (apk,dest):print(p.name,p.stat().st_size,hashlib.sha256(p.read_bytes()).hexdigest())
