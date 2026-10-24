"""Prepare unmodified data1.0.3 as an API/build baseline. Does not download dependencies."""
from pathlib import Path
import hashlib,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
apk=ROOT/'current103/PhigrosScript_data1.0.3.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest()=='76c6cf44106eae9aafbb162bfa9090bb4118cf7ec1359a92e8e685ff19105ee0','Use the exact data1.0.3 baseline described in README'
app=ROOT/'analysis/data103';app.parent.mkdir(exist_ok=True)
if not app.exists():subprocess.run(['java','-jar','tools/apktool.jar','d','-f',str(apk),'-o',str(app)],cwd=ROOT,check=True)
jar=ROOT/'analysis/data103-base.jar'
if not jar.exists():subprocess.run(['bash','tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh','-f','-o',str(jar),str(apk)],cwd=ROOT,check=True)
prefixes=tuple(str(p.relative_to(ROOT/'patchdata104/src')).removesuffix('.java') for p in (ROOT/'patchdata104/src').rglob('*.java'))
with zipfile.ZipFile(jar) as src,zipfile.ZipFile(ROOT/'analysis/data103-api.jar','w',zipfile.ZIP_DEFLATED) as out:
    for name in src.namelist():
        if not any(name==p+'.class' or name.startswith(p+'$') for p in prefixes):out.writestr(name,src.read(name))
print('Prepared baseline decode and API jar; apply.py must run once on a fresh versionCode 19 decode.')
