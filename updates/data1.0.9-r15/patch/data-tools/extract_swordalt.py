import json,tarfile,hashlib,time,re,zipfile
from pathlib import Path
base=Path('analysis/swordalt');raw=base/'raw-charts';noise=base/'noise';raw.mkdir(exist_ok=True);noise.mkdir(exist_ok=True)
info={p[0]:p[1:] for p in (line.split('\t') for line in (base/'info.tsv').read_text().splitlines()) if len(p)>1}
difficulty={p[0]:p[1:] for p in (line.split('\t') for line in (base/'difficulty.tsv').read_text().splitlines())}
chart_commit=json.loads((base/'chart-branch.json').read_text())['commit']['sha'];info_commit=json.loads((base/'info-branch.json').read_text())['commit']['sha'];tree={row['path']:row for row in json.loads((base/'chart-tree.json').read_text())['tree'] if row['type']=='blob'}
aliases_map=json.loads(Path('patch/data-tools/snapshot/song-aliases.json').read_text())
entries=[];noise_rows=[];formats={};totals=0;last=time.monotonic();seen=set()
with tarfile.open(base/'charts-original.tar.gz','r|gz') as archive:
 for member in archive:
  if not member.isfile():continue
  parts=member.name.split('/',1)
  if len(parts)!=2 or parts[1] not in tree:raise RuntimeError('unexpected archive path '+member.name)
  path=parts[1];expected=tree[path];data=archive.extractfile(member).read(64*1024*1024+1)
  if len(data)!=expected['size'] or len(data)>64*1024*1024:raise RuntimeError('size '+path)
  blob=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
  if blob!=expected['sha']:raise RuntimeError('git blob hash '+path)
  doc=json.loads(data);blocks=doc.get('blockAreaList',[])
  if not isinstance(blocks,list):raise RuntimeError('block list '+path)
  songfile,filename=path.split('/');rank=filename[:-5];match=re.fullmatch(r'(.+)\.(\d+)',songfile);key=match[1] if match else songfile;variant=int(match[2]) if match else 0
  meta=info.get(key);title=meta[0] if meta else songfile;composer=meta[1] if meta and len(meta)>1 else ''
  aliases=[]
  if key=='AnotherMe.NeutralMoon':title='Another Me (Neutral Moon)';aliases=['Another Me']
  if key=='AnotherMe.DAAN':title='Another Me (DAAN)';aliases=['Another Me']
  if variant:title+='（变体 '+str(variant)+'）'
  levels=difficulty.get(key,[]);level=levels[['EZ','HD','IN','AT'].index(rank)] if rank in ['EZ','HD','IN','AT'] and len(levels)>['EZ','HD','IN','AT'].index(rank) else '?'
  visible=bool(meta and variant==0 and (level not in ['', '?'] or key=='Message.くるぶっこちゃん' and rank=='SP'))
  aliases=list(dict.fromkeys(aliases+[a for a in aliases_map.get(title,[]) if a.strip()]))
  entry={'path':path,'id':songfile,'title':title,'composer':composer,'difficulty':rank,'level':level or '?','aliases':aliases,'sha1':blob,'bytes':len(data),'noiseCount':len(blocks),'noiseDeclared':'blockAreaList' in doc,'siteVisible':visible}
  entries.append(entry);seen.add(path);totals+=len(data);formats[str(doc.get('formatVersion'))]=formats.get(str(doc.get('formatVersion')),0)+1
  dest=raw/path;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(data)
  if blocks:
   sidecar=noise/path;sidecar.parent.mkdir(parents=True,exist_ok=True);sidecar.write_text(json.dumps({'songId':songfile,'difficulty':rank,'chartOffset':doc.get('offset',0),'blockAreaList':blocks},ensure_ascii=False,separators=(',',':')))
   noise_rows.append(entry)
  if len(entries)%100==0:print('extracted',len(entries),'noise charts',len(noise_rows),flush=True)
if seen!=set(tree):raise RuntimeError('missing paths '+repr(set(tree)-seen))
entries.sort(key=lambda x:x['path'])
catalog={'phiscriptSwordaltCatalog':1,'provider':'swordalt','repository':'7aGiven/Phigros_Resource','website':'https://swordalt.github.io/phigros-chart-downloader/','chartCommit':chart_commit,'infoCommit':info_commit,'version':(base/'version.txt').read_text().strip(),'entries':entries}
(base/'swordalt-catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,separators=(',',':')))
summary={'version':catalog['version'],'chartCommit':chart_commit,'infoCommit':info_commit,'allCharts':len(entries),'siteVisibleCharts':sum(e['siteVisible'] for e in entries),'hiddenVariants':sum(not e['siteVisible'] for e in entries),'totalBytes':totals,'formats':formats,'noiseCharts':len(noise_rows),'noiseAreas':sum(e['noiseCount'] for e in entries),'chartsWithNoise':noise_rows,'allGitBlobHashesVerified':True}
(base/'noise-index.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2))
print('summary',json.dumps({k:v for k,v in summary.items() if k!='chartsWithNoise'},ensure_ascii=False),flush=True)
