"""Extract exact already-converted charts missing from the recorded public share."""
from pathlib import Path
import hashlib
import json
import zipfile
snapshot=Path('patch/data-tools/snapshot/feijipan-feyajI8g')
audit=json.loads((snapshot/'audit.json').read_bytes())
assert audit['share']=='feyajI8g' and len(audit['missing'])==5
entries={e['path']:e for e in json.loads((snapshot/'manifest.json').read_bytes())['entries']}
output=Path('deliverables/Phigros_feyajI8g_missing_5.zip')
readme='''新小飞机分享缺失谱面补齐包\n\n目标分享：https://share.feijipan.com/s/feyajI8g\n\n解压本包，将 charts/ 中的歌曲文件夹合并上传到分享内的 Phigros/charts/。\n保留歌曲文件夹和难度文件名；不要直接把整个 ZIP 当作一个谱面导入。\n原分享的 swordalt-catalog.json 已登记这些文件，无需改写。\n上传后，在应用“导入谱面 → Phigros · 联网链接 / 更新目录”连接此链接，\n刷新后应用会按新文件编号识别缺失谱面。\n\n所有文件均从之前完整的 1057 张脚本包原样提取，已验证字节长度和 Git blob SHA-1。\n这 5 张均无非空噪域，不需补传额外 noise/ 文件。\n\n缺失路径：\n'''+''.join('charts/'+p+'\n' for p in audit['missing'])
results=[]
with zipfile.ZipFile('count-current/Phigros_Swordalt_All_Scripts.zip') as source,zipfile.ZipFile(output,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=9) as target:
 for path in audit['missing']:
  name='charts/'+path;data=source.read(name);entry=entries[path]
  assert len(data)==entry['bytes']
  digest=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
  assert digest==entry['sha1'] and entry['noiseCount']==0
  target.writestr(name,data)
  results.append(dict(path=path,bytes=len(data),sha1=digest))
 target.writestr('README.txt',readme.encode('utf-8'))
 target.writestr('attribution/Swordalt-MIT.txt',Path('patch/attribution/Swordalt-MIT.txt').read_bytes())
with zipfile.ZipFile(output) as archive:assert archive.testzip() is None
report=dict(charts=results,zip_bytes=output.stat().st_size,sha256=hashlib.sha256(output.read_bytes()).hexdigest())
Path('r7/repair-pack.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(report,ensure_ascii=False))
