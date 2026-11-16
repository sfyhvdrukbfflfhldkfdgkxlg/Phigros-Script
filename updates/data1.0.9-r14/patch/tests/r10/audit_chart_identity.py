"""Audit every export against the archived raw chart and pinned Git tree."""
from pathlib import Path
from collections import Counter
import hashlib,json,math,re,sys,zipfile

raw_path=Path(sys.argv[1] if len(sys.argv)>1 else 'r10/raw/Phigros_Swordalt_OriginalCharts_and_Noise.zip')
script_path=Path(sys.argv[2] if len(sys.argv)>2 else 'count-current/Phigros_Swordalt_All_Scripts.zip')
output=Path(sys.argv[3] if len(sys.argv)>3 else 'r10/chart-identity-audit.json')
snapshot=Path('patch/data-tools/snapshot')
tree={e['path']:e for e in json.loads((snapshot/'chart-tree.json').read_text())['tree'] if e['type']=='blob'}
levels={r[0]:r[1:] for r in (line.split('\t') for line in (snapshot/'difficulty.tsv').read_text().splitlines())}
def blob(data):return hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest()
def close(a,b):return math.isclose(a,b,rel_tol=1e-12,abs_tol=1e-10)
def require(value,message):
    if not value:raise AssertionError(message)

counts=Counter();notes=noise=noise_charts=0;failures=[];rows=[]
with zipfile.ZipFile(raw_path) as raw,zipfile.ZipFile(script_path) as scripts:
    source=json.loads(raw.read('swordalt-catalog.json'));catalog=json.loads(scripts.read('swordalt-catalog.json'))
    original={e['path']:e for e in source['entries']}
    exported={e['path']:e for e in catalog['entries']}
    require(len(original)==len(exported)==len(tree)==1057,'chart inventories differ')
    require(original.keys()==exported.keys()==tree.keys(),'chart paths differ')
    require(source['chartCommit']==catalog['chartCommit'],'export revision differs')
    require(len(scripts.namelist())==len(set(scripts.namelist())),'duplicate ZIP paths')
    require({n[7:] for n in scripts.namelist() if n.startswith('charts/')}==set(tree),'extra/missing chart files')
    for index,(path,e) in enumerate(exported.items(),1):
        try:
            rank=path.rsplit('/',1)[1][:-5];r=original[path];git=tree[path]
            require(e['difficulty']==r['difficulty']==rank,'filename/catalog difficulty mismatch')
            require(e['id']==r['id']==path.split('/')[0],'song id mismatch')
            for key in ['title','level','composer','noiseCount','aliases']:
                require(e[key]==r[key],'export metadata mismatch: '+key)
            base=re.sub(r'\.\d+$','',e['id'])
            if rank in ['EZ','HD','IN','AT'] and base in levels:
                level=levels[base][['EZ','HD','IN','AT'].index(rank)] or '?'
                require(e['level']==level,'difficulty.tsv level mismatch')
            raw_bytes=raw.read('charts/'+path);script_bytes=scripts.read('charts/'+path)
            require(len(raw_bytes)==r['bytes']==git['size']==e['sourceBytes'],'source size mismatch')
            require(blob(raw_bytes)==r['sha1']==git['sha']==e['sourceSha1'],'source path/hash mismatch')
            require(len(script_bytes)==e['bytes'] and blob(script_bytes)==e['sha1'],'script checksum mismatch')
            a=json.loads(raw_bytes);b=json.loads(script_bytes);export=b['phiscriptExport']
            require(export['path']==path and export['sourceSha1']==git['sha'] and export['chartCommit']==source['chartCommit'],'script provenance mismatch')
            require(b['META']['level']==rank+' '+str(e['level']) and b['META']['name']==e['title'],'script embedded name/difficulty mismatch')
            require(close(a.get('offset',0),b['offset']),'chart offset changed')
            require(a.get('blockAreaList',[])==b.get('blockAreaList',[]),'noise data changed')
            areas=a.get('blockAreaList',[])
            require(len(areas)==e['noiseCount'],'noise count mismatch')
            if areas:
                require(json.loads(scripts.read('noise/'+path))==areas,'noise sidecar changed')
                noise_charts+=1
            noise+=len(areas)
            require(len(a['judgeLineList'])==len(b['lines']),'judge line count changed')
            count=0
            for line_index,(first,second) in enumerate(zip(a['judgeLineList'],b['lines'])):
                incoming=[(True,n) for n in first.get('notesAbove',[])]+[(False,n) for n in first.get('notesBelow',[])]
                require(len(incoming)==len(second['notes']),'line note count changed')
                tick=1.875/first['bpm']
                for (above,n),m in zip(incoming,second['notes']):
                    require(n['type']==m['type'] and above==m['above'],'note type/side changed')
                    require(close(n['time']*tick,m['time']),'note time changed')
                    tail=n['time']*tick+(n.get('holdTime',0)*tick if n['type']==3 else 0)
                    require(close(tail,m.get('endTime',m['time'])),'note end changed')
                    require(close(n['positionX']*.1125,m['object']['x'][0][0]['value']),'note x changed')
                    require(close(n.get('speed',1),m['speed']) and close(n.get('floorPosition',0),m['height']),'note speed/floor changed')
                    count+=1
            require(count==e['notes'],'script note count metadata changed')
            counts[rank]+=1;notes+=count
            rows.append({'path':path,'difficulty':rank,'notes':count,'noise':len(areas),'sourceSha1':git['sha'],'status':'verified'})
        except Exception as error:failures.append({'path':path,'error':str(error)})
        if index%100==0:print('audited',index,'/',len(exported),flush=True)
    # Every file location in the current Feijipan catalog points at the same
    # chart identity and checksums; the five known missing paths stay missing.
    cloud=json.loads(Path('patch/assets/phigros-cloud-feyajI8g-v1.json').read_text())
    for row in cloud['entries']:
        resource=row['resource'];expected=exported[resource['path']]
        for key in ['path','difficulty','title','sha1','bytes','sourceSha1']:
            require(resource[key]==expected[key],'Feijipan resource mismatch: '+resource['path']+'/'+key)
    require(len(cloud['entries'])==1052 and len(cloud['missing'])==5,'Feijipan inventory changed')
result={'charts':len(rows),'byDifficulty':dict(counts),'notesCompared':notes,'noiseCharts':noise_charts,'noiseAreasCompared':noise,'cloudLocationsVerified':1052,'failures':failures,'chartCommit':source['chartCommit'],'allNoteFieldsCompared':True,'rows':rows}
output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,ensure_ascii=False,indent=2))
print(json.dumps({k:v for k,v in result.items() if k!='rows'},ensure_ascii=False))
require(not failures and len(rows)==1057 and notes==677156 and noise==17782,'chart identity audit failed')
