import json,urllib.request,time
from pathlib import Path
base=Path('analysis/swordalt');base.mkdir(parents=True,exist_ok=True)
import shutil
for source in Path('patch/data-tools/snapshot').iterdir():
 if source.is_file():shutil.copyfile(source,base/source.name)
sha=json.loads((base/'chart-branch.json').read_text())['commit']['sha'];dest=base/'charts-original.tar.gz';temporary=base/'charts-original.partial'
if dest.exists():print('reuse',dest.stat().st_size,flush=True);raise SystemExit
request=urllib.request.Request('https://codeload.github.com/7aGiven/Phigros_Resource/tar.gz/'+sha,headers={'User-Agent':'PhigrosScript-maintenance/1.0'})
with urllib.request.urlopen(request,timeout=60) as response,temporary.open('wb') as out:
 size=0;last=0
 while True:
  data=response.read(1024*1024)
  if not data:break
  out.write(data);size+=len(data)
  if size>600*1024*1024:raise RuntimeError('archive exceeds 600 MiB')
  if time.monotonic()-last>15:print('downloaded MiB',round(size/1048576,1),flush=True);last=time.monotonic()
temporary.replace(dest);print('archive complete',size,flush=True)
