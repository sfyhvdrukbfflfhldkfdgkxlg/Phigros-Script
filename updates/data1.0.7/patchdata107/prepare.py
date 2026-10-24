"""Prepare unmodified data1.0.6 as an API/build baseline. Does not download dependencies."""
from pathlib import Path
import hashlib,subprocess,zipfile
ROOT=Path(__file__).resolve().parents[1]
apk=ROOT/'current106/PhigrosScript_data1.0.6.apk'
assert hashlib.sha256(apk.read_bytes()).hexdigest()=='0148b93c31bbafd191b9db44b1f8ba007c58b6903baf376290671e115a662cd6','Use the exact data1.0.5 baseline described in README'
app=ROOT/'analysis/data106';app.parent.mkdir(exist_ok=True)
if not app.exists():subprocess.run(['java','-jar','tools/apktool.jar','d','-f',str(apk),'-o',str(app)],cwd=ROOT,check=True)
jar=ROOT/'analysis/data106-base.jar'
if not jar.exists():subprocess.run(['bash','tools/dex2jar/dex-tools-v2.4/d2j-dex2jar.sh','-f','-o',str(jar),str(apk)],cwd=ROOT,check=True)
prefixes=tuple(str(p.relative_to(ROOT/'patchdata107/src')).removesuffix('.java') for p in (ROOT/'patchdata107/src').rglob('*.java'))
with zipfile.ZipFile(jar) as src,zipfile.ZipFile(ROOT/'analysis/data106-api.jar','w',zipfile.ZIP_DEFLATED) as out:
    for name in src.namelist():
        if not any(name==p+'.class' or name.startswith(p+'$') for p in prefixes):out.writestr(name,src.read(name))
print('Prepared baseline decode and API jar; apply.py must run once on a fresh versionCode 22 decode.')
