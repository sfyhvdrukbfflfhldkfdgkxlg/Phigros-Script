"""Prepare unmodified data1.0.7 as an API/build baseline. Does not download dependencies."""
from pathlib import Path
import hashlib,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
apk=ROOT/'current107/PhigrosScript_data1.0.7.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest()=='5bb2237308ce02ba3077781caa8990f9e74edabece1fd459f081345e5df3bedf','Use the exact data1.0.7 baseline described in README'
app=ROOT/'analysis/data107';app.parent.mkdir(exist_ok=True)
if not app.exists():subprocess.run(['java','-jar','tools/apktool.jar','d','-f',str(apk),'-o',str(app)],cwd=ROOT,check=True)
jar=ROOT/'analysis/data107-base.jar'
if not jar.exists():subprocess.run(['bash','tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh','-f','-o',str(jar),str(apk)],cwd=ROOT,check=True)
prefixes=tuple(str(p.relative_to(ROOT/'patchdata108/src')).removesuffix('.java') for p in (ROOT/'patchdata108/src').rglob('*.java'))
with zipfile.ZipFile(jar) as src,zipfile.ZipFile(ROOT/'analysis/data107-api.jar','w',zipfile.ZIP_DEFLATED) as out:
    for name in src.namelist():
        if not any(name==p+'.class' or name.startswith(p+'$') for p in prefixes):out.writestr(name,src.read(name))
print('Prepared baseline decode and API jar; apply.py must run once on a fresh versionCode 23 decode.')
