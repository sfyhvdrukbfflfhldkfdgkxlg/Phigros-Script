"""Extract only chart JSON from the user's original nested ZIP packages."""
from pathlib import Path
from zipfile import ZipFile
from io import BytesIO
import json,hashlib
source=Path('upload/Download.zip')
out=Path('analysis/realcharts');out.mkdir(parents=True,exist_ok=True)
records=[]
with ZipFile(source) as outer:
    for name in outer.namelist():
        if not name.lower().endswith('.zip'):continue
        with ZipFile(BytesIO(outer.read(name))) as inner:
            for info in inner.infolist():
                if not info.filename.lower().endswith('.json') or info.file_size>16*1024*1024:continue
                raw=inner.read(info.filename);root=json.loads(raw)
                if 'BPMList' not in root or 'judgeLineList' not in root:continue
                path=out/f'chart_{len(records)+1}.json';path.write_bytes(raw)
                records.append(dict(package=name,chart=info.filename,path=str(path),bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest()))
                break
if len(records)!=3:raise ValueError('Expected three reference charts')
(out/'manifest.json').write_text(json.dumps(records,ensure_ascii=False,indent=2))
print(json.dumps(records,ensure_ascii=False,indent=2))
