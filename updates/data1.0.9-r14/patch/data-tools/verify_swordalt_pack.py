import json,zipfile,hashlib
from pathlib import Path
path=Path('deliverables/Phigros_Swordalt_OriginalCharts_and_Noise.zip');charts=noise=areas=0
with zipfile.ZipFile(path) as z:
 catalog=json.loads(z.read('swordalt-catalog.json'));entries=catalog['entries'];expected={'charts/'+e['path'] for e in entries};actual={n for n in z.namelist() if n.startswith('charts/')};assert expected==actual
 for e in entries:
  data=z.read('charts/'+e['path']);digest=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest();assert len(data)==e['bytes'] and digest==e['sha1'],e['path'];charts+=1
  if e['noiseCount']:
   source=json.loads(data);sidecar=json.loads(z.read('noise/'+e['path']));assert sidecar['blockAreaList']==source['blockAreaList'];assert len(source['blockAreaList'])==e['noiseCount'];noise+=1;areas+=e['noiseCount']
result={'charts':charts,'chartsWithNoise':noise,'noiseAreas':areas,'allGitBlobHashesVerified':True,'allNoiseSidecarsEqualOriginal':True,'archiveBytes':path.stat().st_size,'archiveSha256':hashlib.sha256(path.read_bytes()).hexdigest()};Path('analysis/r4-data-pack-results.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
